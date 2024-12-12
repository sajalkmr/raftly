package com.raftly;

import com.raftly.persistence.PersistentState;
import com.raftly.persistence.StateMachineSnapshot;
import com.raftly.persistence.Storage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class RaftNode {
    private final String nodeId;
    private volatile int currentTerm;
    private volatile String votedFor;
    private final Log log;
    private final StateMachine stateMachine;
    private final Storage storage;
    private int commitIndex;
    private int lastApplied;
    private final Map<String, Integer> nextIndex;
    private final Map<String, Integer> matchIndex;
    private volatile State state;
    private final ScheduledExecutorService scheduler;
    private final Random random;
    private volatile String leaderId;
    private volatile boolean isRunning;
    private final Object stateLock = new Object();
    private ScheduledFuture<?> electionTimeout;
    private ScheduledFuture<?> heartbeatTask;
    private static final int SCHEDULER_POOL_SIZE = 4;
    private static final long BASE_ELECTION_TIMEOUT = 150; // milliseconds
    private static final long ELECTION_TIMEOUT_RANGE = 150; // milliseconds
    private static final long COMMIT_CHECK_INTERVAL = 50; // milliseconds
    private static final long HEARTBEAT_INTERVAL = 50; // milliseconds
    private RaftCluster cluster;

    public enum State {
        FOLLOWER,
        CANDIDATE,
        LEADER
    }

    public RaftNode(String nodeId, StateMachine stateMachine, Storage storage) {
        this.nodeId = nodeId;
        this.stateMachine = stateMachine;
        this.storage = storage;
        this.log = new LogImpl();
        this.state = State.FOLLOWER;
        this.currentTerm = 0;
        this.votedFor = null;
        this.leaderId = null;
        this.isRunning = true;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.nextIndex = new ConcurrentHashMap<>();
        this.matchIndex = new ConcurrentHashMap<>();
        this.random = new Random();
        this.cluster = null;
        
        // Load persisted state
        loadPersistedState();
        
        this.commitIndex = -1;
        this.lastApplied = -1;
        
        // Init nextIndex for all servers
        int lastLogIndex = log.getLastLogIndex();
        for (RaftNode peer : cluster != null ? cluster.getNodes() : new ArrayList<>()) {
            nextIndex.put(peer.getId(), lastLogIndex + 1);
            matchIndex.put(peer.getId(), 0);
        }
    }

    public String getId() {
        return nodeId;
    }

    public int getCurrentTerm() {
        return currentTerm;
    }

    public void setCurrentTerm(int term) {
        this.currentTerm = term;
        persistState();
    }

    public String getVotedFor() {
        return votedFor;
    }

    public void setVotedFor(String votedFor) {
        this.votedFor = votedFor;
        persistState();
    }

    public Log getLog() {
        return log;
    }

    public int getCommitIndex() {
        return commitIndex;
    }

    public void setCommitIndex(int index) {
        this.commitIndex = index;
    }

    private void persistState() {
        if (storage != null) {
            try {
                StateMachineSnapshot snapshot = new StateMachineSnapshot(stateMachine.getSnapshot());
                storage.save(new PersistentState(currentTerm, votedFor, log, snapshot));
            } catch (Exception e) {
                System.err.println("Failed to persist state: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void loadPersistedState() {
        if (storage != null) {
            try {
                PersistentState state = storage.load();
                if (state != null) {
                    this.currentTerm = state.getCurrentTerm();
                    this.votedFor = state.getVotedFor();
                    this.log = state.getLog();
                    
                    StateMachineSnapshot snapshot = state.getStateMachineSnapshot();
                    if (snapshot != null) {
                        this.stateMachine.restoreFromSnapshot(snapshot.getState());
                    }
                    
                    // Apply any entries that came after the snapshot
                    applyCommittedEntries();
                }
            } catch (Exception e) {
                System.err.println("Failed to load persistent state: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void start() {
        if (!isRunning) {
            return;
        }
        
        // Start election timeout timer
        resetElectionTimeout();

        // Start commit checker with less frequent checks
        electionTimeout = scheduler.scheduleAtFixedRate(
            () -> {
                if (isRunning) {
                    try {
                        checkCommits();
                    } catch (Exception e) {
                        System.err.println("Error in commit checker task: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            },
            COMMIT_CHECK_INTERVAL,
            COMMIT_CHECK_INTERVAL,
            TimeUnit.MILLISECONDS
        );
    }

    private void resetElectionTimeout() {
        if (!isRunning) {
            return;
        }

        synchronized (stateLock) {
            cancelTask(electionTimeout);
            
            if (!scheduler.isShutdown()) {
                long timeout = BASE_ELECTION_TIMEOUT + random.nextInt((int)ELECTION_TIMEOUT_RANGE);
                electionTimeout = scheduler.schedule(
                    this::startElection,
                    timeout,
                    TimeUnit.MILLISECONDS
                );
            }
        }
    }

    private void startElection() {
        synchronized (stateLock) {
            if (!isRunning) return;
            
            currentTerm++;
            state = State.CANDIDATE;
            votedFor = nodeId;
            persistState();  // Persist state change immediately
            
            int lastLogIndex = log.getLastLogIndex();
            int lastLogTerm = log.getLastLogTerm();
            int votesReceived = 1;
            
            System.out.println("Node " + nodeId + " starting election for term " + currentTerm);
            
            // Use CompletableFuture to handle votes asynchronously
            List<CompletableFuture<Boolean>> voteFutures = new ArrayList<>();
            
            for (RaftNode peer : cluster != null ? cluster.getNodes() : new ArrayList<>()) {
                if (peer.getId().equals(this.nodeId)) continue;
                
                CompletableFuture<Boolean> voteFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        if (!isRunning) return false;
                        return peer.requestVote(nodeId, currentTerm, lastLogIndex, lastLogTerm);
                    } catch (Exception e) {
                        System.err.println("Error requesting vote from Node " + peer.getId() + ": " + e.getMessage());
                        return false;
                    }
                }, scheduler);
                
                voteFutures.add(voteFuture);
            }
            
            // Wait for all votes or until we have a majority
            for (CompletableFuture<Boolean> future : voteFutures) {
                try {
                    if (future.get(ELECTION_TIMEOUT_RANGE, TimeUnit.MILLISECONDS)) {
                        votesReceived++;
                        if (votesReceived > (cluster != null ? cluster.getNodes().size() : 0) / 2) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    // Timeout or other error, continue with other votes
                }
            }
            
            // Check if we're still a candidate and in the same term
            if (state == State.CANDIDATE && votesReceived > (cluster != null ? cluster.getNodes().size() : 0) / 2) {
                System.out.println("Node " + nodeId + " won election with " + votesReceived + " votes");
                becomeLeader();
            } else {
                System.out.println("Node " + nodeId + " lost election with only " + votesReceived + " votes");
                becomeFollower();
            }
        }
    }

    public boolean handleVoteRequest(String candidateId, int term, int lastLogIndex, int lastLogTerm) {
        if (term < currentTerm) {
            return false;
        }

        if (term > currentTerm) {
            currentTerm = term;
            votedFor = null;
            becomeFollower();
        }

        if ((votedFor == null || votedFor.equals(candidateId)) &&
                isLogUpToDate(lastLogIndex, lastLogTerm)) {
            votedFor = candidateId;
            resetElectionTimeout();
            return true;
        }

        return false;
    }

    private boolean isLogUpToDate(int lastLogIndex, int lastLogTerm) {
        int ownLastIndex = log.getLastLogIndex();
        int ownLastTerm = log.getLastLogTerm();

        if (ownLastTerm != lastLogTerm) {
            return lastLogTerm > ownLastTerm;
        }
        return lastLogIndex >= ownLastIndex;
    }

    public void becomeLeader() {
        if (!isRunning) return;
        
        synchronized (stateLock) {
            System.out.println("Node " + nodeId + " became leader for term " + currentTerm);
            state = State.LEADER;
            leaderId = nodeId;
            
            // Initialize leader state
            int lastLogIndex = log.getLastLogIndex();
            for (RaftNode peer : cluster != null ? cluster.getNodes() : new ArrayList<>()) {
                nextIndex.put(peer.getId(), lastLogIndex + 1);
                matchIndex.put(peer.getId(), 0);
            }
            
            // Cancel election timeout and start heartbeat
            cancelTask(electionTimeout);
            startHeartbeat();
            
            persistState();
        }
    }

    public void becomeFollower() {
        if (!isRunning) return;
        
        synchronized (stateLock) {
            if (state != State.FOLLOWER) {
                System.out.println("Node " + nodeId + " became follower for term " + currentTerm);
                state = State.FOLLOWER;
                
                // Cancel heartbeat if we were leader
                cancelTask(heartbeatTask);
                
                // Reset election timeout
                resetElectionTimeout();
                
                persistState();
            }
        }
    }

    public void appendCommand(LogEntry.Command command) {
        if (!isLeader()) {
            throw new IllegalStateException("Cannot append commands on non-leader node");
        }
        
        synchronized (stateLock) {
            LogEntry entry = new LogEntry(currentTerm, log.size(), command);
            log.append(entry);
            persistState(); // Persist after log append
            
            // Replicate to followers
            for (RaftNode follower : cluster != null ? cluster.getNodes() : new ArrayList<>()) {
                if (follower.getId().equals(this.nodeId)) {
                    continue;
                }
                replicateToFollower(follower);
            }
        }
    }

    private void setCurrentTermAndVotedFor(int term, String votedFor) {
        this.currentTerm = term;
        this.votedFor = votedFor;
        persistState();
    }

    public AppendEntriesResult appendEntries(int term, String leaderId, int prevLogIndex, int prevLogTerm,
                                          List<LogEntry> entries, int leaderCommit) {
        synchronized (stateLock) {
            if (term < currentTerm) {
                return new AppendEntriesResult(currentTerm, false);
            }

            if (term > currentTerm) {
                setCurrentTermAndVotedFor(term, null);
                becomeFollower();
            }

            this.leaderId = leaderId;
            resetElectionTimeout();

            // Reply false if log doesn't contain an entry at prevLogIndex whose term matches prevLogTerm
            if (prevLogIndex >= 0) {
                if (log.size() <= prevLogIndex) {
                    return new AppendEntriesResult(currentTerm, false);
                }
                if (log.getEntry(prevLogIndex).term() != prevLogTerm) {
                    return new AppendEntriesResult(currentTerm, false);
                }
            }

            // If an existing entry conflicts with a new one (same index but different terms),
            // delete the existing entry and all that follow it
            for (int i = 0; i < entries.size(); i++) {
                int index = prevLogIndex + 1 + i;
                if (index < log.size()) {
                    if (log.getEntry(index).term() != entries.get(i).term()) {
                        // Delete conflicting entry and all that follow
                        while (log.size() > index) {
                            log.removeLast();
                        }
                        break;
                    }
                } else {
                    break;
                }
            }

            // Append any new entries not already in the log
            for (int i = 0; i < entries.size(); i++) {
                int index = prevLogIndex + 1 + i;
                if (index >= log.size()) {
                    log.append(entries.get(i));
                }
            }

            // Update commit index if leader commit is greater than local commit
            if (leaderCommit > commitIndex) {
                commitIndex = Math.min(leaderCommit, log.size() - 1);
                applyCommittedEntries();
            }

            persistState();
            return new AppendEntriesResult(currentTerm, true);
        }
    }

    private void cancelTask(ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }

    public void sendHeartbeat() {
        if (state != State.LEADER || !isRunning) {
            return;
        }

        for (RaftNode follower : cluster != null ? cluster.getNodes() : new ArrayList<>()) {
            if (follower.getId().equals(this.nodeId)) {
                continue;
            }

            int prevLogIndex = nextIndex.get(follower.getId()) - 1;
            int prevLogTerm = prevLogIndex >= 0 ? log.getEntry(prevLogIndex).term() : 0;
            List<LogEntry> entries = new ArrayList<>();
            
            // Include any missing entries
            for (int j = nextIndex.get(follower.getId()); j <= log.getLastLogIndex(); j++) {
                entries.add(log.getEntry(j));
            }

            // Only log when actually sending entries
            if (!entries.isEmpty()) {
                System.out.println("Leader " + nodeId + " sending " + entries.size() + " entries to Node " + follower.getId());
            }

            AppendEntriesResult result = follower.appendEntries(
                    currentTerm,
                    nodeId,
                    prevLogIndex,
                    prevLogTerm,
                    entries,
                    commitIndex
            );

            if (result.success()) {
                if (!entries.isEmpty()) {
                    nextIndex.put(follower.getId(), prevLogIndex + entries.size() + 1);
                    matchIndex.put(follower.getId(), prevLogIndex + entries.size());
                    System.out.println("Successfully replicated " + entries.size() + " entries to Node " + follower.getId() + 
                                     " (nextIndex=" + nextIndex.get(follower.getId()) + ", matchIndex=" + matchIndex.get(follower.getId()) + ")");
                    
                    // Try to advance commit index
                    int lastNewEntry = matchIndex.get(follower.getId());
                    int replicationCount = 1; 
                    for (RaftNode peer : cluster != null ? cluster.getNodes() : new ArrayList<>()) {
                        if (!peer.getId().equals(nodeId) && matchIndex.get(peer.getId()) >= lastNewEntry) {
                            replicationCount++;
                        }
                    }
                    
                    if (replicationCount > (cluster != null ? cluster.getNodes().size() : 0) / 2 && lastNewEntry > commitIndex) {
                        LogEntry entry = log.getEntry(lastNewEntry);
                        if (entry != null && entry.term() == currentTerm) {
                            System.out.println("Leader " + nodeId + " advancing commit index to " + lastNewEntry);
                            commitIndex = lastNewEntry;
                            applyCommittedEntries();
                        }
                    }
                }
            } else {
                if (result.term() > currentTerm) {
                    currentTerm = result.term();
                    becomeFollower();
                    break;
                } else {
                    nextIndex.put(follower.getId(), Math.max(0, nextIndex.get(follower.getId()) - 1));
                }
            }
        }
    }

    private void checkCommits() {
        if (commitIndex > lastApplied) {
            for (int i = lastApplied + 1; i <= commitIndex; i++) {
                LogEntry entry = log.getEntry(i);
                if (entry != null) {
                    stateMachine.apply(entry.getCommand());
                    lastApplied = i;
                }
            }
            persistState();
        }
    }

    public void applyCommittedEntries() {
        synchronized (stateLock) {
            while (commitIndex > lastApplied) {
                lastApplied++;
                LogEntry entry = log.getEntry(lastApplied);
                stateMachine.apply(entry.getCommand());
            }
        }
    }

    public void stop() {
        synchronized (stateLock) {
            isRunning = false;
            
            // Cancel all scheduled tasks first
            cancelTask(electionTimeout);
            cancelTask(heartbeatTask);
            
            electionTimeout = null;
            heartbeatTask = null;
            
            // Shutdown the scheduler gracefully
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
        }
    }

    public boolean isLeader() {
        return state == State.LEADER;
    }

    public StateMachine getStateMachine() {
        return stateMachine;
    }

    public void updateClusterConfiguration(RaftCluster newCluster) {
        synchronized (stateLock) {
            // Instead of reassigning final variables, we'll clear and add new elements
            cluster = newCluster;
            
            // Update nextIndex and matchIndex maps
            Map<String, Integer> newNextIndex = new HashMap<>();
            Map<String, Integer> newMatchIndex = new HashMap<>();
            
            // Copy existing values for nodes that are still in the cluster
            for (RaftNode node : newCluster.getNodes()) {
                String oldId = findNodeId(node.getId());
                if (oldId != null) {
                    newNextIndex.put(node.getId(), nextIndex.get(oldId));
                    newMatchIndex.put(node.getId(), matchIndex.get(oldId));
                } else {
                    newNextIndex.put(node.getId(), log.getLastLogIndex() + 1);
                    newMatchIndex.put(node.getId(), 0);
                }
            }
            
            nextIndex.clear();
            nextIndex.putAll(newNextIndex);
            matchIndex.clear();
            matchIndex.putAll(newMatchIndex);
            
            persistState();
        }
    }
    
    private String findNodeId(String nodeId) {
        for (String id : nextIndex.keySet()) {
            if (id.equals(nodeId)) {
                return id;
            }
        }
        return null;
    }

    public void startHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        heartbeatTask = scheduler.scheduleAtFixedRate(
            this::sendHeartbeat,
            0,
            HEARTBEAT_INTERVAL,
            TimeUnit.MILLISECONDS
        );
    }

    private void replicateToFollower(RaftNode follower) {
        int nextIdx = nextIndex.get(follower.getId());
        int prevLogIndex = nextIdx - 1;
        int prevLogTerm = prevLogIndex < 0 ? 0 : log.getEntry(prevLogIndex).term();
        
        List<LogEntry> entries = new ArrayList<>();
        for (int i = nextIdx; i < log.size(); i++) {
            entries.add(log.getEntry(i));
        }
        
        AppendEntriesResult result = follower.appendEntries(
            currentTerm,
            nodeId,
            prevLogIndex,
            prevLogTerm,
            entries,
            commitIndex
        );
        
        if (result.success()) {
            if (!entries.isEmpty()) {
                nextIndex.put(follower.getId(), nextIdx + entries.size());
                matchIndex.put(follower.getId(), nextIdx + entries.size() - 1);
            }
        } else {
            if (result.term() > currentTerm) {
                setCurrentTermAndVotedFor(result.term(), null);
                becomeFollower();
            } else {
                nextIndex.put(follower.getId(), nextIndex.get(follower.getId()) - 1);
            }
        }
    }

    public VoteResult requestVote(int candidateTerm, String candidateId, int lastLogIndex, int lastLogTerm) {
        synchronized (stateLock) {
            if (candidateTerm < currentTerm) {
                return new VoteResult(currentTerm, false);
            }

            if (candidateTerm > currentTerm) {
                currentTerm = candidateTerm;
                votedFor = null;
                becomeFollower();
            }

            if ((votedFor == null || votedFor.equals(candidateId)) && isLogUpToDate(lastLogIndex, lastLogTerm)) {
                votedFor = candidateId;
                persistState();
                resetElectionTimeout();
                return new VoteResult(currentTerm, true);
            }

            return new VoteResult(currentTerm, false);
        }
    }

    public class VoteResult {
        private final int term;
        private final boolean voteGranted;

        public VoteResult(int term, boolean voteGranted) {
            this.term = term;
            this.voteGranted = voteGranted;
        }

        public int term() {
            return term;
        }

        public boolean voteGranted() {
            return voteGranted;
        }
    }
}

class AppendEntriesResult {
    private final int term;
    private final boolean success;

    public AppendEntriesResult(int term, boolean success) {
        this.term = term;
        this.success = success;
    }

    public int term() {
        return term;
    }

    public boolean success() {
        return success;
    }
}
