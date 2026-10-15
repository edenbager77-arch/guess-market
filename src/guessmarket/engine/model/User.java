package guessmarket.engine.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A user of the system. Balance starts from initial-cash in the XML.
 * Per the spec: a user is ALLOWED to go negative once, is told about it,
 * and from that moment on is blocked from any further action.
 */
public class User {

    private final String name;
    private double balance;
    private boolean blocked;
    private final List<AccountEntry> statement = new ArrayList<>();
    // event name -> (option name -> shares held)
    private final Map<String, Map<String, Double>> holdings = new LinkedHashMap<>();
    private final List<String> mmOfEvents = new ArrayList<>();
    // per-event bookkeeping so the UI can show what a user paid, what
    // commission they were charged, and their profit/loss once an event closes
    private final Map<String, Map<String, Double>> paidPerOption = new LinkedHashMap<>();
    private final Map<String, Double> commissionPerEvent = new LinkedHashMap<>();
    private final Map<String, Double> netPerEvent = new LinkedHashMap<>();

    public User(String name, double initialCash) {
        this.name = name;
        this.balance = initialCash;
        this.blocked = false;
        statement.add(new AccountEntry("Initial balance", initialCash, initialCash));
    }

    public String getName() { return name; }
    public double getBalance() { return balance; }
    public boolean isBlocked() { return blocked; }
    public List<AccountEntry> getStatement() { return statement; }
    public List<String> getMmOfEvents() { return mmOfEvents; }
    public void addMmEvent(String eventName) { mmOfEvents.add(eventName); }
    public boolean isMarketMaker() { return !mmOfEvents.isEmpty(); }

    /** Applies a signed change and records it. Blocks the user if the balance goes negative. */
    public void applyChange(double amount, String description) {
        this.balance += amount;
        statement.add(new AccountEntry(description, amount, this.balance));
        if (this.balance < 0) {
            this.blocked = true;
        }
    }

    public boolean canAfford(double amount) { return balance >= amount; }

    public Map<String, Map<String, Double>> getHoldings() { return holdings; }

    public double getShares(String eventName, String optionName) {
        Map<String, Double> perEvent = holdings.get(eventName);
        if (perEvent == null) return 0.0;
        return perEvent.getOrDefault(optionName, 0.0);
    }

    public void addShares(String eventName, String optionName, double q) {
        holdings.computeIfAbsent(eventName, k -> new LinkedHashMap<>())
                .merge(optionName, q, Double::sum);
    }

    /** Gross amount this user paid for a given option of a given event. */
    public double getPaidForOption(String eventName, String optionName) {
        Map<String, Double> m = paidPerOption.get(eventName);
        return m == null ? 0.0 : m.getOrDefault(optionName, 0.0);
    }

    public void addPaidForOption(String eventName, String optionName, double amount) {
        paidPerOption.computeIfAbsent(eventName, k -> new LinkedHashMap<>())
                .merge(optionName, amount, Double::sum);
    }

    /** Total commission this user has paid within a given event. */
    public double getCommissionPaid(String eventName) {
        return commissionPerEvent.getOrDefault(eventName, 0.0);
    }

    public void addCommissionPaid(String eventName, double amount) {
        commissionPerEvent.merge(eventName, amount, Double::sum);
    }

    /**
     * Signed net cash flow for this event: money out is negative, money in
     * is positive. After the event closes this is the profit or loss.
     */
    public double getNetForEvent(String eventName) {
        return netPerEvent.getOrDefault(eventName, 0.0);
    }

    public void addNetForEvent(String eventName, double signedAmount) {
        netPerEvent.merge(eventName, signedAmount, Double::sum);
    }

    /** Event names this user is participating in (holds shares or placed an order). */
    public List<String> getParticipatingEvents() { return new ArrayList<>(holdings.keySet()); }

    public void ensureParticipation(String eventName) {
        holdings.computeIfAbsent(eventName, k -> new LinkedHashMap<>());
    }
}
