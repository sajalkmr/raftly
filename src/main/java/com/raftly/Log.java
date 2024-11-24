package com.raftly;

import com.raftly.LogEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.io.Serializable;

public class Log implements Serializable {
    private final List<LogEntry> entries;
    private final ReentrantReadWriteLock lock;
    private int lastLogIndex;
    private int lastLogTerm;
    private volatile int lastApplied = -1;

    public Log() {
        this.entries = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
        this.lastLogIndex = -1;
        this.lastLogTerm = 0;
    }

    public void append(LogEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Entry cannot be null");
        }
        
        lock.writeLock().lock();
        try {
            entries.add(entry);
            lastLogIndex = entries.size() - 1;
            lastLogTerm = entry.term();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void appendEntries(List<LogEntry> newEntries, int prevLogIndex) {
        if (newEntries == null) {
            throw new IllegalArgumentException("newEntries cannot be null");
        }
        if (prevLogIndex < -1) {
            throw new IllegalArgumentException("prevLogIndex cannot be less than -1");
        }
        
        lock.writeLock().lock();
        try {
            // Remove any conflicting entries
            while (entries.size() > prevLogIndex + 1) {
                entries.remove(entries.size() - 1);
            }
            
            // Append new entries
            entries.addAll(newEntries);
            
            // Update indices
            if (!entries.isEmpty()) {
                lastLogIndex = entries.size() - 1;
                lastLogTerm = entries.get(lastLogIndex).term();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean hasEntry(int index, int term) {
        lock.readLock().lock();
        try {
            if (index < 0 || index >= entries.size()) return false;
            return entries.get(index).term() == term;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<LogEntry> getEntries() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(entries);
        } finally {
            lock.readLock().unlock();
        }
    }

    public LogEntry getEntry(int index) {
        lock.readLock().lock();
        try {
            if (index < 0 || index >= entries.size()) {
                return null;  
            }
            return entries.get(index);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getLastLogIndex() {
        return lastLogIndex;
    }

    public int getLastLogTerm() {
        return lastLogTerm;
    }

    public int getTermAt(int index) {
        lock.readLock().lock();
        try {
            if (index < 0 || index >= entries.size()) {
                return 0;
            }
            return entries.get(index).term();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<LogEntry> getEntriesFrom(int index) {
        lock.readLock().lock();
        try {
            if (index >= entries.size()) {
                return new ArrayList<>();
            }
            return new ArrayList<>(entries.subList(index, entries.size()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void takeSnapshot() {
        throw new UnsupportedOperationException("Log compaction not yet implemented");
    }

    // Verify log entry at prevLogIndex matches prevLogTerm
    public boolean matchesLog(int prevLogIndex, int prevLogTerm) {
        lock.readLock().lock();
        try {
            if (prevLogIndex < 0) {
                return true; 
            }
            if (prevLogIndex >= entries.size()) {
                return false;
            }
            return entries.get(prevLogIndex).term() == prevLogTerm;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Handle new entries from leader
    public boolean appendEntries(int prevLogIndex, int prevLogTerm, List<LogEntry> newEntries) {
        if (!matchesLog(prevLogIndex, prevLogTerm)) {
            return false;
        }

        lock.writeLock().lock();
        try {
            // Remove any conflicting entries
            int newIndex = prevLogIndex + 1;
            while (entries.size() > newIndex) {
                entries.remove(entries.size() - 1);
            }

            // Append new entries
            entries.addAll(newEntries);

            // Update indices
            if (!entries.isEmpty()) {
                lastLogIndex = entries.size() - 1;
                lastLogTerm = entries.get(lastLogIndex).term();
            }

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Apply entries to state machine
    public void applyToStateMachine(StateMachine stateMachine, int commitIndex) {
        lock.readLock().lock();
        try {
            for (int i = lastApplied + 1; i <= commitIndex && i < entries.size(); i++) {
                stateMachine.apply(entries.get(i).command());
                lastApplied = i;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public void truncateFrom(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Index cannot be negative");
        }
        
        lock.writeLock().lock();
        try {
            while (entries.size() > index) {
                entries.remove(entries.size() - 1);
            }
            if (entries.isEmpty()) {
                lastLogIndex = -1;
                lastLogTerm = 0;
            } else {
                lastLogIndex = entries.size() - 1;
                lastLogTerm = entries.get(lastLogIndex).term();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
