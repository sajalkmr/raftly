package com.raftly;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.io.Serializable;

public class Log implements Serializable {
    private final List<LogEntry> entries;
    private final ReentrantReadWriteLock lock;
    private int lastLogIndex;
    private int lastLogTerm;

    public Log() {
        this.entries = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
        this.lastLogIndex = -1;
        this.lastLogTerm = 0;
    }

    public void append(LogEntry entry) {
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

    public void takeSnapshot() {
        // TODO: Implement log compaction
        System.out.println("Snapshot taken of log entries.");
    }
}
