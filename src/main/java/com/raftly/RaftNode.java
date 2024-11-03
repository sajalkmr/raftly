package main.java.com.raftly;

import java.util.List;

public class RaftNode{
    private int id;
    private int currentTerm;
    private int votedFor;
    private Log log;
    //private State state;
    private List<RaftNode> cluster;


    public RaftNode(int id, List<RaftNode> cluster){
        this.id = id;
        this.currentTerm = 0;
        this.votedFor = 0;
        this.log = new Log();
        //this.state = State.FOLLOWER;
        this.cluster = cluster;

    }

    public void start(){
        // begin node
    }

    public void handleRPC(){
        //
    }
    
    public void becomeLeader(){
        //
    }

    public void becomeFollower(){
        //
    }

    public void becomeCandidate(){
        //
    }


    public static void main(String[] args){
        //

    }

    private static List<RaftNode> initCluster(){
        //
    }

    private enum State{
        LEADER,
        FOLLOWER,
        CANDIDATE

    }


}