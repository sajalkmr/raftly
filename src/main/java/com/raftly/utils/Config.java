package com.raftly.utils;

/**
 * Configuration constants for the Raft implementation
 */
public class Config {
    // Cluster configuration
    public static final int INITIAL_CLUSTER_SIZE = 6;
    public static final int MIN_CLUSTER_SIZE = 3;
    
    // Timeouts (in milliseconds)
    public static final long ELECTION_TIMEOUT_MIN = 150;
    public static final long ELECTION_TIMEOUT_MAX = 300;
    public static final long HEARTBEAT_INTERVAL = 50;
    
    // Network configuration
    public static final int DEFAULT_PORT = 50051;
    public static final String DEFAULT_HOST = "localhost";
    
    // Log configuration
    public static final int SNAPSHOT_THRESHOLD = 1000;  // Number of entries before taking snapshot
    public static final int MAX_BATCH_SIZE = 100;      // Maximum entries in a single AppendEntries RPC
    
    // Retry configuration
    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final long RETRY_BACKOFF_MS = 100;
    
    private Config() {
        // Prevent instantiation
    }
}
