package guessmarket.ui;

import guessmarket.engine.model.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/** Reusable read-only panels shared by both tabs. */
public final class DetailPanels {

    private DetailPanels() {}

    private static Label header(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    /** LMSR: current prices + full trade history (newest first), per Ex1 command 3. */
    public static Pane lmsrPanel(LmsrEvent e) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8, 0, 0, 0));
        box.getChildren().add(header("Market state"));

        GridPane g = new GridPane();
        g.setHgap(16); g.setVgap(4);
        g.add(header("Option"), 0, 0);
        g.add(header("Value"), 1, 0);
        g.add(header("Shares bought"), 2, 0);
        int r = 1;
        for (GMOption o : e.getOptions()) {
            g.add(new Label(o.getName()), 0, r);
            g.add(new Label(Fmt.price(e.priceOf(o.getName()))), 1, r);
            g.add(new Label(Fmt.qty(o.getTotalSharesBought())), 2, r);
            r++;
        }
        box.getChildren().add(g);
        box.getChildren().add(new Label("b = " + e.getB()));

        box.getChildren().add(header("Trade history (newest first)"));
        TableView<Trade> t = new TableView<>();
        TableColumn<Trade, String> c1 = new TableColumn<>("User");
        c1.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUserName()));
        TableColumn<Trade, String> c2 = new TableColumn<>("Option");
        c2.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getOptionName()));
        TableColumn<Trade, String> c3 = new TableColumn<>("Quantity");
        c3.setCellValueFactory(c -> new SimpleStringProperty(Fmt.qty(c.getValue().getQuantity())));
        TableColumn<Trade, String> c4 = new TableColumn<>("Price paid");
        c4.setCellValueFactory(c -> new SimpleStringProperty(Fmt.money(c.getValue().getPricePaid())));
        TableColumn<Trade, String> c5 = new TableColumn<>("Commission");
        c5.setCellValueFactory(c -> new SimpleStringProperty(Fmt.money(c.getValue().getCommissionPaid())));
        t.getColumns().addAll(List.of(c1, c2, c3, c4, c5));
        List<Trade> rev = new java.util.ArrayList<>(e.getHistory());
        java.util.Collections.reverse(rev);
        t.setItems(FXCollections.observableArrayList(rev));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPrefHeight(170);
        t.setPlaceholder(new Label("No trades yet."));
        box.getChildren().add(t);

        if (e.getStatus() == EventStatus.CLOSED) {
            VBox fin = new VBox(2, header("Final shares per option"));
            for (GMOption o : e.getOptions()) {
                String mark = o.getName().equalsIgnoreCase(e.getWinningOption()) ? "   <-- WINNER" : "";
                fin.getChildren().add(new Label(o.getName() + ": " + Fmt.qty(o.getTotalSharesBought()) + mark));
            }
            box.getChildren().add(fin);
        }
        return box;
    }

    /** Order book: one book per option side by side, each with its stats. */
    public static Pane orderBookPanel(OrderBookEvent e) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8, 0, 0, 0));
        box.getChildren().add(new Label("Base value d = " + e.getD()
                + "   |   initial = " + e.getInitial()
                + "   |   mint allowed: " + e.isAllowMint()));

        HBox books = new HBox(10);
        for (GMOption o : e.getOptions()) {
            books.getChildren().add(singleBook(e, o.getName()));
        }
        for (javafx.scene.Node n : books.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        box.getChildren().add(books);
        return box;
    }

    private static Pane singleBook(OrderBookEvent e, String optionName) {
        OrderBook b = e.getBook(optionName);
        VBox v = new VBox(6);
        v.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 4; -fx-padding: 8;");
        Label t = new Label(optionName + " - order book");
        t.setStyle("-fx-font-weight: bold;");
        t.setWrapText(true);
        v.getChildren().add(t);

        v.getChildren().add(new Label(
                "LAST " + Fmt.price(b.getLastTradePrice())
              + "   BID " + Fmt.price(b.getBestBid())
              + "   ASK " + Fmt.price(b.getBestAsk())
              + "\nMID " + Fmt.price(b.getMid())
              + "   SPREAD " + Fmt.price(b.getSpread())));

        v.getChildren().add(new Label("Buy orders (bids)"));
        v.getChildren().add(orderTable(b.getBids()));
        v.getChildren().add(new Label("Sell orders (asks)"));
        v.getChildren().add(orderTable(b.getAsks()));
        return v;
    }

    private static TableView<Order> orderTable(List<Order> orders) {
        TableView<Order> t = new TableView<>();
        TableColumn<Order, String> c1 = new TableColumn<>("User");
        c1.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUserName()));
        TableColumn<Order, String> c2 = new TableColumn<>("Qty");
        c2.setCellValueFactory(c -> new SimpleStringProperty(Fmt.qty(c.getValue().getRemainingQuantity())));
        TableColumn<Order, String> c3 = new TableColumn<>("Price");
        c3.setCellValueFactory(c -> new SimpleStringProperty(Fmt.money(c.getValue().getPrice())));
        t.getColumns().addAll(List.of(c1, c2, c3));
        t.setItems(FXCollections.observableArrayList(orders));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPrefHeight(120);
        t.setPlaceholder(new Label("empty"));
        return t;
    }

    /** Who is in this event and what they hold. */
    public static Pane participantsPanel(GMEvent e, List<User> users) {
        VBox v = new VBox(6);
        v.setPadding(new Insets(8, 0, 0, 0));
        v.getChildren().add(header("Participants"));

        GridPane g = new GridPane();
        g.setHgap(14); g.setVgap(3);
        g.add(header("User"), 0, 0);
        int col = 1;
        for (GMOption o : e.getOptions()) { g.add(header(o.getName()), col++, 0); }
        g.add(header("Value"), col, 0);

        int r = 1;
        for (User u : users) {
            boolean participates = false;
            for (GMOption o : e.getOptions())
                if (u.getShares(e.getName(), o.getName()) != 0) participates = true;
            if (u.getHoldings().containsKey(e.getName())) participates = true;
            if (!participates) continue;

            g.add(new Label(u.getName()), 0, r);
            int c = 1;
            double value = 0;
            for (GMOption o : e.getOptions()) {
                double sh = u.getShares(e.getName(), o.getName());
                g.add(new Label(Fmt.qty(sh)), c++, r);
                if (e instanceof OrderBookEvent) {
                    OrderBook bk = ((OrderBookEvent) e).getBook(o.getName());
                    Double mid = bk.getMid() != null ? bk.getMid() : bk.getLastTradePrice();
                    if (mid != null) value += sh * mid;
                } else {
                    value += sh * ((LmsrEvent) e).priceOf(o.getName());
                }
            }
            g.add(new Label(Fmt.money(value)), c, r);
            r++;
        }
        if (r == 1) v.getChildren().add(new Label("No participants yet."));
        else v.getChildren().add(g);
        return v;
    }
}
