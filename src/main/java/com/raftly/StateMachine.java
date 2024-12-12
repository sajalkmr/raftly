package com.raftly;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.HashMap;

public class StateMachine {
    private final ConcurrentHashMap<String, String> kvStore;
    private final ReentrantReadWriteLock lock;

    public StateMachine() {
        this.kvStore = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public StateMachine(Map<String, String> initialState) {
        this.kvStore = new ConcurrentHashMap<>(initialState);
        this.lock = new ReentrantReadWriteLock();
    }

    public void apply(LogEntry.Command command) {
        lock.writeLock().lock();
        try {
            switch (command.operation()) {
                case "SET":
                    kvStore.put(command.key(), command.value());
                    break;
                case "DELETE":
                    kvStore.remove(command.key());
                    break;
                default:
                    throw new IllegalArgumentException("Unknown operation: " + command.operation());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String get(String key) {
        lock.readLock().lock();
        try {
            return kvStore.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, String> getSnapshot() {
        lock.readLock().lock();
        try {
            return new HashMap<>(kvStore);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void restoreFromSnapshot(Map<String, String> snapshot) {
        lock.writeLock().lock();
        try {
            kvStore.clear();
            kvStore.putAll(snapshot);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
