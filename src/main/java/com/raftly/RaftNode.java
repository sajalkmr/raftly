package main.java.com.raftly;

import java.util.List;
import java.util.ArrayList;

public class RaftNode {
    private int id;
    private int currentTerm;
    private int votedFor;
    private Log log;
    private State state;
    private List<RaftNode> cluster;
    private StateMachine stateMachine;



    public RaftNode(int id, List<RaftNode> cluster) {
        this.id = id;
        this.currentTerm = 0;
        this.votedFor = -1;
        this.log = new Log();
        this.state = State.FOLLOWER;
        this.cluster = cluster;
        this.stateMachine = new StateMachine();
    }

    public void setVotedFor(int candidateId) {
        this.votedFor = candidateId;
    }

    public int getId() {
        return id;
    }

    public void start() {
        ElectionMgr electionMgr = new ElectionMgr(this, cluster);
        electionMgr.startElection();
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

    public void receiveLogEntries(List<LogEntry> entries) {
        for (LogEntry entry : entries) {
            log.append(entry);
        }
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

    public void handleVoteRequest(int candidateId, int term) {
        if (term > currentTerm) {
            becomeFollower();
            setVotedFor(candidateId);
            // Send vote response
        }
    }

    public void handleLogEntries(List<LogEntry> entries) {
        for (LogEntry entry : entries) {
            log.append(entry);
            stateMachine.applyCommand(entry.getCommand(), "someValue");
        }
        // Send acknowledgment back to the leader
        //sendAckToLeader();
    }

    private enum State {
        LEADER,
        FOLLOWER,
        CANDIDATE
    }
}
