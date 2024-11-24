package com.raftly;

import java.util.Arrays;
import java.util.List;

public class RaftDemo {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Starting Raft cluster with 3 nodes...");
        
        // Init state machines
        StateMachine stateMachine0 = new StateMachine();
        StateMachine stateMachine1 = new StateMachine();
        StateMachine stateMachine2 = new StateMachine();
        
        // Init logs
        Log log0 = new Log();
        Log log1 = new Log();
        Log log2 = new Log();
        
        // Init nodes
        RaftNode node0 = new RaftNode(0, null, stateMachine0, log0);
        RaftNode node1 = new RaftNode(1, null, stateMachine1, log1);
        RaftNode node2 = new RaftNode(2, null, stateMachine2, log2);
        
        // Create cluster and set it for each node
        List<RaftNode> nodes = Arrays.asList(node0, node1, node2);
        RaftCluster cluster = new RaftCluster(nodes);
        
        // Set cluster for each node
        node0.setCluster(cluster);
        node1.setCluster(cluster);
        node2.setCluster(cluster);
        
        // Start nodes
        node0.start();
        node1.start();
        node2.start();
        
        System.out.println("\nWaiting for leader election...");
        Thread.sleep(2000);
        
        // Find leader
        RaftNode leader = null;
        for (RaftNode node : nodes) {
            if (node.isLeader()) {
                leader = node;
                System.out.println("Leader elected: Node " + node.getId() + "\n");
                break;
            }
        }
        
        if (leader == null) {
            System.out.println("No leader elected! Exiting...");
            shutdownCluster(nodes);
            return;
        }

        // Test operations
        System.out.println("Testing key-value operations:");
        
        System.out.println("Setting key1=value1");
        leader.appendCommand(new LogEntry.Command("SET", "key1", "value1"));
        
        // Wait for replication (1 second)
        Thread.sleep(1000);
        
        // Read from all nodes
        for (RaftNode node : nodes) {
            String value = node.getStateMachine().get("key1");
            System.out.println("Node " + node.getId() + " reads key1=" + value);
            System.out.println("Node " + node.getId() + " commit index: " + node.getCommitIndex());
            System.out.println("Node " + node.getId() + " log size: " + node.getLog().getLastLogIndex() + 1);
        }

        // Shutdown the cluster
        System.out.println("\nShutting down cluster...");
        shutdownCluster(nodes);
    }
    
    private static void shutdownCluster(List<RaftNode> nodes) {
        for (RaftNode node : nodes) {
            node.stop();
        }
    }
}
