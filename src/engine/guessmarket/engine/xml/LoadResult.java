package guessmarket.engine.xml;

import guessmarket.engine.model.GMEvent;
import guessmarket.engine.model.User;

import java.util.Collections;
import java.util.List;

public class LoadResult {
    private final boolean valid;
    private final List<String> errors;
    private final List<GMEvent> events;
    private final List<User> users;

    private LoadResult(boolean v, List<String> e, List<GMEvent> ev, List<User> u) {
        this.valid = v; this.errors = e; this.events = ev; this.users = u;
    }
    public static LoadResult ok(List<GMEvent> ev, List<User> u) {
        return new LoadResult(true, Collections.emptyList(), ev, u);
    }
    public static LoadResult fail(List<String> e) {
        return new LoadResult(false, e, Collections.emptyList(), Collections.emptyList());
    }
    public boolean isValid() { return valid; }
    public List<String> getErrors() { return errors; }
    public List<GMEvent> getEvents() { return events; }
    public List<User> getUsers() { return users; }
}
