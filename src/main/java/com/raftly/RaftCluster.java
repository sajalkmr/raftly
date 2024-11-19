package com.raftly;

import java.util.List;

public class RaftCluster {
    private List<RaftNode> nodes;

    public RaftCluster(List<RaftNode> nodes) {
        this.nodes = nodes;
    }

    public void startCluster() {
        for (RaftNode node : nodes) {
            new Thread(() -> node.start()).start();
        }
    }

    public void broadcastLogEntries(List<LogEntry> entries) {
        for (RaftNode node : nodes) {
            node.receiveLogEntries(entries);
        }
    }
}