package guessmarket.ui;

import guessmarket.engine.model.*;
import guessmarket.engine.service.GuessMarketEngine;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Shared state between the two tabs. */
public class AppModel {

    private final GuessMarketEngine engine = new GuessMarketEngine();
    private final ObservableList<GMEvent> events = FXCollections.observableArrayList();
    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final StringProperty loadedPath = new SimpleStringProperty("(no file loaded)");

    public GuessMarketEngine getEngine() { return engine; }
    public ObservableList<GMEvent> getEvents() { return events; }
    public ObservableList<User> getUsers() { return users; }
    public StringProperty loadedPathProperty() { return loadedPath; }

    public void refreshFromEngine() {
        events.setAll(engine.getAllEvents());
        users.setAll(engine.getAllUsers());
    }
}
