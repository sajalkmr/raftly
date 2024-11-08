package main.java.com.raftly;

public class LogEntry {
    private int term;
    private String command;
    private long timestamp;

    public LogEntry(int term, String command) {
        this.term = term;
        this.command = command;
        this.timestamp = System.currentTimeMillis();
    }

    public int getTerm() {
        return term;
    }

    public String getCommand() {
        return command;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
