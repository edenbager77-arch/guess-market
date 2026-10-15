package guessmarket.engine.model;

import guessmarket.engine.service.LmsrCalculator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LmsrEvent extends GMEvent {

    private final int b;
    private final Map<String, Double> quantities = new LinkedHashMap<>();
    private final List<Trade> history = new ArrayList<>();

    public LmsrEvent(int id, String name, String description, int commissionPercent,
                     CommissionType commissionType, List<String> optionNames, int b) {
        super(id, name, description, commissionPercent, commissionType, optionNames);
        this.b = b;
        for (String o : optionNames) quantities.put(o, 0.0);
    }

    @Override public EventKind getKind() { return EventKind.LMSR; }

    public int getB() { return b; }
    public double getQuantity(String opt) { return quantities.getOrDefault(opt, 0.0); }
    public void setQuantity(String opt, double q) { quantities.put(opt, q); }
    public List<Trade> getHistory() { return history; }
    public void addTrade(Trade t) { history.add(t); }

    /** Initial subsidy the MM pays: C(0,0) = b * ln(2). */
    @Override public double openingCost() { return LmsrCalculator.cost(b, 0, 0); }

    public double priceOf(String optionName) {
        GMOption other = null;
        for (GMOption o : getOptions()) if (!o.getName().equalsIgnoreCase(optionName)) other = o;
        if (other == null) return 0.5;
        return LmsrCalculator.price(b, getQuantity(optionName), getQuantity(other.getName()));
    }
}
