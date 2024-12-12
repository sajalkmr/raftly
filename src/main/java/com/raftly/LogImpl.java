package com.raftly;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LogImpl implements Log, Serializable {
    private static final long serialVersionUID = 1L;
    private final List<LogEntry> entries;
    private transient ReentrantReadWriteLock lock;
    private int lastLogIndex;
    private int lastLogTerm;
    private volatile boolean isRunning;

    public LogImpl() {
        this.entries = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
        this.lastLogIndex = -1;
        this.lastLogTerm = 0;
        this.isRunning = true;
    }

    @Override
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

    @Override
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

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return entries.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void removeLast() {
        lock.writeLock().lock();
        try {
            if (!entries.isEmpty()) {
                entries.remove(entries.size() - 1);
                if (entries.isEmpty()) {
                    lastLogIndex = -1;
                    lastLogTerm = 0;
                } else {
                    lastLogIndex = entries.size() - 1;
                    lastLogTerm = entries.get(lastLogIndex).term();
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int getLastLogIndex() {
        return lastLogIndex;
    }

    @Override
    public int getLastLogTerm() {
        return lastLogTerm;
    }

    @Override
    public void appendEntries(List<LogEntry> entries, int prevLogIndex) {
        if (entries == null) {
            throw new IllegalArgumentException("entries cannot be null");
        }
        if (prevLogIndex < -1) {
            throw new IllegalArgumentException("prevLogIndex cannot be less than -1");
        }
        
        lock.writeLock().lock();
        try {
            // Remove any conflicting entries
            while (this.entries.size() > prevLogIndex + 1) {
                this.entries.remove(this.entries.size() - 1);
            }
            
            // Append new entries
            this.entries.addAll(entries);
            
            // Update indices
            if (!this.entries.isEmpty()) {
                lastLogIndex = this.entries.size() - 1;
                lastLogTerm = this.entries.get(lastLogIndex).term();
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

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.lock = new ReentrantReadWriteLock();
    }
}
