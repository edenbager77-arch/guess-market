package guessmarket.engine.xml;

import guessmarket.engine.model.GMEvent;

import java.util.Collections;
import java.util.List;

public class EventFileValidationResult {

    private final boolean valid;
    private final List<String> errors;
    private final List<GMEvent> events;

    private EventFileValidationResult(boolean valid, List<String> errors, List<GMEvent> events) {
        this.valid = valid;
        this.errors = errors;
        this.events = events;
    }

    public static EventFileValidationResult success(List<GMEvent> events) {
        return new EventFileValidationResult(true, Collections.emptyList(), events);
    }

    public static EventFileValidationResult failure(List<String> errors) {
        return new EventFileValidationResult(false, errors, Collections.emptyList());
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<GMEvent> getEvents() {
        return events;
    }
}
