package com.raftly.persistence;

import com.raftly.Log;
import com.raftly.persistence.StateMachineSnapshot;
import java.io.Serializable;

public class PersistentState implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final int currentTerm;
    private final int votedFor;
    private final Log log;
    private final StateMachineSnapshot stateMachineSnapshot;

    public PersistentState(int currentTerm, int votedFor, Log log, StateMachineSnapshot stateMachineSnapshot) {
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        this.log = log;
        this.stateMachineSnapshot = stateMachineSnapshot;
    }

    public int getCurrentTerm() {
        return currentTerm;
    }

    public int getVotedFor() {
        return votedFor;
    }

    public Log getLog() {
        return log;
    }

    public StateMachineSnapshot getStateMachineSnapshot() {
        return stateMachineSnapshot;
    }
}
