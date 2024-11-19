package com.raftly;

import java.io.Serializable;

public record LogEntry(
    int term,
    int index,
    Command command
) implements Serializable {
    public static record Command(
        String operation,
        byte[] data
    ) implements Serializable {}
}
