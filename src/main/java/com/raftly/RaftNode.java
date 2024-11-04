package main.java.com.raftly;

import java.util.List;
import java.util.ArrayList;

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
        this.votedFor = -1;
        this.log = new Log();
        this.state = State.FOLLOWER;
        this.cluster = cluster;

    }

    public void start(){
        // begin node
    }

    public void handleRPC(){
        if(state == State.LEADER){
            // handle leader RPC
        }
        else if(state == State.FOLLOWER){
            // handle follower RPC
        }
        else if(state == State.CANDIDATE){
            // handle candidate RPC
        }
    }
    
    public void becomeLeader(){
        this.state = State.LEADER;

        
    }

    public void becomeFollower(){
        this.state = State.FOLLOWER;
    }

    public void becomeCandidate(){
        this.state = State.CANDIDATE;
    }


    public static void main(String[] args){
        //

    }

    private static List<RaftNode> initCluster(){
        private List<RaftNode> cluster = new ArrayList<RaftNode>();
        for(int i = 0; i < 6; i++){
            cluster.add(new RaftNode(i, cluster));
        }
        return cluster;


        
    }

    private enum State{
        LEADER,
        FOLLOWER,
        CANDIDATE

    }


}