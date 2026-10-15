package guessmarket.engine.dto;

import guessmarket.engine.model.*;
import guessmarket.engine.service.GuessMarketEngine;

import java.util.List;

/**
 * Converts the engine's live objects into the flat DTOs that go over the wire.
 *
 * Kept separate from the model so the model stays free of any knowledge about
 * how it is transported, and separate from the servlets so the same mapping
 * serves every endpoint.
 */
public final class StateMapper {

    private StateMapper() {}

    public static Dtos.StateDto buildState(GuessMarketEngine engine, String forUser) {
        Dtos.StateDto s = new Dtos.StateDto();
        s.version = engine.getVersion();

        for (GMEvent e : engine.getAllEvents()) s.events.add(mapEvent(e, engine));

        for (User u : engine.getAllUsers()) {
            Dtos.UserSummaryDto d = new Dtos.UserSummaryDto();
            d.name = u.getName();
            d.balance = round(u.getBalance());
            d.marketMaker = u.isMarketMaker();
            d.blocked = u.isBlocked();
            s.users.add(d);
        }

        if (forUser != null && engine.userExists(forUser)) {
            s.me = mapMe(engine.getUser(forUser), engine);
        }
        return s;
    }

    private static Dtos.EventDto mapEvent(GMEvent e, GuessMarketEngine engine) {
        Dtos.EventDto d = new Dtos.EventDto();
        d.name = e.getName();
        d.description = e.getDescription();
        d.status = e.getStatus().displayName();
        d.kind = e.getKind().displayName();
        d.commissionPercent = e.getCommissionPercent();
        d.commissionType = e.getCommissionType().displayName();
        d.account = round(e.getAccount());
        d.commissionCollected = round(e.getTotalCommissionCollected());
        d.marketMaker = e.getMarketMakerName();
        d.winningOption = e.getWinningOption();

        for (GMOption o : e.getOptions()) {
            Dtos.OptionDto od = new Dtos.OptionDto();
            od.name = o.getName();
            od.totalSharesBought = round(o.getTotalSharesBought());
            od.value = round(valueOf(e, o.getName()));
            d.options.add(od);
        }

        if (e instanceof LmsrEvent) {
            LmsrEvent le = (LmsrEvent) e;
            d.b = le.getB();
            List<Trade> h = le.getHistory();
            for (int i = h.size() - 1; i >= 0; i--) {   // newest first
                Trade t = h.get(i);
                Dtos.TradeDto td = new Dtos.TradeDto();
                td.user = t.getUserName();
                td.option = t.getOptionName();
                td.quantity = round(t.getQuantity());
                td.pricePaid = round(t.getPricePaid());
                td.commissionPaid = round(t.getCommissionPaid());
                d.history.add(td);
            }
        } else {
            OrderBookEvent ob = (OrderBookEvent) e;
            d.d = ob.getD();
            d.initial = ob.getInitial();
            d.allowMint = ob.isAllowMint();
            for (GMOption o : ob.getOptions()) {
                OrderBook b = ob.getBook(o.getName());
                Dtos.BookDto bd = new Dtos.BookDto();
                bd.option = o.getName();
                for (Order x : b.getBids()) bd.bids.add(mapOrder(x));
                for (Order x : b.getAsks()) bd.asks.add(mapOrder(x));
                bd.last = round(b.getLastTradePrice());
                bd.bid = round(b.getBestBid());
                bd.ask = round(b.getBestAsk());
                bd.mid = round(b.getMid());
                bd.spread = round(b.getSpread());
                d.books.add(bd);
            }
        }

        for (User u : engine.getAllUsers()) {
            if (!u.getHoldings().containsKey(e.getName())) continue;
            Dtos.ParticipantDto p = new Dtos.ParticipantDto();
            p.user = u.getName();
            double value = 0;
            for (GMOption o : e.getOptions()) {
                double sh = u.getShares(e.getName(), o.getName());
                p.sharesPerOption.add(round(sh));
                value += sh * valueOf(e, o.getName());
            }
            p.holdingsValue = round(value);
            d.participants.add(p);
        }
        return d;
    }

    private static Dtos.OrderDto mapOrder(Order o) {
        Dtos.OrderDto d = new Dtos.OrderDto();
        d.user = o.getUserName();
        d.quantity = round(o.getRemainingQuantity());
        d.price = round(o.getPrice());
        return d;
    }

    /** An option's current worth: the LMSR price, or the book's mid (falling back to last). */
    private static double valueOf(GMEvent e, String optionName) {
        if (e instanceof LmsrEvent) return ((LmsrEvent) e).priceOf(optionName);
        OrderBook b = ((OrderBookEvent) e).getBook(optionName);
        Double mid = b.getMid();
        if (mid != null) return mid;
        Double last = b.getLastTradePrice();
        return last == null ? 0.0 : last;
    }

    private static Dtos.MyUserDto mapMe(User u, GuessMarketEngine engine) {
        Dtos.MyUserDto d = new Dtos.MyUserDto();
        d.name = u.getName();
        d.balance = round(u.getBalance());
        d.blocked = u.isBlocked();

        for (AccountEntry a : u.getStatement()) {
            Dtos.AccountLineDto l = new Dtos.AccountLineDto();
            l.description = a.getDescription();
            l.amount = round(a.getAmount());
            l.balanceAfter = round(a.getBalanceAfter());
            d.statement.add(l);
        }

        for (GMEvent e : engine.getAllEvents()) {
            boolean isMm = u.getName().equals(e.getMarketMakerName());
            boolean participates = u.getHoldings().containsKey(e.getName());
            if (!isMm && !participates) continue;

            Dtos.InvolvementDto inv = new Dtos.InvolvementDto();
            inv.eventName = e.getName();
            inv.status = e.getStatus().displayName();
            inv.kind = e.getKind().displayName();
            inv.role = isMm ? "Market Maker" : "Participating";
            for (GMOption o : e.getOptions()) {
                inv.optionNames.add(o.getName());
                inv.shares.add(round(u.getShares(e.getName(), o.getName())));
                inv.paid.add(round(u.getPaidForOption(e.getName(), o.getName())));
            }
            inv.commissionPaid = round(u.getCommissionPaid(e.getName()));
            if (e.getStatus() == EventStatus.CLOSED)
                inv.profitLoss = round(u.getNetForEvent(e.getName()));
            d.involvements.add(inv);
        }
        return d;
    }

    /** Everything on the wire is already rounded to 2 decimals, as the spec requires. */
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
    private static Double round(Double v) { return v == null ? null : round(v.doubleValue()); }
}
