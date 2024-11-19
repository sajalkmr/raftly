package com.raftly.utils;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for the Raft implementation
 */
public final class Utils {
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);
    private static final Random random = new Random();

    private Utils() {
        // Prevent instantiation
    }

    /**
     * Generate a random election timeout between MIN and MAX
     */
    public static long randomElectionTimeout() {
        return Config.ELECTION_TIMEOUT_MIN + 
               random.nextLong(Config.ELECTION_TIMEOUT_MAX - Config.ELECTION_TIMEOUT_MIN);
    }

    /**
     * Safely wait for a CompletableFuture with timeout
     */
    public static <T> T waitForFuture(CompletableFuture<T> future, long timeout, TimeUnit unit) 
            throws TimeoutException {
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error waiting for future", e);
            throw new RuntimeException("Error waiting for future", e);
        }
    }

    /**
     * Check if a number is within a valid range
     */
    public static boolean isInRange(long value, long min, long max) {
        return value >= min && value <= max;
    }

    /**
     * Validate cluster size
     */
    public static boolean isValidClusterSize(int size) {
        return size >= Config.MIN_CLUSTER_SIZE && size <= Config.INITIAL_CLUSTER_SIZE;
    }

    /**
     * Get majority quorum size for a given cluster size
     */
    public static int getQuorumSize(int clusterSize) {
        return (clusterSize / 2) + 1;
    }

    /**
     * Exponential backoff sleep with jitter
     */
    public static void backoffSleep(int attempt) {
        long backoff = Math.min(Config.RETRY_BACKOFF_MS * (1L << attempt), 1000);
        backoff += random.nextLong(backoff / 2); // Add jitter
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during backoff", e);
        }
    }
}
