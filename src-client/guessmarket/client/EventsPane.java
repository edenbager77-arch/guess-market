package guessmarket.client;

import guessmarket.engine.dto.Dtos;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The events screen: filters and the event list on the left, the selected
 * event's details and the actions this client may take on the right.
 *
 * The whole pane is rebuilt from the server state on every change, so the
 * selected event is tracked by name and restored after each refresh.
 */
public class EventsPane {

    private final ServerClient server;
    private final String myName;
    private final BiConsumer<String, Boolean> flash;

    private final BorderPane root = new BorderPane();
    private final TableView<Dtos.EventDto> table = new TableView<>();
    private final ObservableList<Dtos.EventDto> rows = FXCollections.observableArrayList();
    private final VBox detail = new VBox(10);

    private List<Dtos.EventDto> allEvents = new ArrayList<>();
    private String selectedName;

    private final ToggleButton kAll = new ToggleButton("All");
    private final ToggleButton kLmsr = new ToggleButton("LMSR");
    private final ToggleButton kOb = new ToggleButton("Order Book");
    private final ToggleButton sAll = new ToggleButton("All");
    private final ToggleButton sNot = new ToggleButton("Not Started");
    private final ToggleButton sAct = new ToggleButton("Active");
    private final ToggleButton sClosed = new ToggleButton("Closed");
    private final ToggleButton cAll = new ToggleButton("All");
    private final ToggleButton cClose = new ToggleButton("On Close");
    private final ToggleButton cPurchase = new ToggleButton("On Purchase");

    public EventsPane(ServerClient server, String myName, BiConsumer<String, Boolean> flash) {
        this.server = server;
        this.myName = myName;
        this.flash = flash;
        build();
    }

    public Node getNode() { return root; }

    private void build() {
        FlowPane filters = new FlowPane(14, 8,
                group("Type:", kAll, kLmsr, kOb),
                group("Status:", sAll, sNot, sAct, sClosed),
                group("Commission:", cAll, cClose, cPurchase));
        filters.setPadding(new Insets(8));
        filters.setStyle("-fx-background-color: #f2e6f5;");
        filters.setPrefWrapLength(520);

        TableColumn<Dtos.EventDto, String> c1 = new TableColumn<>("Event");
        c1.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        c1.setPrefWidth(230);
        TableColumn<Dtos.EventDto, String> c2 = new TableColumn<>("Status");
        c2.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status));
        TableColumn<Dtos.EventDto, String> c3 = new TableColumn<>("Type");
        c3.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().kind));
        TableColumn<Dtos.EventDto, String> c4 = new TableColumn<>("Commission");
        c4.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().commissionPercent + "% " + c.getValue().commissionType));
        c4.setPrefWidth(130);
        TableColumn<Dtos.EventDto, String> c5 = new TableColumn<>("Account");
        c5.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().account)));
        TableColumn<Dtos.EventDto, String> c6 = new TableColumn<>("Market Maker");
        c6.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().marketMaker == null ? "-" : c.getValue().marketMaker));
        table.getColumns().addAll(List.of(c1, c2, c3, c4, c5, c6));
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No events yet. Upload an events file to add some."));
        table.setMinWidth(340);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) selectedName = b.name;
            showDetail(b);
        });

        BorderPane left = new BorderPane();
        left.setTop(filters);
        left.setCenter(table);
        left.setPadding(new Insets(6));

        detail.setPadding(new Insets(10));
        ScrollPane detailScroll = new ScrollPane(detail);
        detailScroll.setFitToWidth(true);

        SplitPane split = new SplitPane(left, detailScroll);
        split.setDividerPositions(0.46);
        split.setMinWidth(860);

        ScrollPane outer = new ScrollPane(split);
        outer.setFitToWidth(true);
        outer.setFitToHeight(true);
        root.setCenter(outer);

        showDetail(null);
    }

    private VBox group(String label, ToggleButton... buttons) {
        ToggleGroup g = new ToggleGroup();
        for (ToggleButton b : buttons) {
            b.setToggleGroup(g);
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setOnAction(e -> { if (!b.isSelected()) b.setSelected(true); applyFilters(); });
        }
        buttons[0].setSelected(true);
        Label l = new Label(label);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        return new VBox(3, l, new HBox(4, buttons));
    }

    // ---------------- refresh from server ----------------

    public void update(Dtos.StateDto s) {
        allEvents = s.events;
        applyFilters();
    }

    private void applyFilters() {
        List<Dtos.EventDto> filtered = new ArrayList<>();
        for (Dtos.EventDto e : allEvents) {
            if (kLmsr.isSelected() && !"LMSR".equals(e.kind)) continue;
            if (kOb.isSelected() && !"Order Book".equals(e.kind)) continue;
            if (sNot.isSelected() && !"Not Started".equals(e.status)) continue;
            if (sAct.isSelected() && !"Active".equals(e.status)) continue;
            if (sClosed.isSelected() && !"Closed".equals(e.status)) continue;
            if (cClose.isSelected() && !"On Close".equals(e.commissionType)) continue;
            if (cPurchase.isSelected() && !"On Purchase".equals(e.commissionType)) continue;
            filtered.add(e);
        }
        rows.setAll(filtered);
        table.refresh();

        // keep whatever the user had selected, and rebuild its details with fresh numbers
        Dtos.EventDto keep = null;
        for (Dtos.EventDto e : filtered) if (e.name.equals(selectedName)) keep = e;
        if (keep != null) {
            table.getSelectionModel().select(keep);
            showDetail(keep);
        } else if (selectedName != null) {
            showDetail(null);
        }
    }

    // ---------------- details ----------------

    private void showDetail(Dtos.EventDto e) {
        detail.getChildren().clear();
        if (e == null) {
            Label l = new Label("Select an event to see its details.");
            detail.getChildren().add(l);
            return;
        }

        Label title = new Label(e.name);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        title.setWrapText(true);
        detail.getChildren().add(title);

        Label meta = new Label(e.description
                + "\nStatus: " + e.status + "   |   Type: " + e.kind
                + "\nCommission: " + e.commissionPercent + "% " + e.commissionType
                + "   |   Market maker: " + e.marketMaker
                + "\nEvent account: " + String.format("%.2f", e.account)
                + "   |   Commission collected: " + String.format("%.2f", e.commissionCollected)
                + (e.winningOption != null ? "\nWinning option: " + e.winningOption : ""));
        meta.setWrapText(true);
        detail.getChildren().add(meta);

        detail.getChildren().add(bold("Current state"));
        GridPane g = new GridPane();
        g.setHgap(16); g.setVgap(3);
        g.add(bold("Option"), 0, 0);
        g.add(bold("Value"), 1, 0);
        g.add(bold("Shares bought"), 2, 0);
        int r = 1;
        for (Dtos.OptionDto o : e.options) {
            g.add(new Label(o.name), 0, r);
            g.add(new Label(String.format("%.2f", o.value)), 1, r);
            g.add(new Label(String.format("%.2f", o.totalSharesBought)), 2, r);
            r++;
        }
        detail.getChildren().add(g);

        boolean isMm = myName.equals(e.marketMaker);
        if (isMm && "Not Started".equals(e.status)) detail.getChildren().add(openControls(e));
        if (isMm && "Active".equals(e.status)) detail.getChildren().add(closeControls(e));
        if ("Active".equals(e.status)) {
            detail.getChildren().add("LMSR".equals(e.kind) ? lmsrTrade(e) : obTrade(e));
        }

        if ("LMSR".equals(e.kind)) {
            detail.getChildren().add(new Label("b = " + e.b));
            detail.getChildren().add(bold("Trade history (newest first)"));
            detail.getChildren().add(historyTable(e));
        } else {
            detail.getChildren().add(new Label("Base value d = " + e.d
                    + "   |   initial = " + e.initial + "   |   mint allowed: " + e.allowMint));
            HBox books = new HBox(10);
            for (Dtos.BookDto b : e.books) books.getChildren().add(bookBox(b));
            for (Node n : books.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
            detail.getChildren().add(books);
        }

        detail.getChildren().add(bold("Participants"));
        detail.getChildren().add(participantsTable(e));
    }

    private Label bold(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    private TableView<Dtos.TradeDto> historyTable(Dtos.EventDto e) {
        TableView<Dtos.TradeDto> t = new TableView<>();
        TableColumn<Dtos.TradeDto, String> a = new TableColumn<>("User");
        a.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().user));
        TableColumn<Dtos.TradeDto, String> b = new TableColumn<>("Option");
        b.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().option));
        TableColumn<Dtos.TradeDto, String> c3 = new TableColumn<>("Quantity");
        c3.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().quantity)));
        TableColumn<Dtos.TradeDto, String> c4 = new TableColumn<>("Price paid");
        c4.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().pricePaid)));
        TableColumn<Dtos.TradeDto, String> c5 = new TableColumn<>("Commission");
        c5.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().commissionPaid)));
        t.getColumns().addAll(List.of(a, b, c3, c4, c5));
        t.setItems(FXCollections.observableArrayList(e.history));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPrefHeight(170);
        t.setPlaceholder(new Label("No trades yet."));
        return t;
    }

    private Pane bookBox(Dtos.BookDto b) {
        VBox v = new VBox(6);
        v.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 4; -fx-padding: 8;");
        Label t = new Label(b.option + " - order book");
        t.setStyle("-fx-font-weight: bold;");
        t.setWrapText(true);
        v.getChildren().add(t);
        v.getChildren().add(new Label(
                "LAST " + num(b.last) + "   BID " + num(b.bid) + "   ASK " + num(b.ask)
                        + "\nMID " + num(b.mid) + "   SPREAD " + num(b.spread)));
        v.getChildren().add(new Label("Buy orders (bids)"));
        v.getChildren().add(orderTable(b.bids));
        v.getChildren().add(new Label("Sell orders (asks)"));
        v.getChildren().add(orderTable(b.asks));
        return v;
    }

    private String num(Double d) { return d == null ? "-" : String.format("%.2f", d); }

    private TableView<Dtos.OrderDto> orderTable(List<Dtos.OrderDto> orders) {
        TableView<Dtos.OrderDto> t = new TableView<>();
        TableColumn<Dtos.OrderDto, String> a = new TableColumn<>("User");
        a.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().user));
        TableColumn<Dtos.OrderDto, String> b = new TableColumn<>("Qty");
        b.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().quantity)));
        TableColumn<Dtos.OrderDto, String> c3 = new TableColumn<>("Price");
        c3.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().price)));
        t.getColumns().addAll(List.of(a, b, c3));
        t.setItems(FXCollections.observableArrayList(orders));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPrefHeight(110);
        t.setPlaceholder(new Label("empty"));
        return t;
    }

    private TableView<Dtos.ParticipantDto> participantsTable(Dtos.EventDto e) {
        TableView<Dtos.ParticipantDto> t = new TableView<>();
        TableColumn<Dtos.ParticipantDto, String> a = new TableColumn<>("User");
        a.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().user));
        t.getColumns().add(a);
        for (int i = 0; i < e.options.size(); i++) {
            final int idx = i;
            TableColumn<Dtos.ParticipantDto, String> col = new TableColumn<>(e.options.get(i).name);
            col.setCellValueFactory(c -> new SimpleStringProperty(
                    idx < c.getValue().sharesPerOption.size()
                            ? String.format("%.2f", c.getValue().sharesPerOption.get(idx)) : "-"));
            t.getColumns().add(col);
        }
        TableColumn<Dtos.ParticipantDto, String> val = new TableColumn<>("Value");
        val.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().holdingsValue)));
        t.getColumns().add(val);
        t.setItems(FXCollections.observableArrayList(e.participants));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPrefHeight(150);
        t.setPlaceholder(new Label("No participants yet."));
        return t;
    }

    // ---------------- actions ----------------

    private Pane openControls(Dtos.EventDto e) {
        Button open = new Button("Open event");
        open.setOnAction(a -> call(() -> server.openEvent(e.name)));
        return new VBox(4, bold("You are the market maker of this event"), open);
    }

    private Pane closeControls(Dtos.EventDto e) {
        ComboBox<String> winner = new ComboBox<>();
        for (Dtos.OptionDto o : e.options) winner.getItems().add(o.name);
        winner.setPromptText("winning option");
        Button close = new Button("Close event");
        close.setOnAction(a -> {
            if (winner.getValue() == null) {
                Dialogs.error("Pick a winner", "Choose the winning option first.");
                return;
            }
            call(() -> server.closeEvent(e.name, winner.getValue()));
        });
        return new VBox(4, bold("Close this event"), new HBox(8, winner, close));
    }

    private Pane lmsrTrade(Dtos.EventDto e) {
        ComboBox<String> opt = new ComboBox<>();
        for (Dtos.OptionDto o : e.options) opt.getItems().add(o.name);
        opt.getSelectionModel().selectFirst();
        TextField qty = new TextField();
        qty.setPromptText("quantity");
        qty.setPrefWidth(90);
        Button buy = new Button("Buy");
        buy.setOnAction(a -> {
            Double q = parse(qty.getText());
            if (q == null || q <= 0) { Dialogs.error("Invalid quantity", "Enter a positive number."); return; }
            call(() -> server.lmsrBuy(e.name, opt.getValue(), q));
        });
        FlowPane row = new FlowPane(8, 8, new Label("Buy option:"), opt, qty, buy);
        return new VBox(4, bold("Trade (LMSR)"), row);
    }

    private Pane obTrade(Dtos.EventDto e) {
        ComboBox<String> side = new ComboBox<>();
        side.getItems().addAll("BUY", "SELL");
        side.getSelectionModel().selectFirst();
        ComboBox<String> opt = new ComboBox<>();
        for (Dtos.OptionDto o : e.options) opt.getItems().add(o.name);
        opt.getSelectionModel().selectFirst();
        TextField qty = new TextField(); qty.setPromptText("quantity"); qty.setPrefWidth(90);
        TextField price = new TextField(); price.setPromptText("price"); price.setPrefWidth(80);
        Button send = new Button("Submit order");
        send.setOnAction(a -> {
            Double q = parse(qty.getText()), p = parse(price.getText());
            if (q == null || q <= 0) { Dialogs.error("Invalid quantity", "Enter a positive number."); return; }
            if (p == null || p <= 0) { Dialogs.error("Invalid price", "Enter a positive number."); return; }
            call(() -> server.placeOrder(e.name, opt.getValue(), side.getValue(), p, q));
        });
        FlowPane row = new FlowPane(8, 8, side, new Label("option:"), opt, qty, price, send);
        double max = e.d == null ? 0 : e.d - 0.01;
        return new VBox(4, bold("Trade (Order Book) - maximum price " + String.format("%.2f", max)), row);
    }

    /** Runs a server call off the FX thread and reports the outcome in the status line. */
    private interface Call { Dtos.ApiResult run() throws Exception; }

    private void call(Call c) {
        new Thread(() -> {
            try {
                Dtos.ApiResult r = c.run();
                Platform.runLater(() -> flash.accept(r.message, r.ok));
            } catch (Exception ex) {
                Platform.runLater(() -> flash.accept("Request failed: " + ex.getMessage(), false));
            }
        }, "gm-action").start();
    }

    private Double parse(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
