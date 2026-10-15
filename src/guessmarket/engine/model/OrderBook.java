package guessmarket.engine.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The resting orders for ONE option of an order-book event.
 * Bids are kept best-first (highest price); asks best-first (lowest price).
 */
public class OrderBook {

    private final String optionName;
    private final List<Order> bids = new ArrayList<>();
    private final List<Order> asks = new ArrayList<>();
    private Double lastTradePrice = null;

    public OrderBook(String optionName) { this.optionName = optionName; }

    public String getOptionName() { return optionName; }
    public List<Order> getBids() { return bids; }
    public List<Order> getAsks() { return asks; }

    public void addBid(Order o) {
        bids.add(o);
        bids.sort(Comparator.comparingDouble(Order::getPrice).reversed()
                .thenComparingLong(Order::getId));
    }

    public void addAsk(Order o) {
        asks.add(o);
        asks.sort(Comparator.comparingDouble(Order::getPrice)
                .thenComparingLong(Order::getId));
    }

    public void purgeFilled() {
        bids.removeIf(Order::isFilled);
        asks.removeIf(Order::isFilled);
    }

    public Double getBestBid() { return bids.isEmpty() ? null : bids.get(0).getPrice(); }
    public Double getBestAsk() { return asks.isEmpty() ? null : asks.get(0).getPrice(); }
    public Double getLastTradePrice() { return lastTradePrice; }
    public void setLastTradePrice(double p) { this.lastTradePrice = p; }

    public Double getMid() {
        Double bb = getBestBid(), ba = getBestAsk();
        if (bb == null || ba == null) return null;
        return (bb + ba) / 2.0;
    }

    public Double getSpread() {
        Double bb = getBestBid(), ba = getBestAsk();
        if (bb == null || ba == null) return null;
        return ba - bb;
    }
}
