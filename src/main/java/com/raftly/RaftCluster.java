package com.raftly;

import java.util.ArrayList;
import java.util.List;

public class RaftCluster {
    private final List<RaftNode> nodes;

    public RaftCluster() {
        this.nodes = new ArrayList<>();
    }

    public RaftCluster(List<RaftNode> nodes) {
        this.nodes = new ArrayList<>(nodes);
        for (RaftNode node : nodes) {
            node.setCluster(this);
        }
    }

    public void addNode(RaftNode node) {
        nodes.add(node);
        for (RaftNode existingNode : nodes) {
            if (existingNode != node) {
                existingNode.updateClusterConfiguration(this);
            }
        }
        node.updateClusterConfiguration(this);
    }

    public void removeNode(RaftNode node) {
        nodes.remove(node);
        for (RaftNode remainingNode : nodes) {
            remainingNode.updateClusterConfiguration(this);
        }
    }

    public List<RaftNode> getNodes() {
        return nodes;
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