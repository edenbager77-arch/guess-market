package guessmarket.engine.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single binary Guess Market event traded via the LMSR method.
 * Exactly two options are supported, per the Ex1 spec.
 */
public class GMEvent {

    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercent;
    private final CommissionType commissionType;
    private final List<GMOption> options;
    private final Map<String, Double> quantities; // option name -> outstanding shares (LMSR q)
    private final int b; // LMSR liquidity parameter

    private double account;
    private double totalCommissionCollected;
    private boolean closed;
    private String winningOption;
    private final List<Trade> history; // chronological order, oldest first

    public GMEvent(int id, String name, String description, int commissionPercent,
                   CommissionType commissionType, List<String> optionNames, int b) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercent = commissionPercent;
        this.commissionType = commissionType;
        this.b = b;
        this.options = new ArrayList<>();
        this.quantities = new LinkedHashMap<>();
        for (String optName : optionNames) {
            options.add(new GMOption(optName));
            quantities.put(optName, 0.0);
        }
        this.history = new ArrayList<>();
        this.account = 0.0;
        this.totalCommissionCollected = 0.0;
        this.closed = false;
        this.winningOption = null;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getCommissionPercent() {
        return commissionPercent;
    }

    public CommissionType getCommissionType() {
        return commissionType;
    }

    public List<GMOption> getOptions() {
        return options;
    }

    public GMOption getOption(String name) {
        for (GMOption o : options) {
            if (o.getName().equalsIgnoreCase(name)) return o;
        }
        return null;
    }

    public int getB() {
        return b;
    }

    public double getQuantity(String optionName) {
        return quantities.get(optionName);
    }

    public void setQuantity(String optionName, double q) {
        quantities.put(optionName, q);
    }

    public double getAccount() {
        return account;
    }

    public void setAccount(double account) {
        this.account = account;
    }

    public void addToAccount(double amount) {
        this.account += amount;
    }

    public double getTotalCommissionCollected() {
        return totalCommissionCollected;
    }

    public void addCommission(double amount) {
        this.totalCommissionCollected += amount;
    }

    public boolean isClosed() {
        return closed;
    }

    public void close(String winningOption) {
        this.closed = true;
        this.winningOption = winningOption;
    }

    public String getWinningOption() {
        return winningOption;
    }

    public List<Trade> getHistory() {
        return history;
    }

    public void addTrade(Trade t) {
        history.add(t);
    }
}
