package com.raftly.persistence;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.locks.ReentrantLock;

public class FileStorage implements Storage {
    private final Path storageFile;
    private final ReentrantLock lock;

    public FileStorage(String directory, int nodeId) {
        this.storageFile = Paths.get(directory, "raft-state-" + nodeId + ".bin");
        this.lock = new ReentrantLock();
        
        // Create directory if it doesn't exist
        try {
            Files.createDirectories(storageFile.getParent());
        } catch (IOException e) {
            throw new StorageException("Failed to create storage directory", e);
        }
    }

    @Override
    public void save(PersistentState state) throws StorageException {
        lock.lock();
        try {
            // Write to a temporary file first
            Path tempFile = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                oos.writeObject(state);
                oos.flush();
                
                // Atomic rename for safe persistence
                Files.move(tempFile, storageFile, StandardCopyOption.ATOMIC_MOVE, 
                          StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to save state", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PersistentState load() throws StorageException {
        lock.lock();
        try {
            if (!Files.exists(storageFile)) {
                return null;
            }

            try (ObjectInputStream ois = new ObjectInputStream(
                    new BufferedInputStream(Files.newInputStream(storageFile)))) {
                return (PersistentState) ois.readObject();
            } catch (ClassNotFoundException e) {
                throw new StorageException("Failed to deserialize state", e);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to load state", e);
        } finally {
            lock.unlock();
        }
    }
}
