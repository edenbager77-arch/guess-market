package guessmarket.engine.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderBookEvent extends GMEvent {

    private final int d;            // base value: a YES+NO pair is always worth d
    private final int initial;      // MM's initial purchase amount
    private final boolean allowMint;
    private final Map<String, OrderBook> books = new LinkedHashMap<>();
    private final List<OrderBookTrade> tradeLog = new ArrayList<>();

    public OrderBookEvent(int id, String name, String description, int commissionPercent,
                          CommissionType commissionType, List<String> optionNames,
                          int d, int initial, boolean allowMint) {
        super(id, name, description, commissionPercent, commissionType, optionNames);
        this.d = d; this.initial = initial; this.allowMint = allowMint;
        for (String o : optionNames) books.put(o, new OrderBook(o));
    }

    @Override public EventKind getKind() { return EventKind.ORDER_BOOK; }

    public int getD() { return d; }
    public int getInitial() { return initial; }
    public boolean isAllowMint() { return allowMint; }
    public OrderBook getBook(String optionName) { return books.get(optionName); }
    public Map<String, OrderBook> getBooks() { return books; }
    public List<OrderBookTrade> getTradeLog() { return tradeLog; }
    public void logTrade(OrderBookTrade t) { tradeLog.add(t); }

    /**
     * MM pays `initial` and receives initial/d PAIRS of shares
     * (spec example: d=1, initial=100 -> 100 YES + 100 NO for $100).
     */
    @Override public double openingCost() { return initial; }
    public double initialPairs() { return d == 0 ? 0 : (double) initial / d; }

    /** The other option's name (binary events only). */
    public String otherOption(String optionName) {
        for (GMOption o : getOptions())
            if (!o.getName().equalsIgnoreCase(optionName)) return o.getName();
        return optionName;
    }
}
