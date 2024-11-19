package com.raftly;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static final int BASE_ELECTION_TIMEOUT = 300;
    private static final int ELECTION_TIMEOUT_RANGE = 150;
    private static final int HEARTBEAT_INTERVAL = 150;

    public RaftNode(int id, List<RaftNode> cluster) {
        this.id = id;
        this.currentTerm = 0;
        this.votedFor = -1;
        this.log = new Log();
        this.state = State.FOLLOWER;
        this.cluster = cluster;
        this.stateMachine = new StateMachine();

        this.commitIndex = 0;
        this.lastApplied = 0;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.random = new Random();
        this.leaderId = -1;
        this.isRunning = new AtomicBoolean(true);

        this.nextIndex = new int[cluster.size()];
        this.matchIndex = new int[cluster.size()];
    }

    public void start() {
        if (!isRunning.get()) {
            return;
        }
        
        // Initialize indices
        for (int i = 0; i < cluster.size(); i++) {
            nextIndex[i] = log.getLastIndex() + 1;
            matchIndex[i] = 0;
        }

        // Start election timeout timer
        resetElectionTimeout();

        // Start commit checker
        scheduler.scheduleAtFixedRate(this::checkCommits, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void resetElectionTimeout() {
        scheduler.schedule(this::startElection,
                BASE_ELECTION_TIMEOUT + random.nextInt(ELECTION_TIMEOUT_RANGE),
                TimeUnit.MILLISECONDS);
    }

    private void startElection() {
        if (!isRunning.get() || state == State.LEADER) {
            return;
        }

        becomeCandidate();
        currentTerm++;
        votedFor = id;
        int votesReceived = 1; // Vote for self

        // Request votes from all other nodes
        for (RaftNode peer : cluster) {
            if (peer.getId() != this.id) {
                boolean voteGranted = peer.handleVoteRequest(id, currentTerm,
                        log.getLastIndex(), log.getLastTerm());
                if (voteGranted) {
                    votesReceived++;
                }
            }
        }

        // Check if we won the election
        if (votesReceived > cluster.size() / 2) {
            becomeLeader();
        } else {
            becomeFollower();
            resetElectionTimeout();
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
        int ownLastIndex = log.getLastIndex();
        int ownLastTerm = log.getLastTerm();

        if (ownLastTerm != lastLogTerm) {
            return lastLogTerm > ownLastTerm;
        }
        return lastLogIndex >= ownLastIndex;
    }

    @Override
    public void becomeLeader() {
        if (state != State.LEADER) {
            state = State.LEADER;
            leaderId = id;

            // Initialize leader-specific data structures
            for (int i = 0; i < cluster.size(); i++) {
                nextIndex[i] = log.getLastIndex() + 1;
                matchIndex[i] = 0;
            }

            // Start sending heartbeats
            scheduler.scheduleAtFixedRate(
                    this::sendHeartbeat,
                    0,
                    HEARTBEAT_INTERVAL,
                    TimeUnit.MILLISECONDS);
        }
    }

    private void sendHeartbeat() {
        if (state != State.LEADER) {
            return;
        }

        for (RaftNode peer : cluster) {
            if (peer.getId() != this.id) {
                int prevLogIndex = nextIndex[peer.getId()] - 1;
                int prevLogTerm = 0;
                
                try {
                    if (prevLogIndex >= 0) {
                        prevLogTerm = log.getTermAt(prevLogIndex);
                    }
                    List<LogEntry> entries = log.getEntriesFrom(nextIndex[peer.getId()]);

                    AppendEntriesResult result = peer.appendEntries(
                            currentTerm,
                            id,
                            prevLogIndex,
                            prevLogTerm,
                            entries,
                            commitIndex);

                    if (result.isSuccess()) {
                        if (!entries.isEmpty()) {
                            nextIndex[peer.getId()] += entries.size();
                            matchIndex[peer.getId()] = nextIndex[peer.getId()] - 1;
                            updateCommitIndex();
                        }
                    } else {
                        // If AppendEntries fails due to log inconsistency
                        if (nextIndex[peer.getId()] > 0) {
                            nextIndex[peer.getId()]--;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error sending heartbeat to peer " + peer.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    public AppendEntriesResult appendEntries(int term, int leaderId, int prevLogIndex,
            int prevLogTerm, List<LogEntry> entries, int leaderCommit) {
        if (term < currentTerm) {
            return new AppendEntriesResult(currentTerm, false);
        }

        if (term > currentTerm) {
            currentTerm = term;
            votedFor = -1;
            becomeFollower();
        }

        resetElectionTimeout();

        // Check log consistency
        if (prevLogIndex > log.getLastIndex() ||
                (prevLogIndex >= 0 && log.getTermAt(prevLogIndex) != prevLogTerm)) {
            return new AppendEntriesResult(currentTerm, false);
        }

        // Append new entries
        for (int i = 0; i < entries.size(); i++) {
            int logIndex = prevLogIndex + 1 + i;
            if (logIndex <= log.getLastIndex()) {
                if (log.getTermAt(logIndex) != entries.get(i).getTerm()) {
                    // Delete conflicting entries and all that follow
                    log.truncateFrom(logIndex);
                    log.append(entries.get(i));
                }
            } else {
                log.append(entries.get(i));
            }
        }

        // Update commit index
        if (leaderCommit > commitIndex) {
            commitIndex = Math.min(leaderCommit, log.getLastIndex());
        }

        return new AppendEntriesResult(currentTerm, true);
    }

    private void updateCommitIndex() {
        for (int n = commitIndex + 1; n <= log.getLastIndex(); n++) {
            if (log.getTermAt(n) == currentTerm) {
                int replicationCount = 1; // Count self
                for (int i = 0; i < matchIndex.length; i++) {
                    if (i != id && matchIndex[i] >= n) {
                        replicationCount++;
                    }
                }
                if (replicationCount > cluster.size() / 2) {
                    commitIndex = n;
                }
            }
        }
    }

    private void checkCommits() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry entry = log.getEntry(lastApplied);
            if (entry != null) {
                stateMachine.applyCommand(entry.getCommand(), entry.getValue());
            }
        }
    }

    public void handleClientRequest(String command, String value) {
        if (state != State.LEADER) {
            throw new IllegalStateException("Not the leader. Please redirect to node " + leaderId);
        }

        LogEntry entry = new LogEntry(currentTerm, command, value);
        log.append(entry);
        sendHeartbeat(); // Triggers immediate replication
    }

    public void stop() {
        isRunning.set(false);
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class AppendEntriesResult {
        private final int term;
        private final boolean success;

        public AppendEntriesResult(int term, boolean success) {
            this.term = term;
            this.success = success;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getTerm() {
            return term;
        }
    }

    public int getCurrentTerm() {
        return currentTerm;
    }

    public void setCurrentTerm(int term) {
        this.currentTerm = term;
    }

    public int getCommitIndex() {
        return commitIndex;
    }

    public void setCommitIndex(int index) {
        this.commitIndex = index;
    }

    public Log getLog() {
        return log;
    }

    public void setVotedFor(int candidateId) {
        this.votedFor = candidateId;
    }

    public int getId() {
        return id;
    }

    public void handleRPC() {
        if (state == State.LEADER) {
            // handle leader RPC
        } else if (state == State.FOLLOWER) {
            // handle follower RPC
        } else if (state == State.CANDIDATE) {
            // handle candidate RPC
        }
    }

    public void becomeLeader() {
        this.state = State.LEADER;
        sendLogEntries();
    }

    public void becomeFollower() {
        this.state = State.FOLLOWER;
    }

    public void becomeCandidate() {
        this.state = State.CANDIDATE;
        this.currentTerm++;
        this.votedFor = this.id;
    }

    public static void main(String[] args) {
        List<RaftNode> cluster = initCluster();
        // Start the cluster nodes
        for (RaftNode node : cluster) {
            node.start();
        }
    }

    private static List<RaftNode> initCluster() {
        List<RaftNode> cluster = new ArrayList<RaftNode>();
        for (int i = 0; i < 6; i++) {
            cluster.add(new RaftNode(i, cluster));
        }
        return cluster;

    }

    public void requestVote(int candidateId, int term) {
        if (term <= currentTerm) {
            System.out.println("Vote request denied: term is not greater than current term.");
            return;
        }
        if (votedFor != -1 && votedFor != candidateId) {
            System.out.println("Vote request denied: already voted for another candidate.");
            return;
        }
        currentTerm = term;
        votedFor = candidateId;
        System.out.println("Vote granted to candidate " + candidateId + " for term " + term);
        // send vote
    }

    public void sendLogEntries() {
        for (RaftNode peer : cluster) {
            if (peer.getId() != this.id) {
                peer.receiveLogEntries(this.log.getEntries());
            }
        }
    }

    private void sendAckToLeader() {
        // send acknowledgment to the leader

    }

    public void receiveLogEntries(List<LogEntry> entries) {
        for (LogEntry entry : entries) {
            log.append(entry);
            stateMachine.applyCommand(entry.getCommand(), "someValue");
        }
        sendAckToLeader();
    }

    public void appendEntries(int leaderId, int term, List<LogEntry> entries) {
        if (term < currentTerm) {
            // Reply false if term < currentTerm
            return;
        }
        currentTerm = term; // Update current term
        // Append the entries to the log
        for (LogEntry entry : entries) {
            log.append(entry);
        }

    }

    public void handleLogEntries(List<LogEntry> entries) {
        for (LogEntry entry : entries) {
            log.append(entry);
            stateMachine.applyCommand(entry.getCommand(), "someValue");
        }
        // sendAckToLeader();
    }

    public void saveState() {
        System.out.println("State saved: Term=" + currentTerm + ", VotedFor=" + votedFor);
    }

    private void logState() {
        System.out.println("Node " + id + " is in state: " + state);
    }

    private enum State {
        LEADER,
        FOLLOWER,
        CANDIDATE
    }
}
