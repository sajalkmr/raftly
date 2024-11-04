package main.java.com.raftly;

import java.util.List;

public class ElectionMgr {
    private List<RaftNode> cluster;

    public ElectionMgr(List<RaftNode> cluster) {
        this.cluster = cluster;
    }

    public void startElection() {
        // start an election
        // Increment current term, vote for self, send RequestVote RPCs
    }

    public void receiveVote(int voterId) {
        //  handle votes
        // Count votes and determine if a leader can be elected
    }
}


    

    

