package com.raftly.persistence;

public interface Storage {
    /**
     * Save the persistent state to storage
     * @param state The state to persist
     * @throws StorageException if there is an error saving the state
     */
    void save(PersistentState state) throws StorageException;

    /**
     * Load the persistent state from storage
     * @return The loaded state, or null if no state exists
     * @throws StorageException if there is an error loading the state
     */
    PersistentState load() throws StorageException;
}
