package guessmarket.client;

import guessmarket.engine.dto.Dtos;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * The client's own account: balance, every line of the statement, and what
 * the user is involved in across all events.
 */
public class AccountPane {

    private final BorderPane root = new BorderPane();
    private final Label balance = new Label("-");

    private final TableView<Dtos.AccountLineDto> statement = new TableView<>();
    private final ObservableList<Dtos.AccountLineDto> statementRows = FXCollections.observableArrayList();

    private final TableView<Dtos.InvolvementDto> involvement = new TableView<>();
    private final ObservableList<Dtos.InvolvementDto> involvementRows = FXCollections.observableArrayList();

    public AccountPane() {
        balance.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        TableColumn<Dtos.AccountLineDto, String> s1 = new TableColumn<>("Description");
        s1.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().description));
        s1.setPrefWidth(320);
        TableColumn<Dtos.AccountLineDto, String> s2 = new TableColumn<>("Amount");
        s2.setCellValueFactory(c -> new SimpleStringProperty(String.format("%+.2f", c.getValue().amount)));
        TableColumn<Dtos.AccountLineDto, String> s3 = new TableColumn<>("Balance after");
        s3.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().balanceAfter)));
        statement.getColumns().addAll(List.of(s1, s2, s3));
        statement.setItems(statementRows);
        statement.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        statement.setPlaceholder(new Label("No account activity yet."));
        statement.setPrefHeight(240);

        TableColumn<Dtos.InvolvementDto, String> i1 = new TableColumn<>("Event");
        i1.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().eventName));
        i1.setPrefWidth(200);
        TableColumn<Dtos.InvolvementDto, String> i2 = new TableColumn<>("Status");
        i2.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status));
        TableColumn<Dtos.InvolvementDto, String> i3 = new TableColumn<>("Role");
        i3.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().role));
        TableColumn<Dtos.InvolvementDto, String> i4 = new TableColumn<>("Holdings");
        i4.setCellValueFactory(c -> new SimpleStringProperty(holdings(c.getValue())));
        i4.setPrefWidth(240);
        TableColumn<Dtos.InvolvementDto, String> i5 = new TableColumn<>("Commission paid");
        i5.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().commissionPaid)));
        TableColumn<Dtos.InvolvementDto, String> i6 = new TableColumn<>("Profit / Loss");
        i6.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().profitLoss == null ? "-" : String.format("%+.2f", c.getValue().profitLoss)));
        involvement.getColumns().addAll(List.of(i1, i2, i3, i4, i5, i6));
        involvement.setItems(involvementRows);
        involvement.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        involvement.setPlaceholder(new Label("You are not taking part in any event yet."));
        involvement.setPrefHeight(240);

        VBox box = new VBox(10,
                balance,
                sectionLabel("Events you are involved in"), involvement,
                sectionLabel("Account statement"), statement);
        box.setPadding(new Insets(12));
        box.setMinWidth(560);

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        root.setCenter(sp);
    }

    private Label sectionLabel(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    /** "Yes: 10.00 (paid 5.12) | No: 0.00 (paid 0.00)" */
    private String holdings(Dtos.InvolvementDto inv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inv.optionNames.size(); i++) {
            if (i > 0) sb.append("  |  ");
            sb.append(inv.optionNames.get(i)).append(": ")
              .append(String.format("%.2f", inv.shares.get(i)))
              .append(" (paid ").append(String.format("%.2f", inv.paid.get(i))).append(")");
        }
        return sb.toString();
    }

    public Node getNode() { return root; }

    public void update(Dtos.StateDto s) {
        if (s.me == null) return;
        balance.setText("Balance: " + String.format("%.2f", s.me.balance)
                + (s.me.blocked ? "   (BLOCKED - negative balance)" : ""));
        statementRows.setAll(s.me.statement);
        involvementRows.setAll(s.me.involvements);
        statement.refresh();
        involvement.refresh();
    }
}
