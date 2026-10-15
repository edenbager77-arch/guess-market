package guessmarket.engine.model;

public enum CommissionType {
    ON_CLOSE("on-close"), ON_PURCHASE("on-purchase");
    private final String xmlValue;
    CommissionType(String v) { this.xmlValue = v; }
    public String getXmlValue() { return xmlValue; }
    public static CommissionType fromXmlValue(String v) {
        if (v == null) return null;
        for (CommissionType t : values()) if (t.xmlValue.equalsIgnoreCase(v.trim())) return t;
        return null;
    }
    public String displayName() { return this == ON_CLOSE ? "On Close" : "On Purchase"; }
}
