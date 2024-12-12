package com.raftly;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

class RaftNodeTest {
    private RaftNode node;
    private RaftCluster mockCluster;
    private StateMachine mockStateMachine;
    private Log mockLog;
    private List<RaftNode> mockNodes;

    @BeforeEach
    void setUp() {
        mockCluster = mock(RaftCluster.class);
        mockStateMachine = mock(StateMachine.class);
        mockLog = mock(Log.class);
        mockNodes = new ArrayList<>();
        
        // Initialize cluster with 3 mock nodes
        for (int i = 0; i < 3; i++) {
            mockNodes.add(mock(RaftNode.class));
        }
        
        when(mockCluster.getNodes()).thenReturn(mockNodes);
        when(mockLog.getLastLogIndex()).thenReturn(-1);
        
        node = new RaftNode(1, mockCluster, mockStateMachine, mockLog, null);
    }

    @Test
    @DisplayName("Should start as follower")
    void startsAsFollower() {
        assertEquals(-1, node.getVotedFor());
        assertEquals(0, node.getCurrentTerm());
        assertFalse(node.isLeader());
    }

    @Test
    @DisplayName("Should handle vote requests correctly")
    void handlesVoteRequests() {
        when(mockLog.getLastLogTerm()).thenReturn(1);
        when(mockLog.getLastLogIndex()).thenReturn(1);
        
        // Higher term should grant vote
        boolean voteGranted = node.handleVoteRequest(2, 2, 1, 1);
        assertTrue(voteGranted);
        assertEquals(2, node.getVotedFor());
        
        // Lower term should deny vote
        voteGranted = node.handleVoteRequest(3, 1, 1, 1);
        assertFalse(voteGranted);
        assertEquals(2, node.getVotedFor());
    }

    @Test
    @DisplayName("Should handle append entries correctly")
    void handlesAppendEntries() {
        LogEntry.Command cmd = new LogEntry.Command("SET", "key1", "value1");
        List<LogEntry> entries = new ArrayList<>();
        entries.add(new LogEntry(1, 0, cmd));
        
        when(mockLog.getLastLogTerm()).thenReturn(0);
        when(mockLog.getLastLogIndex()).thenReturn(-1);
        
        AppendEntriesResult result = node.appendEntries(1, 2, -1, 0, entries, 0);
        assertTrue(result.success());
        assertEquals(1, result.term());
        
        verify(mockLog).appendEntries(eq(entries), eq(-1));
    }

    @Test
    @DisplayName("Should reject append entries with stale term")
    void rejectsStaleTermEntries() {
        node.setCurrentTerm(2);
        
        List<LogEntry> entries = new ArrayList<>();
        AppendEntriesResult result = node.appendEntries(1, 1, 0, 0, entries, 0);
        
        assertFalse(result.success());
        assertEquals(2, result.term());
        verify(mockLog, never()).appendEntries(any(), anyInt());
    }

    @Test
    @DisplayName("Should update commit index")
    void updatesCommitIndex() {
        // Setup mock log with entries
        when(mockLog.getLastLogIndex()).thenReturn(2);
        when(mockLog.getEntry(anyInt())).thenReturn(new LogEntry(1, 0, new LogEntry.Command("SET", "key1", "value1")));
        
        // Test commit index update
        node.setCurrentTerm(1);
        AppendEntriesResult result = node.appendEntries(1, 2, -1, 0, new ArrayList<>(), 2);
        
        assertTrue(result.success());
        assertEquals(2, node.getCommitIndex());
        verify(mockLog, atLeastOnce()).getEntry(anyInt());
    }

    @Test
    @DisplayName("Should handle log replication")
    void handlesLogReplication() {
        // Setup mock log
        LogEntry.Command cmd = new LogEntry.Command("SET", "key1", "value1");
        List<LogEntry> entries = new ArrayList<>();
        entries.add(new LogEntry(1, 0, cmd));
        
        when(mockLog.getLastLogTerm()).thenReturn(0);
        when(mockLog.getLastLogIndex()).thenReturn(-1);
        
        // Test log replication
        node.appendEntries(1, 2, -1, 0, entries, 0);
        
        verify(mockLog).appendEntries(eq(entries), eq(-1));
    }

    @Test
    @DisplayName("Should stop node gracefully")
    void stopsGracefully() {
        node.start();
        node.stop();
        assertFalse(node.isLeader());
    }
}
