package guessmarket.engine.service;

import guessmarket.engine.model.CommissionType;
import guessmarket.engine.model.GMEvent;
import guessmarket.engine.model.GMOption;
import guessmarket.engine.model.Trade;
import guessmarket.engine.xml.EventFileValidationResult;
import guessmarket.engine.xml.GMEventFileLoader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The "passive" engine module: it only reacts to calls, never prints
 * anything and never knows who is calling it (per the assignment's
 * module-separation requirement). All console I/O lives in the ui module.
 */
public class GuessMarketEngine {

    private final GMEventFileLoader loader = new GMEventFileLoader();
    private Map<Integer, GMEvent> events = new LinkedHashMap<>();
    private boolean fileLoaded = false;

    public EventFileValidationResult loadFile(String path) {
        EventFileValidationResult result = loader.load(path);
        if (result.isValid()) {
            Map<Integer, GMEvent> newEvents = new LinkedHashMap<>();
            for (GMEvent e : result.getEvents()) {
                // Initial LMSR subsidy: C(0,0) = b * ln(2). This funds the market
                // before any trading happens (see appendix A of the spec).
                double subsidy = LmsrCalculator.cost(e.getB(), 0, 0);
                e.setAccount(subsidy);
                newEvents.put(e.getId(), e);
            }
            this.events = newEvents; // a valid load fully replaces the previous state
            this.fileLoaded = true;
        }
        return result;
    }

    public boolean isFileLoaded() {
        return fileLoaded;
    }

    public List<GMEvent> getAllEvents() {
        List<GMEvent> list = new ArrayList<>(events.values());
        list.sort(Comparator.comparingInt(GMEvent::getId));
        return list;
    }

    public List<GMEvent> getActiveEvents() {
        List<GMEvent> list = new ArrayList<>();
        for (GMEvent e : getAllEvents()) {
            if (!e.isClosed()) list.add(e);
        }
        return list;
    }

    public GMEvent getEventById(int id) {
        GMEvent e = events.get(id);
        if (e == null) throw new GuessMarketException("Event not found.");
        return e;
    }

    /** Current LMSR price (0..1) of the given option within its event. */
    public double getPrice(int eventId, String optionName) {
        GMEvent event = getEventById(eventId);
        GMOption chosen = event.getOption(optionName);
        if (chosen == null) throw new GuessMarketException("Unknown option: " + optionName);
        GMOption other = otherOption(event, chosen);
        double q = event.getQuantity(chosen.getName());
        double qOther = event.getQuantity(other.getName());
        return LmsrCalculator.price(event.getB(), q, qOther);
    }

    public PurchaseResult purchase(int eventId, int optionIndex, double quantity) {
        GMEvent event = getEventById(eventId);
        if (event.isClosed()) {
            throw new GuessMarketException("This event is closed and can no longer be traded.");
        }
        if (optionIndex < 1 || optionIndex > event.getOptions().size()) {
            throw new GuessMarketException("Invalid option selection.");
        }
        if (quantity <= 0) {
            throw new GuessMarketException("Quantity must be a positive number.");
        }

        GMOption chosen = event.getOptions().get(optionIndex - 1);
        GMOption other = otherOption(event, chosen);

        double qChosenBefore = event.getQuantity(chosen.getName());
        double qOtherValue = event.getQuantity(other.getName());
        double costBefore = LmsrCalculator.cost(event.getB(), qChosenBefore, qOtherValue);
        double qChosenAfter = qChosenBefore + quantity;
        double costAfter = LmsrCalculator.cost(event.getB(), qChosenAfter, qOtherValue);
        double sharesCost = costAfter - costBefore;

        double commission = 0.0;
        if (event.getCommissionType() == CommissionType.ON_PURCHASE) {
            commission = sharesCost * event.getCommissionPercent() / 100.0;
        }
        double totalPaid = sharesCost + commission;

        event.setQuantity(chosen.getName(), qChosenAfter);
        chosen.addShares(quantity);
        event.addToAccount(sharesCost);
        if (commission > 0) {
            event.addToAccount(commission);
            event.addCommission(commission);
        }
        event.addTrade(new Trade(chosen.getName(), quantity, sharesCost));

        return new PurchaseResult(chosen.getName(), quantity, sharesCost, commission, totalPaid);
    }

    public void closeEvent(int eventId, int winningOptionIndex) {
        GMEvent event = getEventById(eventId);
        if (event.isClosed()) {
            throw new GuessMarketException("This event is already closed.");
        }
        if (winningOptionIndex < 1 || winningOptionIndex > event.getOptions().size()) {
            throw new GuessMarketException("Invalid option selection.");
        }

        String winningName = event.getOptions().get(winningOptionIndex - 1).getName();

        double totalInvestedInWinner = 0.0;
        for (Trade t : event.getHistory()) {
            if (t.getOptionName().equalsIgnoreCase(winningName)) {
                totalInvestedInWinner += t.getPricePaid();
            }
        }

        if (event.getCommissionType() == CommissionType.ON_CLOSE) {
            double commission = totalInvestedInWinner * event.getCommissionPercent() / 100.0;
            event.addCommission(commission);
            // The remainder (totalInvestedInWinner - commission) is what would be
            // distributed pro-rata to winning-share holders. Ex1 has a single user
            // and its console UI never needs to display per-user payouts, so we do
            // not track it further here (kept minimal on purpose, per module scope).
        }

        event.close(winningName);
    }

    private GMOption otherOption(GMEvent event, GMOption opt) {
        for (GMOption o : event.getOptions()) {
            if (o != opt) return o;
        }
        return opt;
    }
}
