package guessmarket.engine.service;

import guessmarket.engine.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Order-book matching, per appendix B.
 *
 * Order of operations when a new order arrives:
 *   1. Match it against the opposite side of the SAME option's book
 *      (best price first). Executions happen at the RESTING order's price,
 *      which is the standard price-time-priority convention.
 *   2. If the event allows mint and the new order is a BUY, try to mint
 *      against a BUY on the opposite option whose prices together reach d.
 *   3. Whatever is left rests in the book.
 */
public class OrderBookMatcher {

    private static final double EPS = 1e-9;

    private final OrderBookEvent event;
    private final Map<String, User> users;

    public OrderBookMatcher(OrderBookEvent event, Map<String, User> users) {
        this.event = event;
        this.users = users;
    }

    public void submit(Order incoming) {
        if (incoming.getSide() == OrderSide.BUY) {
            matchBuy(incoming);
            if (!incoming.isFilled() && event.isAllowMint()) tryMint(incoming);
            if (!incoming.isFilled()) event.getBook(incoming.getOptionName()).addBid(incoming);
        } else {
            matchSell(incoming);
            if (!incoming.isFilled()) event.getBook(incoming.getOptionName()).addAsk(incoming);
        }
        for (OrderBook b : event.getBooks().values()) b.purgeFilled();
    }

    private void matchBuy(Order buy) {
        OrderBook book = event.getBook(buy.getOptionName());
        List<Order> asks = new ArrayList<>(book.getAsks());
        for (Order ask : asks) {
            if (buy.isFilled()) break;
            if (ask.getPrice() > buy.getPrice() + EPS) break; // no more crossable asks
            double qty = Math.min(buy.getRemainingQuantity(), ask.getRemainingQuantity());
            if (qty <= EPS) continue;
            execute(buy.getUserName(), ask.getUserName(), buy.getOptionName(), qty, ask.getPrice());
            buy.reduce(qty);
            ask.reduce(qty);
            book.setLastTradePrice(ask.getPrice());
        }
    }

    private void matchSell(Order sell) {
        OrderBook book = event.getBook(sell.getOptionName());
        List<Order> bids = new ArrayList<>(book.getBids());
        for (Order bid : bids) {
            if (sell.isFilled()) break;
            if (bid.getPrice() < sell.getPrice() - EPS) break;
            double qty = Math.min(sell.getRemainingQuantity(), bid.getRemainingQuantity());
            if (qty <= EPS) continue;
            execute(bid.getUserName(), sell.getUserName(), sell.getOptionName(), qty, bid.getPrice());
            sell.reduce(qty);
            bid.reduce(qty);
            book.setLastTradePrice(bid.getPrice());
        }
    }

    /** A normal transfer of shares from seller to buyer. */
    private void execute(String buyerName, String sellerName, String optionName,
                         double qty, double price) {
        User buyer = users.get(buyerName), seller = users.get(sellerName);
        double value = qty * price;

        buyer.applyChange(-value, "Bought " + fmt(qty) + " " + optionName
                + " @ " + fmt(price) + " (" + event.getName() + ")");
        buyer.addShares(event.getName(), optionName, qty);
        buyer.addPaidForOption(event.getName(), optionName, value);
        buyer.addNetForEvent(event.getName(), -value);

        seller.applyChange(+value, "Sold " + fmt(qty) + " " + optionName
                + " @ " + fmt(price) + " (" + event.getName() + ")");
        seller.addShares(event.getName(), optionName, -qty);
        seller.addNetForEvent(event.getName(), +value);

        chargePurchaseCommission(buyer, value);
        event.logTrade(new OrderBookTrade(optionName, buyerName, sellerName, qty, price, false));
    }

    /**
     * Mint: two buyers on OPPOSITE options whose prices together reach d.
     * New shares are created; both payments go into the event account.
     * If the prices sum to MORE than d, the resting order pays its full price
     * and the incoming order pays only the complement (d - restingPrice).
     */
    private void tryMint(Order incoming) {
        String otherName = event.otherOption(incoming.getOptionName());
        OrderBook otherBook = event.getBook(otherName);
        List<Order> otherBids = new ArrayList<>(otherBook.getBids());

        for (Order resting : otherBids) {
            if (incoming.isFilled()) break;
            double sum = incoming.getPrice() + resting.getPrice();
            if (sum < event.getD() - EPS) break; // bids are sorted desc; no better pair ahead

            double qty = Math.min(incoming.getRemainingQuantity(), resting.getRemainingQuantity());
            if (qty <= EPS) continue;

            double restingPays = resting.getPrice();
            double incomingPays = event.getD() - restingPays; // complement, per spec
            if (incomingPays < 0) incomingPays = 0;

            User uIncoming = users.get(incoming.getUserName());
            User uResting = users.get(resting.getUserName());

            uIncoming.applyChange(-qty * incomingPays, "Mint " + fmt(qty) + " "
                    + incoming.getOptionName() + " @ " + fmt(incomingPays) + " (" + event.getName() + ")");
            uIncoming.addShares(event.getName(), incoming.getOptionName(), qty);
            uIncoming.addPaidForOption(event.getName(), incoming.getOptionName(), qty * incomingPays);
            uIncoming.addNetForEvent(event.getName(), -qty * incomingPays);

            uResting.applyChange(-qty * restingPays, "Mint " + fmt(qty) + " "
                    + resting.getOptionName() + " @ " + fmt(restingPays) + " (" + event.getName() + ")");
            uResting.addShares(event.getName(), resting.getOptionName(), qty);
            uResting.addPaidForOption(event.getName(), resting.getOptionName(), qty * restingPays);
            uResting.addNetForEvent(event.getName(), -qty * restingPays);

            event.addToAccount(qty * (incomingPays + restingPays));

            chargePurchaseCommission(uIncoming, qty * incomingPays);
            chargePurchaseCommission(uResting, qty * restingPays);

            incoming.reduce(qty);
            resting.reduce(qty);

            event.getBook(incoming.getOptionName()).setLastTradePrice(incomingPays);
            otherBook.setLastTradePrice(restingPays);

            event.logTrade(new OrderBookTrade(incoming.getOptionName(),
                    incoming.getUserName(), "(mint)", qty, incomingPays, true));
            event.logTrade(new OrderBookTrade(resting.getOptionName(),
                    resting.getUserName(), "(mint)", qty, restingPays, true));
        }
    }

    /** on-purchase commission: the buyer pays a % of the trade value to the MM. */
    private void chargePurchaseCommission(User buyer, double value) {
        if (event.getCommissionType() != CommissionType.ON_PURCHASE) return;
        if (event.getCommissionPercent() <= 0) return;
        double fee = value * event.getCommissionPercent() / 100.0;
        if (fee <= 0) return;
        buyer.applyChange(-fee, "Commission on purchase (" + event.getName() + ")");
        buyer.addCommissionPaid(event.getName(), fee);
        buyer.addNetForEvent(event.getName(), -fee);
        User mm = users.get(event.getMarketMakerName());
        if (mm != null) mm.applyChange(+fee, "Commission received (" + event.getName() + ")");
        event.addCommission(fee);
    }

    private String fmt(double d) { return String.format("%.2f", d); }
}
