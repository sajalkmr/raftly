package com.raftly;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import com.raftly.LogEntry;
import com.raftly.RaftCluster;
import com.raftly.StateMachine;

public class RaftNode {
    private int id;
    private volatile int currentTerm;
    private volatile int votedFor;
    private Log log;
    private volatile State state;
    private List<RaftNode> cluster;
    private StateMachine stateMachine;

    private volatile int commitIndex;
    private volatile int lastApplied;
    private volatile int[] nextIndex;
    private volatile int[] matchIndex;
    private final ScheduledExecutorService scheduler;
    private final Random random;
    private volatile int leaderId;
    private final AtomicBoolean isRunning;
    private final ReentrantLock lock;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> electionTimeoutTask;

    private static final int BASE_ELECTION_TIMEOUT = 1500;
    private static final int ELECTION_TIMEOUT_RANGE = 750;
    private static final int HEARTBEAT_INTERVAL = 1000;
    private static final int COMMIT_CHECK_INTERVAL = 500;

    public RaftNode(int id, RaftCluster cluster, StateMachine stateMachine, Log log) {
        this.id = id;
        this.currentTerm = 0;
        this.votedFor = -1;
        this.log = log;
        this.state = State.FOLLOWER;
        this.cluster = cluster != null ? cluster.getNodes() : new ArrayList<>();
        this.stateMachine = stateMachine;

        this.commitIndex = -1;
        this.lastApplied = -1;
        this.nextIndex = new int[cluster != null ? cluster.getNodes().size() : 0];
        this.matchIndex = new int[cluster != null ? cluster.getNodes().size() : 0];
        
        // Init nextIndex for all servers
        int lastLogIndex = log.getLastLogIndex();
        for (int i = 0; i < (cluster != null ? cluster.getNodes().size() : 0); i++) {
            nextIndex[i] = lastLogIndex + 1;
            matchIndex[i] = 0;
        }
        
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.random = new Random();
        this.leaderId = -1;
        this.isRunning = new AtomicBoolean(true);
        this.lock = new ReentrantLock();
    }

    public void start() {
        if (!isRunning.get()) {
            return;
        }
        
        // Start election timeout timer
        resetElectionTimeout();

        // Start commit checker with less frequent checks
        scheduler.scheduleAtFixedRate(() -> {
            if (isRunning.get()) {
                checkCommits();
            }
        }, 0, COMMIT_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void resetElectionTimeout() {
        // Cancel any existing election timeout
        if (electionTimeoutTask != null) {
            electionTimeoutTask.cancel(false);
        }

        // Only reset election timeout if not leader
        if (state != State.LEADER) {
            // Schedule new election timeout with randomized delay
            int timeout = BASE_ELECTION_TIMEOUT + random.nextInt(ELECTION_TIMEOUT_RANGE);
            electionTimeoutTask = scheduler.schedule(
                    this::startElection,
                    timeout,
                    TimeUnit.MILLISECONDS);
        }
    }

    private void startElection() {
        lock.lock();
        try {
            if (!isRunning.get()) return;
            
            currentTerm++;
            state = State.CANDIDATE;
            votedFor = id;
            int votesReceived = 1;
            
            System.out.println("Node " + id + " starting election for term " + currentTerm);
            
            // Request votes from peers
            for (RaftNode peer : cluster) {
                if (peer.getId() == this.id) continue;
                
                try {
                    if (peer.requestVote(id, currentTerm)) {
                        votesReceived++;
                        System.out.println("Node " + id + " received vote from Node " + peer.getId());
                    }
                } catch (Exception e) {
                    System.err.println("Error requesting vote from Node " + peer.getId() + ": " + e.getMessage());
                }
            }
            
            // If we got majority of votes, become leader
            if (votesReceived > cluster.size() / 2) {
                System.out.println("Node " + id + " won election with " + votesReceived + " votes");
                becomeLeader();
            } else {
                System.out.println("Node " + id + " lost election with only " + votesReceived + " votes");
                becomeFollower();
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean handleVoteRequest(int candidateId, int term, int lastLogIndex, int lastLogTerm) {
        if (term < currentTerm) {
            return false;
        }

        if (term > currentTerm) {
            currentTerm = term;
            votedFor = -1;
            becomeFollower();
        }

        if ((votedFor == -1 || votedFor == candidateId) &&
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
        lock.lock();
        try {
            if (state == State.LEADER) {
                return;  
            }
            
            state = State.LEADER;
            System.out.println("Node " + id + " became leader for term " + currentTerm);
            
            // Init leader state
            nextIndex = new int[cluster.size()];
            matchIndex = new int[cluster.size()];
            
            // Set nextIndex for each server
            int lastLogIndex = log.getLastLogIndex();
            for (int i = 0; i < cluster.size(); i++) {
                nextIndex[i] = lastLogIndex + 1;
                matchIndex[i] = 0;
            }
            
            matchIndex[id] = lastLogIndex;
            
            // Cancel election timeout
            if (electionTimeoutTask != null) {
                electionTimeoutTask.cancel(false);
                electionTimeoutTask = null;
            }
            
            // Start sending heartbeats
            heartbeatTask = scheduler.scheduleAtFixedRate(
                () -> sendHeartbeat(),
                0,
                HEARTBEAT_INTERVAL,
                TimeUnit.MILLISECONDS
            );
            
            // Start commit checker
            scheduler.scheduleAtFixedRate(
                () -> checkCommits(),
                COMMIT_CHECK_INTERVAL,
                COMMIT_CHECK_INTERVAL,
                TimeUnit.MILLISECONDS
            );
            
            sendHeartbeat();
        } finally {
            lock.unlock();
        }
    }

    public void becomeFollower() {
        lock.lock();
        try {
            if (state == State.FOLLOWER) {
                return;  
            }
            
            state = State.FOLLOWER;
            System.out.println("Node " + id + " became follower for term " + currentTerm);
            
            // Cancel heartbeat task if it exists
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
                heartbeatTask = null;
            }
            
            resetElectionTimeout();
        } finally {
            lock.unlock();
        }
    }

    public void becomeCandidate() {
        lock.lock();
        try {
            if (state == State.CANDIDATE) {
                return;  
            }
            
            state = State.CANDIDATE;
            System.out.println("Node " + id + " became candidate for term " + (currentTerm + 1));
            
            // Cancel heartbeat task if it exists
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
                heartbeatTask = null;
            }
        } finally {
            lock.unlock();
        }
    }

    public void sendHeartbeat() {
        if (state != State.LEADER || !isRunning.get()) {
            return;
        }

        for (int i = 0; i < cluster.size(); i++) {
            RaftNode follower = cluster.get(i);
            if (follower.getId() == this.id) {
                continue;
            }

            int prevLogIndex = nextIndex[i] - 1;
            int prevLogTerm = prevLogIndex >= 0 ? log.getEntry(prevLogIndex).term() : 0;
            List<LogEntry> entries = new ArrayList<>();
            
            // Include any missing entries
            for (int j = nextIndex[i]; j <= log.getLastLogIndex(); j++) {
                entries.add(log.getEntry(j));
            }

            // Only log when actually sending entries
            if (!entries.isEmpty()) {
                System.out.println("Leader " + id + " sending " + entries.size() + " entries to Node " + follower.getId());
            }

            AppendEntriesResult result = follower.appendEntries(
                    currentTerm,
                    id,
                    prevLogIndex,
                    prevLogTerm,
                    entries,
                    commitIndex
            );

            if (result.success()) {
                if (!entries.isEmpty()) {
                    nextIndex[i] = prevLogIndex + entries.size() + 1;
                    matchIndex[i] = prevLogIndex + entries.size();
                    System.out.println("Successfully replicated " + entries.size() + " entries to Node " + follower.getId() + 
                                     " (nextIndex=" + nextIndex[i] + ", matchIndex=" + matchIndex[i] + ")");
                    
                    // Try to advance commit index
                    int lastNewEntry = matchIndex[i];
                    int replicationCount = 1; 
                    for (int j = 0; j < cluster.size(); j++) {
                        if (j != id && matchIndex[j] >= lastNewEntry) {
                            replicationCount++;
                        }
                    }
                    
                    if (replicationCount > cluster.size() / 2 && lastNewEntry > commitIndex) {
                        LogEntry entry = log.getEntry(lastNewEntry);
                        if (entry != null && entry.term() == currentTerm) {
                            System.out.println("Leader " + id + " advancing commit index to " + lastNewEntry);
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
                    nextIndex[i] = Math.max(0, nextIndex[i] - 1);
                }
            }
        }
    }

    public AppendEntriesResult appendEntries(int term, int leaderId, int prevLogIndex, int prevLogTerm,
                                      List<LogEntry> entries, int leaderCommit) {
        if (term < currentTerm) {
            return new AppendEntriesResult(currentTerm, false);
        }

        if (term > currentTerm) {
            currentTerm = term;
            votedFor = -1;
            becomeFollower();
        }

        resetElectionTimeout();
        this.leaderId = leaderId;

        // Reply false if log doesn't contain an entry at prevLogIndex whose term matches prevLogTerm
        if (prevLogIndex >= 0) {
            LogEntry prevEntry = log.getEntry(prevLogIndex);
            if (prevEntry == null || prevEntry.term() != prevLogTerm) {
                System.out.println("Node " + id + " rejecting append: term mismatch at prevLogIndex " + prevLogIndex);
                return new AppendEntriesResult(currentTerm, false);
            }
        }

        // If an existing entry conflicts with a new one (same index but different terms),
        // delete the existing entry and all that follow it
        for (int i = 0; i < entries.size(); i++) {
            int index = prevLogIndex + 1 + i;
            LogEntry existing = log.getEntry(index);
            if (existing != null && existing.term() != entries.get(i).term()) {
                System.out.println("Node " + id + " truncating log from index " + index);
                log.truncateFrom(index);
                break;
            }
        }

        // Append any new entries not already in the log
        if (!entries.isEmpty()) {
            System.out.println("Node " + id + " appending " + entries.size() + " entries starting at index " + (prevLogIndex + 1));
            log.appendEntries(entries, prevLogIndex);
        }

        // Update commit index if leader commit is greater than local commit index
        if (leaderCommit > commitIndex) {
            commitIndex = Math.min(leaderCommit, log.getLastLogIndex());
            System.out.println("Node " + id + " updating commitIndex to " + commitIndex);
            applyCommittedEntries();
        }

        return new AppendEntriesResult(currentTerm, true);
    }

    private void applyCommittedEntries() {
        lock.lock();
        try {
            if (lastApplied < commitIndex) {
                System.out.println("Node " + id + " applying entries from index " + (lastApplied + 1) + " to " + commitIndex);
                for (int i = lastApplied + 1; i <= commitIndex; i++) {
                    LogEntry entry = log.getEntry(i);
                    if (entry != null) {
                        System.out.println("Node " + id + " applying command: " + entry.command());
                        stateMachine.apply(entry.command());
                        lastApplied = i;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void appendCommand(LogEntry.Command command) throws IllegalStateException {
        lock.lock();
        try {
            if (state != State.LEADER) {
                throw new IllegalStateException("Commands can only be appended through the leader. Current leader is node " + leaderId);
            }
            
            System.out.println("Leader " + id + " appending command: " + command.operation());
            int nextIndex = log.getLastLogIndex() + 1;
            LogEntry entry = new LogEntry(currentTerm, nextIndex, command);
            log.append(entry);
            
            // Force immediate replication
            sendHeartbeat();
            
            // Update commit index
            updateCommitIndex();
        } catch (Exception e) {
            System.err.println("Error appending command: " + e.getMessage());
            throw new IllegalStateException("Failed to append command", e);
        } finally {
            lock.unlock();
        }
    }

    private void updateCommitIndex() {
        if (state != State.LEADER) {
            return;
        }

        int lastIndex = log.getLastLogIndex();
        for (int n = commitIndex + 1; n <= lastIndex; n++) {
            LogEntry entry = log.getEntry(n);
            if (entry == null || entry.term() != currentTerm) {
                continue;
            }

            int replicationCount = 1; 
            for (int i = 0; i < cluster.size(); i++) {
                if (i != id && matchIndex[i] >= n) {
                    replicationCount++;
                }
            }

            if (replicationCount > cluster.size() / 2) {
                System.out.println("Leader " + id + " advancing commit index from " + commitIndex + " to " + n);
                commitIndex = n;
                applyCommittedEntries();
            }
        }
    }

    public void checkCommits() {
        updateCommitIndex();
    }

    public int getId() {
        return id;
    }

    public int getCurrentTerm() {
        return currentTerm;
    }

    public void setCurrentTerm(int term) {
        this.currentTerm = term;
    }

    public int getVotedFor() {
        return votedFor;
    }

    public void setVotedFor(int nodeId) {
        this.votedFor = nodeId;
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

    public void receiveLogEntries(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        log.appendEntries(entries, log.getLastLogIndex());
    }

    public boolean requestVote(int candidateId, int term) {
        lock.lock();
        try {
            if (term < currentTerm) {
                return false;
            }
            
            if (term > currentTerm) {
                currentTerm = term;
                votedFor = -1;
                becomeFollower();
            }
            
            // If we haven't voted for anyone in this term or we've already voted for this candidate
            if (votedFor == -1 || votedFor == candidateId) {
                votedFor = candidateId;
                resetElectionTimeout();
                System.out.println("Node " + id + " voted for Node " + candidateId + " in term " + term);
                return true;
            }
            
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        isRunning.set(false);
        
        // Cancel any existing tasks
        if (electionTimeoutTask != null) {
            electionTimeoutTask.cancel(true);
            electionTimeoutTask = null;
        }
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
            heartbeatTask = null;
        }
        
        // Shutdown scheduler
        scheduler.shutdownNow();
        try {
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isLeader() {
        return state == State.LEADER;
    }

    public StateMachine getStateMachine() {
        return stateMachine;
    }

    public void setCluster(RaftCluster cluster) {
        if (cluster == null) {
            throw new IllegalArgumentException("Cluster cannot be null");
        }
        
        lock.lock();
        try {
            this.cluster = new ArrayList<>(cluster.getNodes());
            synchronized (this) {
                this.nextIndex = new int[this.cluster.size()];
                this.matchIndex = new int[this.cluster.size()];
                for (int i = 0; i < this.cluster.size(); i++) {
                    nextIndex[i] = log.getLastLogIndex() + 1;
                    matchIndex[i] = 0;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public enum State {
        FOLLOWER,
        CANDIDATE,
        LEADER
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
