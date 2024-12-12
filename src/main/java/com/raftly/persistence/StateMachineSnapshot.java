package com.raftly.persistence;

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;

public class StateMachineSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, String> state;

    public StateMachineSnapshot(Map<String, String> state) {
        this.state = new HashMap<>(state);
    }

    public Map<String, String> getState() {
        return new HashMap<>(state);
    }
}
