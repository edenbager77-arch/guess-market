package guessmarket.client;

import guessmarket.engine.dto.Dtos;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

import java.util.List;

/** Everyone in the system: name, balance and whether they are a market maker. */
public class UsersPane {

    private final BorderPane root = new BorderPane();
    private final TableView<Dtos.UserSummaryDto> table = new TableView<>();
    private final ObservableList<Dtos.UserSummaryDto> rows = FXCollections.observableArrayList();

    public UsersPane() {
        TableColumn<Dtos.UserSummaryDto, String> c1 = new TableColumn<>("User");
        c1.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        TableColumn<Dtos.UserSummaryDto, String> c2 = new TableColumn<>("Balance");
        c2.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().balance)));
        TableColumn<Dtos.UserSummaryDto, String> c3 = new TableColumn<>("Market Maker");
        c3.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().marketMaker ? "Yes" : "No"));
        TableColumn<Dtos.UserSummaryDto, String> c4 = new TableColumn<>("Status");
        c4.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().blocked ? "BLOCKED" : "Active"));
        table.getColumns().addAll(List.of(c1, c2, c3, c4));
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No users yet."));
        table.setMinWidth(380);

        BorderPane inner = new BorderPane(table);
        inner.setPadding(new Insets(10));
        ScrollPane sp = new ScrollPane(inner);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        root.setCenter(sp);
    }

    public Node getNode() { return root; }

    public void update(Dtos.StateDto s) {
        rows.setAll(s.users);
        table.refresh();
    }
}
