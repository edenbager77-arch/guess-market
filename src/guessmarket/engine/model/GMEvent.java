package guessmarket.engine.model;

import java.util.ArrayList;
import java.util.List;

/** Base class for both trading methods. */
public abstract class GMEvent {

    private int id;   // assigned by the engine when the event is added
    private final String name, description;
    private final int commissionPercent;
    private final CommissionType commissionType;
    private final List<GMOption> options = new ArrayList<>();
    private String marketMakerName;

    private EventStatus status = EventStatus.NOT_STARTED;
    private double account = 0.0;
    private double totalCommissionCollected = 0.0;
    private String winningOption;

    protected GMEvent(int id, String name, String description, int commissionPercent,
                      CommissionType commissionType, List<String> optionNames) {
        this.id = id; this.name = name; this.description = description;
        this.commissionPercent = commissionPercent; this.commissionType = commissionType;
        for (String o : optionNames) options.add(new GMOption(o));
    }

    public abstract EventKind getKind();

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getCommissionPercent() { return commissionPercent; }
    public CommissionType getCommissionType() { return commissionType; }
    public List<GMOption> getOptions() { return options; }
    public GMOption getOption(String n) {
        for (GMOption o : options) if (o.getName().equalsIgnoreCase(n)) return o;
        return null;
    }
    public String getMarketMakerName() { return marketMakerName; }
    public void setMarketMakerName(String n) { this.marketMakerName = n; }

    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus s) { this.status = s; }
    public double getAccount() { return account; }
    public void addToAccount(double a) { this.account += a; }
    public void setAccount(double a) { this.account = a; }
    public double getTotalCommissionCollected() { return totalCommissionCollected; }
    public void addCommission(double a) { this.totalCommissionCollected += a; }
    public String getWinningOption() { return winningOption; }
    public void setWinningOption(String w) { this.winningOption = w; }

    /** Money the MM must put up to open this event. */
    public abstract double openingCost();
}
