package com.raftly;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class StateMachine {
    private final Map<String, String> state = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void applyCommand(String key, String value) {
        lock.writeLock().lock();
        try {
            state.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getValue(String key) {
        lock.readLock().lock();
        try {
            return state.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clearState() {
        lock.writeLock().lock();
        try {
            state.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
