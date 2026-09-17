package guessmarket.ui;

import guessmarket.engine.model.*;
import guessmarket.engine.service.GuessMarketException;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * Users tab. Left: users table. Right: the selected user's balance, the events
 * they can act on, and the trade panel for the selected event.
 *
 * All participation happens here because every action needs an acting user.
 */
public class UsersTab {

    private final AppModel model;
    private final Runnable onChanged;

    private final BorderPane root = new BorderPane();
    private final TableView<User> usersTable = new TableView<>();
    private final ObservableList<User> userRows = FXCollections.observableArrayList();

    private final Label balanceBadge = new Label("-");
    private final TableView<GMEvent> eventsTable = new TableView<>();
    private final ObservableList<GMEvent> eventRows = FXCollections.observableArrayList();
    private final VBox tradeBox = new VBox(8);

    public UsersTab(AppModel model, Runnable onChanged) {
        this.model = model;
        this.onChanged = onChanged;
        build();
    }

    public Node getNode() { return root; }

    private void build() {
        TableColumn<User, String> c1 = new TableColumn<>("User");
        c1.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        TableColumn<User, String> c2 = new TableColumn<>("Balance");
        c2.setCellValueFactory(c -> new SimpleStringProperty(Fmt.money(c.getValue().getBalance())));
        TableColumn<User, String> c3 = new TableColumn<>("Status");
        c3.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isBlocked() ? "BLOCKED" : (c.getValue().isMarketMaker() ? "Market Maker" : "Trader")));
        usersTable.getColumns().addAll(List.of(c1, c2, c3));
        usersTable.setItems(userRows);
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        usersTable.setPlaceholder(new Label("No users. Load a file to begin."));
        usersTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> onUserSelected(b));

        balanceBadge.setStyle("-fx-background-color: #ef43d1; -fx-text-fill: white; "
                + "-fx-padding: 4 12 4 12; -fx-font-weight: bold;");

        Label rightTitle = new Label("User details");
        rightTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, rightTitle, spacer, balanceBadge);
        head.setAlignment(Pos.CENTER_LEFT);

        TableColumn<GMEvent, String> e1 = new TableColumn<>("Event");
        e1.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        e1.setPrefWidth(170);
        TableColumn<GMEvent, String> e2 = new TableColumn<>("Status");
        e2.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().displayName()));
        TableColumn<GMEvent, String> e3 = new TableColumn<>("Type");
        e3.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKind().displayName()));
        TableColumn<GMEvent, String> e4 = new TableColumn<>("Role");
        e4.setCellValueFactory(c -> {
            User u = usersTable.getSelectionModel().getSelectedItem();
            if (u == null) return new SimpleStringProperty("");
            boolean mm = u.getName().equals(c.getValue().getMarketMakerName());
            boolean part = u.getHoldings().containsKey(c.getValue().getName());
            return new SimpleStringProperty(mm ? "Market Maker" : (part ? "Participating" : ""));
        });
        eventsTable.getColumns().addAll(List.of(e1, e2, e3, e4));
        eventsTable.setItems(eventRows);
        eventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        eventsTable.setPrefHeight(180);
        eventsTable.setPlaceholder(new Label("Select a user."));
        eventsTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> buildTradePanel(b));

        VBox right = new VBox(10, head, new Label("Events (participating / owned)"),
                eventsTable, new Separator(), tradeBox);
        right.setPadding(new Insets(10));

        ScrollPane rightScroll = new ScrollPane(right);
        rightScroll.setFitToWidth(true);

        SplitPane split = new SplitPane();
        BorderPane left = new BorderPane(usersTable);
        left.setPadding(new Insets(6));
        usersTable.setMinWidth(230);
        split.getItems().addAll(left, rightScroll);
        split.setDividerPositions(0.32);
        // Below this width the tables would be crushed into unreadable stubs,
        // so the whole tab scrolls horizontally instead.
        split.setMinWidth(780);
        ScrollPane outer = new ScrollPane(split);
        outer.setFitToWidth(true);
        outer.setFitToHeight(true);
        root.setCenter(outer);
    }

    public void refresh() {
        User selU = usersTable.getSelectionModel().getSelectedItem();
        GMEvent selE = eventsTable.getSelectionModel().getSelectedItem();
        userRows.setAll(model.getUsers());
        usersTable.refresh();
        if (selU != null && userRows.contains(selU)) usersTable.getSelectionModel().select(selU);

        User u = usersTable.getSelectionModel().getSelectedItem();
        if (u != null) {
            balanceBadge.setText("Balance: " + Fmt.money(u.getBalance()) + (u.isBlocked() ? "  (BLOCKED)" : ""));
            eventRows.setAll(model.getEvents());
            eventsTable.refresh();
            if (selE != null && eventRows.contains(selE)) {
                eventsTable.getSelectionModel().select(selE);
                buildTradePanel(selE);
            }
        }
    }

    private void onUserSelected(User u) {
        if (u == null) {
            balanceBadge.setText("-");
            eventRows.clear();
            tradeBox.getChildren().clear();
            return;
        }
        balanceBadge.setText("Balance: " + Fmt.money(u.getBalance()) + (u.isBlocked() ? "  (BLOCKED)" : ""));
        GMEvent keep = eventsTable.getSelectionModel().getSelectedItem();
        eventRows.setAll(model.getEvents());
        eventsTable.refresh();
        // The selection listener does not fire when the same event object stays
        // selected across a user switch, so rebuild the trade panel explicitly -
        // otherwise the new user sees an empty panel.
        if (keep != null && eventRows.contains(keep)) {
            eventsTable.getSelectionModel().select(keep);
            buildTradePanel(keep);
        } else {
            tradeBox.getChildren().clear();
        }
    }

    private void buildTradePanel(GMEvent e) {
        tradeBox.getChildren().clear();
        User u = usersTable.getSelectionModel().getSelectedItem();
        if (u == null || e == null) return;

        Label t = new Label("Event: " + e.getName());
        t.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        t.setWrapText(true);
        tradeBox.getChildren().add(t);

        boolean isMm = u.getName().equals(e.getMarketMakerName());

        if (isMm && e.getStatus() == EventStatus.NOT_STARTED) {
            Button open = new Button("Open event (cost " + Fmt.money(e.openingCost()) + ")");
            open.setOnAction(a -> act(() -> model.getEngine().openEvent(e.getName(), u.getName())));
            tradeBox.getChildren().add(open);
        }
        if (isMm && e.getStatus() == EventStatus.ACTIVE) {
            ComboBox<String> winner = new ComboBox<>();
            for (GMOption o : e.getOptions()) winner.getItems().add(o.getName());
            winner.setPromptText("winning option");
            Button close = new Button("Close event");
            close.setOnAction(a -> {
                if (winner.getValue() == null) { Dialogs.error("Pick a winner", "Choose the winning option first."); return; }
                act(() -> model.getEngine().closeEvent(e.getName(), u.getName(), winner.getValue()));
            });
            tradeBox.getChildren().add(new HBox(8, winner, close));
        }

        if (e.getStatus() == EventStatus.ACTIVE && !u.isBlocked()) {
            if (e instanceof LmsrEvent) tradeBox.getChildren().add(lmsrTrade((LmsrEvent) e, u));
            else tradeBox.getChildren().add(obTrade((OrderBookEvent) e, u));
        }

        // Involvement details required by the spec: per-option holdings AND the
        // amount paid for each option, total commission paid, and - once the
        // event is closed - the profit or loss from taking part in it.
        VBox holdings = new VBox(3);
        holdings.getChildren().add(boldLabel("Your involvement in this event"));
        GridPane hg = new GridPane();
        hg.setHgap(16);
        hg.setVgap(3);
        hg.add(boldLabel("Option"), 0, 0);
        hg.add(boldLabel("Shares held"), 1, 0);
        hg.add(boldLabel("Paid"), 2, 0);
        int hr = 1;
        for (GMOption o : e.getOptions()) {
            hg.add(new Label(o.getName()), 0, hr);
            hg.add(new Label(Fmt.qty(u.getShares(e.getName(), o.getName()))), 1, hr);
            hg.add(new Label(Fmt.money(u.getPaidForOption(e.getName(), o.getName()))), 2, hr);
            hr++;
        }
        holdings.getChildren().add(hg);
        holdings.getChildren().add(new Label("Total commission paid: "
                + Fmt.money(u.getCommissionPaid(e.getName()))));
        if (e.getStatus() == EventStatus.CLOSED) {
            double net = u.getNetForEvent(e.getName());
            Label pl = new Label((net >= 0 ? "Profit from this event: " : "Loss on this event: ")
                    + Fmt.money(net));
            pl.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                    + (net >= 0 ? "#1a7f37" : "#b3261e") + ";");
            holdings.getChildren().add(pl);
        }
        tradeBox.getChildren().add(holdings);

        if (e instanceof LmsrEvent) tradeBox.getChildren().add(DetailPanels.lmsrPanel((LmsrEvent) e));
        else tradeBox.getChildren().add(DetailPanels.orderBookPanel((OrderBookEvent) e));
    }

    private Label boldLabel(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    private Pane lmsrTrade(LmsrEvent e, User u) {
        ComboBox<String> opt = new ComboBox<>();
        for (GMOption o : e.getOptions()) opt.getItems().add(o.getName());
        opt.getSelectionModel().selectFirst();
        TextField qty = new TextField();
        qty.setPromptText("quantity");
        qty.setPrefWidth(90);
        Label preview = new Label("");
        Runnable updatePreview = () -> {
            Double q = parse(qty.getText());
            if (q == null || q <= 0 || opt.getValue() == null) { preview.setText(""); return; }
            try {
                double cost = model.getEngine().lmsrCost(e.getName(), opt.getValue(), q);
                double fee = e.getCommissionType() == CommissionType.ON_PURCHASE
                        ? cost * e.getCommissionPercent() / 100.0 : 0;
                preview.setText("cost " + Fmt.money(cost)
                        + (fee > 0 ? " + commission " + Fmt.money(fee) : "")
                        + " = " + Fmt.money(cost + fee));
            } catch (Exception ex) { preview.setText(""); }
        };
        qty.textProperty().addListener((o, a, b) -> updatePreview.run());
        opt.valueProperty().addListener((o, a, b) -> updatePreview.run());

        Button buy = new Button("Buy");
        buy.setOnAction(a -> {
            Double q = parse(qty.getText());
            if (q == null || q <= 0) { Dialogs.error("Invalid quantity", "Enter a positive number."); return; }
            act(() -> model.getEngine().lmsrBuy(e.getName(), u.getName(), opt.getValue(), q));
        });

        FlowPane row = new FlowPane(8, 8, new Label("Buy option:"), opt, qty, buy);
        VBox v = new VBox(4, boldLabel("Trade (LMSR)"), row, preview);
        return v;
    }

    private Pane obTrade(OrderBookEvent e, User u) {
        ComboBox<String> opt = new ComboBox<>();
        for (GMOption o : e.getOptions()) opt.getItems().add(o.getName());
        opt.getSelectionModel().selectFirst();
        ComboBox<OrderSide> side = new ComboBox<>();
        side.getItems().addAll(OrderSide.BUY, OrderSide.SELL);
        side.getSelectionModel().selectFirst();
        TextField qty = new TextField(); qty.setPromptText("quantity"); qty.setPrefWidth(90);
        TextField price = new TextField(); price.setPromptText("price"); price.setPrefWidth(80);

        Button send = new Button("Submit order");
        send.setOnAction(a -> {
            Double q = parse(qty.getText()), p = parse(price.getText());
            if (q == null || q <= 0) { Dialogs.error("Invalid quantity", "Enter a positive number."); return; }
            if (p == null || p <= 0) { Dialogs.error("Invalid price", "Enter a positive number."); return; }
            act(() -> model.getEngine().placeOrder(e.getName(), u.getName(),
                    side.getValue(), opt.getValue(), p, q));
        });

        FlowPane row = new FlowPane(8, 8, side, new Label("option:"), opt, qty, price, send);
        return new VBox(4, boldLabel("Trade (Order Book) - max price "
                + Fmt.money(e.getD() - 0.01)), row);
    }

    private Double parse(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    /** Runs an engine action, shows any rule violation, then refreshes everything. */
    private void act(Runnable r) {
        try {
            r.run();
        } catch (GuessMarketException ex) {
            Dialogs.error("Action not allowed", ex.getMessage());
        } catch (Exception ex) {
            Dialogs.error("Unexpected error", String.valueOf(ex.getMessage()));
        }
        User u = usersTable.getSelectionModel().getSelectedItem();
        if (u != null && u.isBlocked())
            Dialogs.info("User blocked", u.getName()
                    + " went into a negative balance and can no longer act in the system.");
        onChanged.run();
    }
}
