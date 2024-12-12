package com.raftly;

import java.util.Arrays;
import java.util.List;
import java.io.File;
import com.raftly.persistence.FileStorage;
import com.raftly.persistence.PersistentState;
import com.raftly.persistence.Storage;

public class RaftDemo {
    private static List<RaftNode> startCluster(boolean loadExisting) {
        System.out.println("\nStarting Raft cluster" + (loadExisting ? " (loading existing state)" : "") + "...");
        
        // Init state machines
        StateMachine stateMachine0 = new StateMachine();
        StateMachine stateMachine1 = new StateMachine();
        StateMachine stateMachine2 = new StateMachine();
        
        // Init storage
        String persistenceDir = "raft_data";
        FileStorage storage0 = new FileStorage(persistenceDir, "0");
        FileStorage storage1 = new FileStorage(persistenceDir, "1");
        FileStorage storage2 = new FileStorage(persistenceDir, "2");
        
        // Try to load existing state
        PersistentState state0 = loadExisting ? storage0.load() : null;
        PersistentState state1 = loadExisting ? storage1.load() : null;
        PersistentState state2 = loadExisting ? storage2.load() : null;
        
        // Init logs
        Log log0 = state0 != null ? state0.getLog() : new Log();
        Log log1 = state1 != null ? state1.getLog() : new Log();
        Log log2 = state2 != null ? state2.getLog() : new Log();
        
        // Init nodes with storage
        RaftNode node0 = new RaftNode("0", stateMachine0, log0, storage0);
        RaftNode node1 = new RaftNode("1", stateMachine1, log1, storage1);
        RaftNode node2 = new RaftNode("2", stateMachine2, log2, storage2);
        
        // Set up cluster configuration
        RaftCluster cluster = new RaftCluster(Arrays.asList(node0, node1, node2));
        node0.updateClusterConfiguration(cluster);
        node1.updateClusterConfiguration(cluster);
        node2.updateClusterConfiguration(cluster);
        
        // Start nodes
        node0.start();
        node1.start();
        node2.start();
        
        return Arrays.asList(node0, node1, node2);
    }
    
    private static void shutdownCluster(List<RaftNode> nodes) {
        System.out.println("\nShutting down cluster...");
        for (RaftNode node : nodes) {
            node.stop();
        }
    }
    
    private static RaftNode waitForLeader(List<RaftNode> nodes) throws InterruptedException {
        System.out.println("\nWaiting for leader election...");
        Thread.sleep(2000);
        
        // Find leader
        for (RaftNode node : nodes) {
            if (node.isLeader()) {
                System.out.println("Leader elected: Node " + node.getId() + "\n");
                return node;
            }
        }
        return null;
    }
    
    private static void verifyClusterState(List<RaftNode> nodes) {
        System.out.println("\nVerifying consistency across all nodes:");
        for (RaftNode node : nodes) {
            StateMachine sm = node.getStateMachine();
            System.out.println("\nNode " + node.getId() + " state:");
            System.out.println("key1=" + sm.get("key1"));
            System.out.println("key2=" + sm.get("key2"));
            System.out.println("key3=" + sm.get("key3"));
            System.out.println("Commit index: " + node.getCommitIndex());
            System.out.println("Log size: " + node.getLog().size());
            System.out.println("Current term: " + node.getCurrentTerm());
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Test 1: Initial cluster startup and operations
        System.out.println("=== Test 1: Initial Cluster Operations ===");
        List<RaftNode> nodes = startCluster(false);
        RaftNode leader = waitForLeader(nodes);
        
        if (leader != null) {
            System.out.println("Making some state changes...");
            leader.appendCommand(new LogEntry.Command("SET", "key1", "value1"));
            leader.appendCommand(new LogEntry.Command("SET", "key2", "value2"));
            Thread.sleep(1000); // Wait for replication
            verifyClusterState(nodes);
        }
        
        // Shutdown cluster
        shutdownCluster(nodes);
        Thread.sleep(1000);
        
        // Test 2: Restart cluster and verify state recovery
        System.out.println("\n=== Test 2: State Recovery After Restart ===");
        nodes = startCluster(true);
        leader = waitForLeader(nodes);
        verifyClusterState(nodes);
        
        if (leader != null) {
            System.out.println("\nMaking additional changes after recovery...");
            leader.appendCommand(new LogEntry.Command("SET", "key3", "value3"));
            Thread.sleep(1000); // Wait for replication
            verifyClusterState(nodes);
        }
        
        // Test 3: Node crash and recovery
        System.out.println("\n=== Test 3: Node Crash and Recovery ===");
        // Stop one follower
        RaftNode crashedNode = nodes.stream()
            .filter(n -> !n.isLeader())
            .findFirst()
            .get();
        System.out.println("Crashing Node " + crashedNode.getId());
        crashedNode.stop();
        Thread.sleep(1000);
        
        // Make some changes while node is down
        if (leader != null) {
            System.out.println("\nMaking changes while Node " + crashedNode.getId() + " is down...");
            leader.appendCommand(new LogEntry.Command("SET", "key1", "new_value"));
            Thread.sleep(1000);
        }
        
        // Restart the crashed node
        System.out.println("\nRestarting Node " + crashedNode.getId());
        String crashedId = crashedNode.getId();
        FileStorage storage = new FileStorage("raft_data", crashedId);
        PersistentState state = storage.load();
        RaftNode newNode = new RaftNode(crashedId, new RaftCluster(nodes), 
                                      new StateMachine(), 
                                      state != null ? state.getLog() : new Log(),
                                      storage);
        nodes.set(nodes.indexOf(crashedNode), newNode);
        newNode.start();
        Thread.sleep(2000); // Wait for recovery
        
        // Verify final state
        System.out.println("\nFinal cluster state after all tests:");
        verifyClusterState(nodes);
        
        // Final shutdown
        shutdownCluster(nodes);
    }
}
