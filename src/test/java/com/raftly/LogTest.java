package com.raftly;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;

class LogTest {
    private Log log;

    @BeforeEach
    void setUp() {
        log = new Log();
    }

    @Test
    @DisplayName("Should append entries correctly")
    void appendsEntriesCorrectly() {
        LogEntry.Command cmd1 = new LogEntry.Command("SET", "key1", "value1");
        LogEntry.Command cmd2 = new LogEntry.Command("SET", "key2", "value2");
        
        LogEntry entry1 = new LogEntry(1, 1, cmd1);
        LogEntry entry2 = new LogEntry(1, 2, cmd2);
        
        log.append(entry1);
        log.append(entry2);
        
        assertEquals(1, log.getLastLogIndex());
        assertEquals(entry1, log.getEntry(0));
        assertEquals(entry2, log.getEntry(1));
    }

    @Test
    @DisplayName("Should get last log term correctly")
    void getsLastLogTermCorrectly() {
        LogEntry.Command cmd1 = new LogEntry.Command("SET", "key1", "value1");
        LogEntry.Command cmd2 = new LogEntry.Command("SET", "key2", "value2");
        
        log.append(new LogEntry(1, 1, cmd1));
        log.append(new LogEntry(2, 2, cmd2));
        
        assertEquals(2, log.getLastLogTerm());
    }

    @Test
    @DisplayName("Should truncate from index correctly")
    void truncatesCorrectly() {
        LogEntry.Command cmd1 = new LogEntry.Command("SET", "key1", "value1");
        LogEntry.Command cmd2 = new LogEntry.Command("SET", "key2", "value2");
        LogEntry.Command cmd3 = new LogEntry.Command("SET", "key3", "value3");
        
        log.append(new LogEntry(1, 1, cmd1));
        log.append(new LogEntry(1, 2, cmd2));
        log.append(new LogEntry(2, 3, cmd3));
        
        log.truncateFrom(1);
        
        assertEquals(0, log.getLastLogIndex());
        assertEquals("key1", log.getEntry(0).command().key());
    }

    @Test
    @DisplayName("Should handle entry conflicts correctly")
    void handlesEntryConflicts() {
        LogEntry.Command cmd1 = new LogEntry.Command("SET", "key1", "value1");
        LogEntry.Command cmd2 = new LogEntry.Command("SET", "key2", "value2");
        LogEntry.Command cmdConflict = new LogEntry.Command("SET", "key2", "different");
        
        log.append(new LogEntry(1, 1, cmd1));
        log.append(new LogEntry(1, 2, cmd2));
        
        // Replace all entries by using prevLogIndex = -1
        List<LogEntry> newEntries = new ArrayList<>();
        newEntries.add(new LogEntry(1, 1, cmd1));
        newEntries.add(new LogEntry(1, 2, cmdConflict));
        
        log.appendEntries(newEntries, -1);
        
        assertEquals(1, log.getLastLogIndex());
        assertEquals("different", log.getEntry(1).command().value());
    }

    @Test
    @DisplayName("Should handle empty log correctly")
    void handlesEmptyLog() {
        assertEquals(-1, log.getLastLogIndex());
        assertEquals(0, log.getLastLogTerm());
    }

    @Test
    @DisplayName("Should handle invalid indices gracefully")
    void handlesInvalidIndices() {
        LogEntry.Command cmd = new LogEntry.Command("SET", "key1", "value1");
        log.append(new LogEntry(1, 1, cmd));
        
        assertNull(log.getEntry(-1));
        assertNull(log.getEntry(2));
    }

    @Test
    @DisplayName("Should verify log entries correctly")
    void verifiesLogEntries() {
        LogEntry.Command cmd1 = new LogEntry.Command("SET", "key1", "value1");
        LogEntry.Command cmd2 = new LogEntry.Command("SET", "key2", "value2");
        
        log.append(new LogEntry(1, 1, cmd1));
        log.append(new LogEntry(2, 2, cmd2));
        
        assertTrue(log.hasEntry(0, 1));
        assertTrue(log.hasEntry(1, 2));
        assertFalse(log.hasEntry(1, 1));
        assertFalse(log.hasEntry(2, 2));
    }

    @Test
    @DisplayName("Should get entries from index")
    void getsEntriesFromIndex() {
        LogEntry.Command cmd1 = new LogEntry.Command("SET", "key1", "value1");
        LogEntry.Command cmd2 = new LogEntry.Command("SET", "key2", "value2");
        LogEntry.Command cmd3 = new LogEntry.Command("SET", "key3", "value3");
        
        log.append(new LogEntry(1, 1, cmd1));
        log.append(new LogEntry(1, 2, cmd2));
        log.append(new LogEntry(2, 3, cmd3));
        
        List<LogEntry> entries = log.getEntriesFrom(1);
        assertEquals(2, entries.size());
        assertEquals("key2", entries.get(0).command().key());
        assertEquals("key3", entries.get(1).command().key());
    }
}
