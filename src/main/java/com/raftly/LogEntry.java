package com.raftly;

import java.io.Serializable;

public record LogEntry(
    int term,
    int index,
    Command command
) implements Serializable {
    public LogEntry {
        if (term < 0) throw new IllegalArgumentException("Term cannot be negative");
        if (index < 0) throw new IllegalArgumentException("Index cannot be negative");
        if (command == null) throw new IllegalArgumentException("Command cannot be null");
    }
    
    public static record Command(
        String operation,
        byte[] data
    ) implements Serializable {
        public Command {
            if (operation == null) throw new IllegalArgumentException("Operation cannot be null");
            if (data == null) throw new IllegalArgumentException("Data cannot be null");
        }
    }
}
