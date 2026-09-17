package guessmarket.engine.model;

/** One executed LMSR purchase. pricePaid excludes commission. */
public class Trade {
    private final String userName, optionName;
    private final double quantity, pricePaid, commissionPaid;

    public Trade(String userName, String optionName, double quantity, double pricePaid, double commissionPaid) {
        this.userName = userName; this.optionName = optionName;
        this.quantity = quantity; this.pricePaid = pricePaid; this.commissionPaid = commissionPaid;
    }
    public String getUserName() { return userName; }
    public String getOptionName() { return optionName; }
    public double getQuantity() { return quantity; }
    public double getPricePaid() { return pricePaid; }
    public double getCommissionPaid() { return commissionPaid; }
}
