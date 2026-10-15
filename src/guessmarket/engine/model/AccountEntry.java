package guessmarket.engine.model;

/** One line in a user's account statement. */
public class AccountEntry {
    private final String description;
    private final double amount;      // signed: negative = money out
    private final double balanceAfter;

    public AccountEntry(String description, double amount, double balanceAfter) {
        this.description = description; this.amount = amount; this.balanceAfter = balanceAfter;
    }
    public String getDescription() { return description; }
    public double getAmount() { return amount; }
    public double getBalanceAfter() { return balanceAfter; }
}
