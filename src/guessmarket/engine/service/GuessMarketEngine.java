package guessmarket.engine.service;

import guessmarket.engine.model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Passive engine. No I/O, no awareness of who is calling it.
 *
 * For Exercise 3 this single instance is shared by every HTTP request on the
 * server, so every public method that reads or changes state is synchronized.
 * A version counter is bumped on every change; clients poll it so they only
 * refresh when something has actually happened.
 *
 * Commission routing note (changed from Ex1): in Ex2 the MM "receives the
 * commissions from the event" (spec, MM responsibilities), so commissions are
 * credited to the MM's user account rather than being parked in the event account.
 */
public class GuessMarketEngine {

    private Map<String, User> users = new LinkedHashMap<>();
    private Map<String, GMEvent> events = new LinkedHashMap<>();
    private boolean fileLoaded = false;
    private int nextEventId = 1;
    private long version = 0;

    /** Bumped on every change so polling clients know when to refresh. */
    public synchronized long getVersion() { return version; }
    private void changed() { version++; }

    // ---------------- Exercise 3: users register themselves ----------------

    /** Registers a new client. Names are unique, compared case-insensitively. */
    public synchronized User registerUser(String name) {
        if (name == null || name.trim().isEmpty())
            throw new GuessMarketException("User name cannot be empty.");
        String clean = name.trim();
        for (String existing : users.keySet())
            if (existing.equalsIgnoreCase(clean))
                throw new GuessMarketException(
                        "The user name '" + clean + "' is already taken. Please choose another one.");
        User u = new User(clean, 0);
        users.put(clean, u);
        changed();
        return u;
    }

    public synchronized boolean userExists(String name) {
        if (name == null) return false;
        for (String existing : users.keySet())
            if (existing.equalsIgnoreCase(name.trim())) return true;
        return false;
    }

    /** Adds money to a user's own account. Exercise 3 allows self top-up. */
    public synchronized void deposit(String userName, double amount) {
        User u = getUser(userName);
        if (amount <= 0) throw new GuessMarketException("Deposit amount must be positive.");
        u.applyChange(+amount, "Deposit");
        changed();
    }

    /** Event names already in the system, lower-cased, for upload validation. */
    public synchronized Set<String> getEventNamesLower() {
        Set<String> out = new HashSet<>();
        for (String n : events.keySet()) out.add(n.toLowerCase());
        return out;
    }

    /**
     * Adds the events of a freshly uploaded file. Files accumulate - they never
     * replace what is already loaded - and the uploader becomes the market maker
     * of every event in the file.
     */
    public synchronized void addEvents(List<GMEvent> newEvents, String uploaderName) {
        User mm = getUser(uploaderName);
        for (GMEvent e : newEvents) {
            e.setId(nextEventId++);
            e.setMarketMakerName(mm.getName());
            mm.addMmEvent(e.getName());
            events.put(e.getName(), e);
        }
        fileLoaded = true;
        changed();
    }

    public synchronized void replaceContents(List<User> newUsers, List<GMEvent> newEvents) {
        Map<String, User> u = new LinkedHashMap<>();
        for (User x : newUsers) u.put(x.getName(), x);
        Map<String, GMEvent> e = new LinkedHashMap<>();
        for (GMEvent x : newEvents) e.put(x.getName(), x);
        this.users = u;
        this.events = e;
        this.fileLoaded = true;
    }

    public synchronized boolean isFileLoaded() { return fileLoaded; }

    public synchronized List<User> getAllUsers() { return new ArrayList<>(users.values()); }
    public synchronized User getUser(String name) {
        User u = users.get(name);
        if (u == null) throw new GuessMarketException("Unknown user: " + name);
        return u;
    }
    public synchronized Map<String, User> getUserMap() { return users; }

    public synchronized List<GMEvent> getAllEvents() {
        List<GMEvent> l = new ArrayList<>(events.values());
        l.sort(Comparator.comparingInt(GMEvent::getId));
        return l;
    }
    public synchronized GMEvent getEvent(String name) {
        GMEvent e = events.get(name);
        if (e == null) throw new GuessMarketException("Unknown event: " + name);
        return e;
    }

    private void requireActive(User u) {
        if (u.isBlocked())
            throw new GuessMarketException("User " + u.getName()
                    + " is blocked (negative balance) and cannot act.");
    }

    // ---------------- event lifecycle ----------------

    public synchronized void openEvent(String eventName, String actingUser) {
        GMEvent e = getEvent(eventName);
        User u = getUser(actingUser);
        requireActive(u);
        if (!u.getName().equals(e.getMarketMakerName()))
            throw new GuessMarketException("Only the market maker can open this event.");
        if (e.getStatus() != EventStatus.NOT_STARTED)
            throw new GuessMarketException("Event is not in a state that can be opened.");

        double cost = e.openingCost();
        if (!u.canAfford(cost))
            throw new GuessMarketException(String.format(
                    "Market maker cannot afford the opening cost of %.2f (balance %.2f).",
                    cost, u.getBalance()));

        u.applyChange(-cost, "Opened event " + e.getName());
        u.addNetForEvent(e.getName(), -cost);
        e.addToAccount(cost);
        e.setStatus(EventStatus.ACTIVE);
        changed();

        if (e instanceof OrderBookEvent) {
            OrderBookEvent ob = (OrderBookEvent) e;
            double pairs = ob.initialPairs();
            for (GMOption o : ob.getOptions()) u.addShares(ob.getName(), o.getName(), pairs);
        }
    }

    public synchronized void closeEvent(String eventName, String actingUser, String winningOption) {
        GMEvent e = getEvent(eventName);
        User mm = getUser(actingUser);
        if (!mm.getName().equals(e.getMarketMakerName()))
            throw new GuessMarketException("Only the market maker can close this event.");
        if (e.getStatus() != EventStatus.ACTIVE)
            throw new GuessMarketException("Only an active event can be closed.");
        if (e.getOption(winningOption) == null)
            throw new GuessMarketException("Unknown option: " + winningOption);

        e.setWinningOption(winningOption);
        double payoutPerShare = (e instanceof OrderBookEvent)
                ? ((OrderBookEvent) e).getD() : 1.0;

        for (User u : users.values()) {
            double shares = u.getShares(e.getName(), winningOption);
            if (shares <= 0) continue;
            double payout = shares * payoutPerShare;

            double fee = 0.0;
            if (e.getCommissionType() == CommissionType.ON_CLOSE && e.getCommissionPercent() > 0) {
                fee = payout * e.getCommissionPercent() / 100.0;
            }
            u.applyChange(payout - fee, "Winnings for " + e.getName()
                    + (fee > 0 ? " (after commission)" : ""));
            u.addNetForEvent(e.getName(), payout - fee);
            if (fee > 0) u.addCommissionPaid(e.getName(), fee);
            e.addToAccount(-payout);
            if (fee > 0) {
                mm.applyChange(+fee, "Closing commission (" + e.getName() + ")");
                e.addCommission(fee);
            }
        }

        // Anything left in the event account goes back to the MM.
        double leftover = e.getAccount();
        if (Math.abs(leftover) > 1e-9) {
            mm.applyChange(+leftover, "Residual event funds returned (" + e.getName() + ")");
            mm.addNetForEvent(e.getName(), +leftover);
            e.setAccount(0);
        }
        e.setStatus(EventStatus.CLOSED);
        changed();
    }

    // ---------------- LMSR trading ----------------

    public synchronized double lmsrCost(String eventName, String optionName, double quantity) {
        LmsrEvent e = asLmsr(eventName);
        String other = otherOptionName(e, optionName);
        double qThis = e.getQuantity(optionName), qOther = e.getQuantity(other);
        return LmsrCalculator.cost(e.getB(), qThis + quantity, qOther)
                - LmsrCalculator.cost(e.getB(), qThis, qOther);
    }

    public synchronized void lmsrBuy(String eventName, String userName, String optionName, double quantity) {
        LmsrEvent e = asLmsr(eventName);
        User u = getUser(userName);
        requireActive(u);
        if (e.getStatus() != EventStatus.ACTIVE)
            throw new GuessMarketException("This event is not open for trading.");
        if (quantity <= 0) throw new GuessMarketException("Quantity must be positive.");
        if (e.getOption(optionName) == null)
            throw new GuessMarketException("Unknown option: " + optionName);

        double cost = lmsrCost(eventName, optionName, quantity);
        double fee = 0.0;
        if (e.getCommissionType() == CommissionType.ON_PURCHASE && e.getCommissionPercent() > 0)
            fee = cost * e.getCommissionPercent() / 100.0;

        u.applyChange(-(cost + fee), "Bought " + String.format("%.2f", quantity) + " "
                + optionName + " (" + e.getName() + ")");
        u.addShares(e.getName(), optionName, quantity);
        u.addPaidForOption(e.getName(), optionName, cost);
        u.addCommissionPaid(e.getName(), fee);
        u.addNetForEvent(e.getName(), -(cost + fee));

        e.setQuantity(optionName, e.getQuantity(optionName) + quantity);
        e.getOption(optionName).addShares(quantity);
        e.addToAccount(cost);

        if (fee > 0) {
            User mm = users.get(e.getMarketMakerName());
            if (mm != null) mm.applyChange(+fee, "Commission received (" + e.getName() + ")");
            e.addCommission(fee);
        }
        e.addTrade(new Trade(userName, optionName, quantity, cost, fee));
        changed();
    }

    // ---------------- Order Book trading ----------------

    public synchronized void placeOrder(String eventName, String userName, OrderSide side,
                           String optionName, double price, double quantity) {
        OrderBookEvent e = asOrderBook(eventName);
        User u = getUser(userName);
        requireActive(u);
        if (e.getStatus() != EventStatus.ACTIVE)
            throw new GuessMarketException("This event is not open for trading.");
        if (quantity <= 0) throw new GuessMarketException("Quantity must be positive.");
        if (e.getOption(optionName) == null)
            throw new GuessMarketException("Unknown option: " + optionName);

        double maxPrice = e.getD() - 0.01;
        if (price <= 0) throw new GuessMarketException("Price must be positive.");
        if (price > maxPrice + 1e-9)
            throw new GuessMarketException(String.format(
                    "Price cannot exceed %.2f (base value %d minus 0.01).", maxPrice, e.getD()));

        if (side == OrderSide.SELL) {
            double held = u.getShares(e.getName(), optionName);
            // Shares already promised to other resting sell orders are not available
            // again - without this a user could oversell the same shares twice.
            double committed = 0;
            for (Order o : e.getBook(optionName).getAsks())
                if (o.getUserName().equals(userName)) committed += o.getRemainingQuantity();
            double available = held - committed;
            if (available < quantity - 1e-9)
                throw new GuessMarketException(String.format(
                        "Not enough shares to sell: holding %.2f, already offered %.2f, "
                        + "available %.2f, tried to sell %.2f.", held, committed, available, quantity));
        }

        u.ensureParticipation(e.getName());
        new OrderBookMatcher(e, users).submit(new Order(userName, side, optionName, price, quantity));
        changed();
    }

    // ---------------- helpers ----------------

    private LmsrEvent asLmsr(String name) {
        GMEvent e = getEvent(name);
        if (!(e instanceof LmsrEvent)) throw new GuessMarketException("Not an LMSR event: " + name);
        return (LmsrEvent) e;
    }

    private OrderBookEvent asOrderBook(String name) {
        GMEvent e = getEvent(name);
        if (!(e instanceof OrderBookEvent)) throw new GuessMarketException("Not an order-book event: " + name);
        return (OrderBookEvent) e;
    }

    private String otherOptionName(GMEvent e, String optionName) {
        for (GMOption o : e.getOptions())
            if (!o.getName().equalsIgnoreCase(optionName)) return o.getName();
        return optionName;
    }
}
