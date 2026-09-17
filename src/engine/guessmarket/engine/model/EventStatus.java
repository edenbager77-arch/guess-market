package guessmarket.engine.model;

/** The three lifecycle stages of an Ex2 event. */
public enum EventStatus {
    NOT_STARTED("Not Started"), ACTIVE("Active"), CLOSED("Closed");
    private final String display;
    EventStatus(String d) { this.display = d; }
    public String displayName() { return display; }
}
