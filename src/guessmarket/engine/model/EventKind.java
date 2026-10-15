package guessmarket.engine.model;

public enum EventKind {
    LMSR("LMSR"), ORDER_BOOK("Order Book");
    private final String display;
    EventKind(String d) { this.display = d; }
    public String displayName() { return display; }
}
