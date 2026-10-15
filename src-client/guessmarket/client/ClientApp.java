package guessmarket.client;

import guessmarket.engine.dto.Dtos;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;

/**
 * The Exercise 3 client.
 *
 * It owns no market state of its own: everything on screen comes from the
 * server. A background service polls the server's version number about once a
 * second and pulls the full state only when that number has moved, so the
 * screens stay current no matter which client caused the change.
 */
public class ClientApp extends Application {

    private final ServerClient server = new ServerClient();
    private Stage stage;

    private String myName;
    private long lastVersion = -1;
    private ScheduledService<Dtos.StateDto> poller;

    private final Label balanceLabel = new Label("-");
    private final Label statusLabel = new Label("");
    private EventsPane eventsPane;
    private AccountPane accountPane;
    private UsersPane usersPane;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("Guess Market - Client");
        stage.setScene(loginScene());
        stage.setMinWidth(640);
        stage.setMinHeight(480);
        stage.show();
    }

    // ---------------- login ----------------

    private Scene loginScene() {
        Label title = new Label("Guess Market");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #2b5fa8;");
        Label sub = new Label("Enter a user name to join the market");

        TextField nameField = new TextField();
        nameField.setPromptText("user name");
        nameField.setMaxWidth(260);

        Button go = new Button("Log in");
        Label error = new Label();
        error.setStyle("-fx-text-fill: #b3261e;");
        error.setWrapText(true);
        error.setMaxWidth(360);

        Runnable attempt = () -> {
            String name = nameField.getText();
            if (name == null || name.trim().isEmpty()) {
                error.setText("Please enter a user name.");
                return;
            }
            go.setDisable(true);
            error.setText("Connecting...");
            new Thread(() -> {
                try {
                    Dtos.ApiResult r = server.login(name.trim());
                    Platform.runLater(() -> {
                        go.setDisable(false);
                        if (r.ok) {
                            myName = name.trim();
                            showMain();
                        } else {
                            error.setText(r.message);
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        go.setDisable(false);
                        error.setText("Could not reach the server at " + ServerClient.BASE
                                + "\nIs Tomcat running with guess-market.war deployed?");
                    });
                }
            }, "login").start();
        };

        go.setOnAction(e -> attempt.run());
        nameField.setOnAction(e -> attempt.run());

        VBox box = new VBox(14, title, sub, nameField, go, error);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        return new Scene(box, 640, 480);
    }

    // ---------------- main window ----------------

    private void showMain() {
        eventsPane = new EventsPane(server, myName, this::flash);
        accountPane = new AccountPane();
        usersPane = new UsersPane();

        TabPane tabs = new TabPane();
        Tab t1 = new Tab("Events", eventsPane.getNode());
        Tab t2 = new Tab("My Account", accountPane.getNode());
        Tab t3 = new Tab("Users", usersPane.getNode());
        t1.setClosable(false); t2.setClosable(false); t3.setClosable(false);
        tabs.getTabs().addAll(t1, t2, t3);

        BorderPane root = new BorderPane();
        root.setTop(topBar());
        root.setCenter(tabs);

        Scene scene = new Scene(root, 1150, 740);
        stage.setScene(scene);
        stage.setTitle("Guess Market - " + myName);
        startPolling();
    }


    private VBox topBar() {
        Label who = new Label(myName);
        who.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        balanceLabel.setStyle("-fx-background-color: #2b5fa8; -fx-text-fill: white; "
                + "-fx-padding: 4 12 4 12; -fx-font-weight: bold;");

        Button upload = new Button("Upload events file");
        upload.setOnAction(e -> chooseAndUpload());

        Button deposit = new Button("Deposit");
        deposit.setOnAction(e -> askDeposit());

        Button logout = new Button("Log out");
        logout.setOnAction(e -> doLogout());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, who, balanceLabel, upload, deposit, spacer, logout);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10));

        statusLabel.setPadding(new Insets(0, 10, 6, 10));
        statusLabel.setWrapText(true);

        return new VBox(row, statusLabel);
    }

    /** Shows a short message under the top bar instead of interrupting with a dialog. */
    private void flash(String msg, boolean ok) {
        statusLabel.setText(msg == null ? "" : msg);
        statusLabel.setStyle("-fx-text-fill: " + (ok ? "#1a7f37" : "#b3261e") + ";");
    }

    private void chooseAndUpload() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose a Guess Market events file");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        flash("Uploading " + f.getName() + "...", true);
        // the upload talks to the server, so it must not run on the FX thread
        new Thread(() -> {
            try {
                Dtos.ApiResult r = server.upload(f);
                Platform.runLater(() -> {
                    flash(r.ok ? r.message : r.message + " " + String.join(" ", r.errors), r.ok);
                    if (!r.ok && !r.errors.isEmpty())
                        Dialogs.errorList("The file was not loaded.", r.errors);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> flash("Upload failed: " + ex.getMessage(), false));
            }
        }, "upload").start();
    }

    private void askDeposit() {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Deposit");
        d.setHeaderText("Add money to your account");
        d.setContentText("Amount:");
        d.showAndWait().ifPresent(text -> {
            double amount;
            try {
                amount = Double.parseDouble(text.trim());
            } catch (NumberFormatException ex) {
                Dialogs.error("Invalid amount", "Please enter a number.");
                return;
            }
            if (amount <= 0) {
                Dialogs.error("Invalid amount", "The amount must be positive.");
                return;
            }
            new Thread(() -> {
                try {
                    Dtos.ApiResult r = server.deposit(amount);
                    Platform.runLater(() -> flash(r.message, r.ok));
                } catch (Exception ex) {
                    Platform.runLater(() -> flash("Deposit failed: " + ex.getMessage(), false));
                }
            }, "deposit").start();
        });
    }

    private void doLogout() {
        if (poller != null) poller.cancel();
        new Thread(() -> {
            try { server.logout(); } catch (Exception ignored) { }
        }, "logout").start();
        myName = null;
        lastVersion = -1;
        stage.setScene(loginScene());
        stage.setTitle("Guess Market - Client");
    }

    // ---------------- polling ----------------

    /**
     * Asks the server for its version once a second; pulls the full state only
     * when it has changed. Returning null means "nothing new", so the UI is
     * left completely alone.
     */
    private void startPolling() {
        poller = new ScheduledService<>() {
            @Override protected Task<Dtos.StateDto> createTask() {
                return new Task<>() {
                    @Override protected Dtos.StateDto call() throws Exception {
                        long v = server.version();
                        if (v == lastVersion) return null;
                        return server.state();
                    }
                };
            }
        };
        poller.setPeriod(Duration.seconds(1));
        poller.setRestartOnFailure(true);
        poller.setOnSucceeded(e -> {
            Dtos.StateDto s = poller.getValue();
            if (s == null) return;
            lastVersion = s.version;
            applyState(s);
        });
        poller.start();
    }

    private void applyState(Dtos.StateDto s) {
        if (s.me != null) {
            balanceLabel.setText("Balance: " + String.format("%.2f", s.me.balance)
                    + (s.me.blocked ? "  (BLOCKED)" : ""));
        }
        eventsPane.update(s);
        accountPane.update(s);
        usersPane.update(s);
    }

    @Override
    public void stop() {
        if (poller != null) poller.cancel();
    }

    public static void main(String[] args) { launch(args); }
}
