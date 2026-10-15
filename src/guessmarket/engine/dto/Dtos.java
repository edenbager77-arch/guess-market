package guessmarket.engine.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain data carriers shared by the server and the clients.
 *
 * They live in the engine module so both sides compile against exactly the
 * same shapes, and they are converted to and from JSON with Gson. They hold
 * no logic on purpose - they are the wire format and nothing else.
 */
public final class Dtos {

    private Dtos() {}

    /** Every response carries this, so a client can always tell success from failure. */
    public static class ApiResult {
        public boolean ok;
        public String message;
        public List<String> errors = new ArrayList<>();

        public static ApiResult success(String msg) {
            ApiResult r = new ApiResult();
            r.ok = true;
            r.message = msg;
            return r;
        }
        public static ApiResult failure(String msg, List<String> errs) {
            ApiResult r = new ApiResult();
            r.ok = false;
            r.message = msg;
            if (errs != null) r.errors = errs;
            return r;
        }
        public static ApiResult failure(String msg) {
            return failure(msg, new ArrayList<>());
        }
    }

    /** One resting order in a book. */
    public static class OrderDto {
        public String user;
        public double quantity;
        public double price;
    }

    /** One option's order book plus its market statistics. */
    public static class BookDto {
        public String option;
        public List<OrderDto> bids = new ArrayList<>();
        public List<OrderDto> asks = new ArrayList<>();
        public Double last;    // null when no trade has happened yet
        public Double bid;
        public Double ask;
        public Double mid;
        public Double spread;
    }

    /** One executed LMSR purchase, for the event's history table. */
    public static class TradeDto {
        public String user;
        public String option;
        public double quantity;
        public double pricePaid;
        public double commissionPaid;
    }

    /** One option of an event. */
    public static class OptionDto {
        public String name;
        public double totalSharesBought;
        public double value;   // LMSR price, or the order book's mid/last
    }

    /** A participant's position in an event, shown in the event details. */
    public static class ParticipantDto {
        public String user;
        public List<Double> sharesPerOption = new ArrayList<>();
        public double holdingsValue;
    }

    /** Everything a client needs to render one event. */
    public static class EventDto {
        public String name;
        public String description;
        public String status;          // Not Started / Active / Closed
        public String kind;            // LMSR / Order Book
        public int commissionPercent;
        public String commissionType;  // On Close / On Purchase
        public double account;
        public double commissionCollected;
        public String marketMaker;
        public String winningOption;
        public List<OptionDto> options = new ArrayList<>();

        // LMSR only
        public Integer b;
        public List<TradeDto> history = new ArrayList<>();

        // Order book only
        public Integer d;
        public Integer initial;
        public Boolean allowMint;
        public List<BookDto> books = new ArrayList<>();

        public List<ParticipantDto> participants = new ArrayList<>();
    }

    /** What one user may see about another user. */
    public static class UserSummaryDto {
        public String name;
        public double balance;
        public boolean marketMaker;
        public boolean blocked;
    }

    /** One line of a user's own account statement. */
    public static class AccountLineDto {
        public String description;
        public double amount;
        public double balanceAfter;
    }

    /** A user's own involvement in one event. */
    public static class InvolvementDto {
        public String eventName;
        public String status;
        public String kind;
        public String role;            // Market Maker / Participating / ""
        public List<Double> shares = new ArrayList<>();
        public List<Double> paid = new ArrayList<>();
        public List<String> optionNames = new ArrayList<>();
        public double commissionPaid;
        public Double profitLoss;      // only once the event is closed
    }

    /** The private view a client gets of itself. */
    public static class MyUserDto {
        public String name;
        public double balance;
        public boolean blocked;
        public List<AccountLineDto> statement = new ArrayList<>();
        public List<InvolvementDto> involvements = new ArrayList<>();
    }

    /**
     * The whole world, as one client sees it. The client polls for this and
     * re-renders only when the version has moved.
     */
    public static class StateDto {
        public long version;
        public List<EventDto> events = new ArrayList<>();
        public List<UserSummaryDto> users = new ArrayList<>();
        public MyUserDto me;
    }

    /** The cheap poll: just the version number. */
    public static class VersionDto {
        public long version;
        public VersionDto(long v) { this.version = v; }
    }
}
