package guessmarket.engine.model;

/** An executed order-book transaction (match or mint). */
public class OrderBookTrade {
    private final String optionName, buyerName, sellerName;
    private final double quantity, price;
    private final boolean mint;

    public OrderBookTrade(String optionName, String buyerName, String sellerName,
                          double quantity, double price, boolean mint) {
        this.optionName = optionName; this.buyerName = buyerName; this.sellerName = sellerName;
        this.quantity = quantity; this.price = price; this.mint = mint;
    }
    public String getOptionName() { return optionName; }
    public String getBuyerName() { return buyerName; }
    public String getSellerName() { return sellerName; }
    public double getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public boolean isMint() { return mint; }
}
