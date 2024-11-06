package main.java.com.raftly;

import java.util.List;

public class ElectionMgr {
    private List<RaftNode> cluster;
    private int currentTerm;
    private int votesReceived;
    private int votesNeeded;
    private RaftNode node;

    public ElectionMgr(RaftNode node, List<RaftNode> cluster) {
        this.cluster = cluster;
        this.node = node;
        this.currentTerm = 0;
        this.votesReceived = 0;
        this.votesNeeded = (cluster.size() / 2)+1;
    }
   

    public void startElection() {
        currentTerm++;
        votesReceived = 1;
        node.setVotedFor(node.getId());

        for (RaftNode peer : cluster) {
            if (peer.getId() != node.getId()) {
                peer.requestVote(node.getId(), currentTerm);
            }
        }

        node.sendLogEntries();
    }

    public void receiveVote(int voterId) {
        votesReceived++;
        if (votesReceived >= cluster.size()) {
            node.becomeLeader();
        }
        
    }


}


    

    

