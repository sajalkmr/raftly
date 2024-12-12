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
    
    public static class Command implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String operation;
        private final String key;
        private final String value;

        public Command(String operation, String key, String value) {
            this.operation = operation;
            this.key = key;
            this.value = value;
        }

        public String operation() {
            return operation;
        }

        public String key() {
            return key;
        }

        public String value() {
            return value;
        }
    }
}
