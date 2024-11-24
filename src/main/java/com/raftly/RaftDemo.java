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
        
        System.out.println("\nTest 1: Basic Set/Get");
        System.out.println("Setting key1=value1");
        leader.appendCommand(new LogEntry.Command("SET", "key1", "value1"));
        Thread.sleep(500);
        
        System.out.println("\nTest 2: Update Existing Key");
        System.out.println("Updating key1=newvalue");
        leader.appendCommand(new LogEntry.Command("SET", "key1", "newvalue"));
        Thread.sleep(500);
        
        System.out.println("\nTest 3: Multiple Keys");
        System.out.println("Setting key2=value2, key3=value3");
        leader.appendCommand(new LogEntry.Command("SET", "key2", "value2"));
        leader.appendCommand(new LogEntry.Command("SET", "key3", "value3"));
        Thread.sleep(500);
        
        // Read from all nodes
        System.out.println("\nVerifying consistency across all nodes:");
        for (RaftNode node : nodes) {
            System.out.println("\nNode " + node.getId() + " state:");
            System.out.println("key1=" + node.getStateMachine().get("key1"));
            System.out.println("key2=" + node.getStateMachine().get("key2"));
            System.out.println("key3=" + node.getStateMachine().get("key3"));
            System.out.println("Commit index: " + node.getCommitIndex());
            System.out.println("Log size: " + (node.getLog().getLastLogIndex() + 1));
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
