package guessmarket.ui;

import guessmarket.engine.model.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * Events tab: filter row + events table on the left, read-only detail panel
 * on the right (two order books side by side for OB events, LMSR summary
 * otherwise, participants underneath).
 *
 * Trading is deliberately NOT done here - an action needs an acting user, and
 * the spec routes participation through the Users tab. This panel is the
 * "system-wide view" the spec describes.
 */
public class EventsTab {

    private final AppModel model;
    private final BorderPane root = new BorderPane();
    private final TableView<GMEvent> table = new TableView<>();
    private final ObservableList<GMEvent> shown = FXCollections.observableArrayList();

    private final ToggleButton fMethodAll = new ToggleButton("All");
    private final ToggleButton fMethodLmsr = new ToggleButton("LMSR");
    private final ToggleButton fMethodOb = new ToggleButton("Order Book");

    private final ToggleButton fStatusAll = new ToggleButton("All");
    private final ToggleButton fStatusNot = new ToggleButton("Not Started");
    private final ToggleButton fStatusActive = new ToggleButton("Active");
    private final ToggleButton fStatusClosed = new ToggleButton("Closed");

    private final ToggleButton fComAll = new ToggleButton("All");
    private final ToggleButton fComClose = new ToggleButton("On Close");
    private final ToggleButton fComPurchase = new ToggleButton("On Purchase");

    private final VBox detail = new VBox(10);

    public EventsTab(AppModel model) {
        this.model = model;
        buildLeft();
        buildRight();
    }

    public Node getNode() { return root; }

    private void buildLeft() {
        FlowPane filters = new FlowPane(14, 8,
                group("Method:", fMethodAll, fMethodLmsr, fMethodOb),
                group("Status:", fStatusAll, fStatusNot, fStatusActive, fStatusClosed),
                group("Commission:", fComAll, fComClose, fComPurchase));
        filters.setPadding(new Insets(8));
        filters.setStyle("-fx-background-color: #f6d6f0;");
        filters.setPrefWrapLength(520);

        buildTable();

        BorderPane left = new BorderPane();
        left.setTop(filters);
        left.setCenter(table);
        left.setPadding(new Insets(6));

        SplitPane split = new SplitPane();
        ScrollPane detailScroll = new ScrollPane(detail);
        detailScroll.setFitToWidth(true);
        detail.setPadding(new Insets(10));
        table.setMinWidth(330);
        split.getItems().addAll(left, detailScroll);
        split.setDividerPositions(0.48);
        split.setMinWidth(820);
        ScrollPane outer = new ScrollPane(split);
        outer.setFitToWidth(true);
        outer.setFitToHeight(true);
        root.setCenter(outer);
    }

    private VBox group(String label, ToggleButton... buttons) {
        ToggleGroup g = new ToggleGroup();
        for (ToggleButton b : buttons) {
            b.setToggleGroup(g);
            b.setMinWidth(Region.USE_PREF_SIZE);   // never truncate the label
            b.setOnAction(e -> { if (!b.isSelected()) b.setSelected(true); applyFilters(); });
        }
        buttons[0].setSelected(true);
        Label l = new Label(label);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        HBox row = new HBox(4, buttons);
        return new VBox(3, l, row);
    }

    private void buildTable() {
        TableColumn<GMEvent, String> cName = new TableColumn<>("Event");
        cName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        cName.setPrefWidth(240);

        TableColumn<GMEvent, String> cStatus = new TableColumn<>("Status");
        cStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().displayName()));

        TableColumn<GMEvent, String> cKind = new TableColumn<>("Type");
        cKind.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKind().displayName()));

        TableColumn<GMEvent, String> cCom = new TableColumn<>("Commission");
        cCom.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCommissionPercent() + "% " + c.getValue().getCommissionType().displayName()));
        cCom.setPrefWidth(130);

        TableColumn<GMEvent, String> cAcct = new TableColumn<>("Account");
        cAcct.setCellValueFactory(c -> new SimpleStringProperty(Fmt.money(c.getValue().getAccount())));

        TableColumn<GMEvent, String> cMm = new TableColumn<>("Market Maker");
        cMm.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getMarketMakerName() == null ? "-" : c.getValue().getMarketMakerName()));

        table.getColumns().addAll(List.of(cName, cStatus, cKind, cCom, cAcct, cMm));
        table.setItems(shown);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No events. Load a file to begin."));
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showDetail(b));
    }

    private void buildRight() {
        Label t = new Label("Event details");
        t.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        detail.getChildren().add(t);
    }

    private void applyFilters() {
        shown.setAll(model.getEvents().filtered(e -> {
            if (fMethodLmsr.isSelected() && e.getKind() != EventKind.LMSR) return false;
            if (fMethodOb.isSelected() && e.getKind() != EventKind.ORDER_BOOK) return false;
            if (fStatusNot.isSelected() && e.getStatus() != EventStatus.NOT_STARTED) return false;
            if (fStatusActive.isSelected() && e.getStatus() != EventStatus.ACTIVE) return false;
            if (fStatusClosed.isSelected() && e.getStatus() != EventStatus.CLOSED) return false;
            if (fComClose.isSelected() && e.getCommissionType() != CommissionType.ON_CLOSE) return false;
            if (fComPurchase.isSelected() && e.getCommissionType() != CommissionType.ON_PURCHASE) return false;
            return true;
        }));
    }

    public void refresh() {
        GMEvent sel = table.getSelectionModel().getSelectedItem();
        applyFilters();
        table.refresh();
        if (sel != null && shown.contains(sel)) {
            table.getSelectionModel().select(sel);
            showDetail(sel);
        } else {
            showDetail(table.getSelectionModel().getSelectedItem());
        }
    }

    private void showDetail(GMEvent e) {
        detail.getChildren().clear();
        Label title = new Label(e == null ? "Event details" : e.getName());
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        title.setWrapText(true);
        detail.getChildren().add(title);
        if (e == null) {
            detail.getChildren().add(new Label("Select an event to see its details."));
            return;
        }

        Label meta = new Label(e.getDescription()
                + "\nStatus: " + e.getStatus().displayName()
                + "   |   Type: " + e.getKind().displayName()
                + "\nCommission: " + e.getCommissionPercent() + "% " + e.getCommissionType().displayName()
                + "   |   Market maker: " + e.getMarketMakerName()
                + "\nEvent account: " + Fmt.money(e.getAccount())
                + "   |   Commission collected: " + Fmt.money(e.getTotalCommissionCollected())
                + (e.getWinningOption() != null ? "\nWinning option: " + e.getWinningOption() : ""));
        meta.setWrapText(true);
        detail.getChildren().add(meta);

        if (e instanceof LmsrEvent) detail.getChildren().add(DetailPanels.lmsrPanel((LmsrEvent) e));
        else detail.getChildren().add(DetailPanels.orderBookPanel((OrderBookEvent) e));

        detail.getChildren().add(DetailPanels.participantsPanel(e, model.getUsers()));
    }
}
