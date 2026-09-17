package guessmarket.ui;

/** Small formatting helpers so every screen shows numbers the same way. */
public final class Fmt {
    private Fmt() {}
    public static String money(double v) { return String.format("%.2f", v); }
    public static String qty(double v) { return String.format("%.2f", v); }
    public static String price(Double v) { return v == null ? "-" : String.format("%.2f", v); }
}
