package main.java.com.raftly;

import java.util.ArrayList;
import java.util.List;

public class Log {
    private List<LogEntry> entries;

    public Log() {
        this.entries = new ArrayList<>();
    }

    public void append(LogEntry entry) {
        entries.add(entry);
    }

    public List<LogEntry> getEntries() {
        return entries;
    }

    public void clear() {
        entries.clear();
    }

    public LogEntry getEntry(int index) {
        return entries.get(index);
    }
}
