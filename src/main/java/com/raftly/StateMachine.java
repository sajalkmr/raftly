package main.java.com.raftly;

import java.util.HashMap;
import java.util.Map;

public class StateMachine {
    private Map<String, String> state = new HashMap<>();

    public void applyCommand(String key, String value) {
        state.put(key, value);
    }

    public String getValue(String key) {
        return state.get(key);
    }

    public void clearState() {
        state.clear();
}
