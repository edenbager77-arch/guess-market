package guessmarket.engine.model;

/**
 * The two commission collection methods supported by Guess Market.
 * ON_PURCHASE: commission is added on top of every buy, at the moment of purchase.
 * ON_CLOSE: commission is deducted from the winnings when the event is closed.
 */
public enum CommissionType {
    ON_CLOSE("on-close"),
    ON_PURCHASE("on-purchase");

    private final String xmlValue;

    CommissionType(String xmlValue) {
        this.xmlValue = xmlValue;
    }

    public String getXmlValue() {
        return xmlValue;
    }

    public static CommissionType fromXmlValue(String value) {
        if (value == null) return null;
        for (CommissionType t : values()) {
            if (t.xmlValue.equalsIgnoreCase(value.trim())) {
                return t;
            }
        }
        return null;
    }

    public String displayName() {
        return this == ON_CLOSE ? "On Close" : "On Purchase";
    }
}
