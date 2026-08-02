package guessmarket.engine.service;

public class PurchaseResult {

    private final String optionName;
    private final double quantity;
    private final double sharesCost;
    private final double commission;
    private final double totalPaid;

    public PurchaseResult(String optionName, double quantity, double sharesCost,
                           double commission, double totalPaid) {
        this.optionName = optionName;
        this.quantity = quantity;
        this.sharesCost = sharesCost;
        this.commission = commission;
        this.totalPaid = totalPaid;
    }

    public String getOptionName() {
        return optionName;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getSharesCost() {
        return sharesCost;
    }

    public double getCommission() {
        return commission;
    }

    public double getTotalPaid() {
        return totalPaid;
    }
}
