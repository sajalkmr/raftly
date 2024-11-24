package com.raftly;

import java.util.List;
import java.util.TimerTask;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class ElectionMgr {
    private static final long ELECTION_TIMEOUT = 5000;
    private final RaftNode node;
    private final List<RaftNode> cluster;
    private final AtomicInteger votesReceived;
    private final ReentrantLock electionLock;
    private int currentTerm;
    private Timer electionTimer;
    private final int votesNeeded;

    public ElectionMgr(RaftNode node, List<RaftNode> cluster) {
        this.node = node;
        this.cluster = cluster;
        this.votesReceived = new AtomicInteger(0);
        this.electionLock = new ReentrantLock();
        this.votesNeeded = (cluster.size() / 2) + 1;
    }

    public void startElection() {
        if (!electionLock.tryLock()) {
            return; 
        }
        
        try {
            currentTerm++;
            votesReceived.set(1); 
            node.setVotedFor(node.getId());
            node.setCurrentTerm(currentTerm);

            // Request votes
            for (RaftNode peer : cluster) {
                if (peer.getId() != node.getId()) {
                    peer.requestVote(node.getId(), currentTerm);
                }
            }

            // Schedule election timeout
            if (electionTimer != null) {
                electionTimer.cancel();
            }
            electionTimer = new Timer();
            electionTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (votesReceived.get() < votesNeeded) {
                        node.becomeFollower();
                    }
                }
            }, ELECTION_TIMEOUT + (long)(Math.random() * 1000));
        } finally {
            electionLock.unlock();
        }
    }

    public void receiveVote(int voterId) {
        int votes = votesReceived.incrementAndGet();
        if (votes >= votesNeeded) {
            node.becomeLeader();
            if (electionTimer != null) {
                electionTimer.cancel();
            }
        }
    }

    public void receiveAck(int followerId) {
        System.out.println("Received acknowledgment from follower " + followerId);
    }
}
