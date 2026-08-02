package guessmarket.engine.model;

/**
 * A single executed purchase in an event's history.
 * pricePaid is the LMSR cost of the shares themselves (NOT including commission).
 */
public class Trade {

    private final String optionName;
    private final double quantity;
    private final double pricePaid;

    public Trade(String optionName, double quantity, double pricePaid) {
        this.optionName = optionName;
        this.quantity = quantity;
        this.pricePaid = pricePaid;
    }

    public String getOptionName() {
        return optionName;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getPricePaid() {
        return pricePaid;
    }
}
