package guessmarket.engine.model;

/**
 * A single resting order in an option's order book.
 * price is per share, strictly less than the event's d (base value).
 */
public class Order {
    private static long SEQ = 0;

    private final long id;
    private final String userName;
    private final OrderSide side;
    private final String optionName;
    private final double price;
    private double remainingQuantity;
    private final double originalQuantity;

    public Order(String userName, OrderSide side, String optionName, double price, double quantity) {
        this.id = ++SEQ;
        this.userName = userName;
        this.side = side;
        this.optionName = optionName;
        this.price = price;
        this.remainingQuantity = quantity;
        this.originalQuantity = quantity;
    }

    public long getId() { return id; }
    public String getUserName() { return userName; }
    public OrderSide getSide() { return side; }
    public String getOptionName() { return optionName; }
    public double getPrice() { return price; }
    public double getRemainingQuantity() { return remainingQuantity; }
    public double getOriginalQuantity() { return originalQuantity; }
    public void reduce(double q) { this.remainingQuantity -= q; }
    public boolean isFilled() { return remainingQuantity <= 1e-9; }
}
