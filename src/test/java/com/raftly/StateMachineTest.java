package com.raftly;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

class StateMachineTest {
    private StateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new StateMachine();
    }

    @Test
    @DisplayName("Should apply SET commands correctly")
    void appliesSetCommandsCorrectly() {
        LogEntry.Command cmd1 = new LogEntry.Command("SET", "key1", "value1");
        LogEntry.Command cmd2 = new LogEntry.Command("SET", "key2", "value2");
        
        stateMachine.apply(cmd1);
        stateMachine.apply(cmd2);
        
        assertEquals("value1", stateMachine.get("key1"));
        assertEquals("value2", stateMachine.get("key2"));
    }

    @Test
    @DisplayName("Should handle DELETE operations correctly")
    void handlesDeleteOperations() {
        LogEntry.Command setCmd = new LogEntry.Command("SET", "key1", "value1");
        LogEntry.Command deleteCmd = new LogEntry.Command("DELETE", "key1", null);
        
        stateMachine.apply(setCmd);
        stateMachine.apply(deleteCmd);
        
        assertNull(stateMachine.get("key1"));
    }

    @Test
    @DisplayName("Should handle invalid operations gracefully")
    void handlesInvalidOperations() {
        LogEntry.Command invalidCmd = new LogEntry.Command("INVALID", "key1", "value1");
        
        assertThrows(IllegalArgumentException.class, () -> stateMachine.apply(invalidCmd));
    }

    @Test
    @DisplayName("Should handle non-existent keys")
    void handlesNonExistentKeys() {
        assertNull(stateMachine.get("nonexistent"));
    }

    @Test
    @DisplayName("Should maintain consistency in concurrent operations")
    void maintainsConsistencyInConcurrentOperations() {
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                LogEntry.Command cmd = new LogEntry.Command(
                    "SET", 
                    "key" + index, 
                    "value" + index
                );
                stateMachine.apply(cmd);
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                fail("Test interrupted");
            }
        }
        
        for (int i = 0; i < 10; i++) {
            assertEquals("value" + i, stateMachine.get("key" + i));
        }
    }

    @Test
    @DisplayName("Should create correct snapshot")
    void createsCorrectSnapshot() {
        stateMachine.apply(new LogEntry.Command("SET", "key1", "value1"));
        stateMachine.apply(new LogEntry.Command("SET", "key2", "value2"));
        
        Map<String, String> snapshot = stateMachine.getSnapshot();
        
        assertEquals("value1", snapshot.get("key1"));
        assertEquals("value2", snapshot.get("key2"));
        assertEquals(2, snapshot.size());
        
        // Verify snapshot is a deep copy
        snapshot.put("key3", "value3");
        assertNull(stateMachine.get("key3"));
    }
}
