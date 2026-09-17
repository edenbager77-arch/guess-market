package guessmarket.ui;

import guessmarket.engine.xml.GMEventFileLoader;
import guessmarket.engine.xml.LoadResult;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/**
 * Guess Market - Ex2 JavaFX client.
 *
 * Layout follows the supplied sketch: a shared top bar (load button + current
 * file path) above a two-tab area (Events / Users).
 *
 * The UI is built programmatically rather than from FXML so that nothing
 * depends on resource lookup paths inside the packaged jar.
 */
public class Main extends Application {

    private final AppModel model = new AppModel();
    private final Label pathLabel = new Label();
    private final ProgressBar progress = new ProgressBar(0);
    private EventsTab eventsTab;
    private UsersTab usersTab;
    private Stage stage;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        BorderPane root = new BorderPane();
        root.setTop(buildTopBar());

        eventsTab = new EventsTab(model);
        usersTab = new UsersTab(model, this::refreshAll);

        TabPane tabs = new TabPane();
        Tab t1 = new Tab("Events", eventsTab.getNode());
        Tab t2 = new Tab("Users", usersTab.getNode());
        t1.setClosable(false);
        t2.setClosable(false);
        tabs.getTabs().addAll(t1, t2);
        // keep both tabs in sync whenever the user switches
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> refreshAll());
        root.setCenter(tabs);

        Scene scene = new Scene(root, 1150, 720);
        primaryStage.setTitle("Guess Market");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(640);
        primaryStage.setMinHeight(480);
        primaryStage.show();
    }

    private VBox buildTopBar() {
        Button load = new Button("Load File");
        load.setOnAction(e -> chooseAndLoad());

        pathLabel.textProperty().bind(model.loadedPathProperty());
        pathLabel.setMaxWidth(Double.MAX_VALUE);
        pathLabel.setStyle("-fx-background-color: #fdece4; -fx-padding: 6 10 6 10;");
        HBox.setHgrow(pathLabel, Priority.ALWAYS);

        HBox row = new HBox(10, load, pathLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10));

        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);
        progress.setManaged(false);

        Label title = new Label("Guess Market");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2b5fa8;");
        HBox titleBox = new HBox(title);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setPadding(new Insets(6, 0, 0, 0));

        VBox top = new VBox(titleBox, row, progress);
        return top;
    }

    private void chooseAndLoad() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose a Guess Market XML file");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        loadInBackground(f.getAbsolutePath());
    }

    /**
     * Loads on a JavaFX Task with a visible progress bar. The parse itself is
     * near-instant, so a short artificial delay is added (as the spec asks) so
     * the progress indicator is actually observable.
     */
    private void loadInBackground(String path) {
        Task<LoadResult> task = new Task<>() {
            @Override protected LoadResult call() throws Exception {
                updateProgress(0, 1);
                Thread.sleep(500);
                updateProgress(0.3, 1);
                LoadResult r = new GMEventFileLoader().load(path);
                updateProgress(0.7, 1);
                Thread.sleep(1000);
                updateProgress(1, 1);
                return r;
            }
        };

        progress.progressProperty().bind(task.progressProperty());
        progress.setVisible(true);
        progress.setManaged(true);

        task.setOnSucceeded(e -> {
            progress.progressProperty().unbind();
            progress.setVisible(false);
            progress.setManaged(false);
            LoadResult r = task.getValue();
            if (r.isValid()) {
                model.getEngine().replaceContents(r.getUsers(), r.getEvents());
                model.loadedPathProperty().set(path);
                refreshAll();
                Dialogs.info("File loaded successfully",
                        r.getEvents().size() + " event(s) and " + r.getUsers().size() + " user(s) loaded.");
            } else {
                Dialogs.errorList("The file is invalid and was not loaded."
                        + "\nAny previously loaded file is unchanged.", r.getErrors());
            }
        });

        task.setOnFailed(e -> {
            progress.progressProperty().unbind();
            progress.setVisible(false);
            progress.setManaged(false);
            Throwable ex = task.getException();
            Dialogs.error("Could not load the file.", ex == null ? "Unknown error" : ex.toString());
        });

        Thread th = new Thread(task, "gm-loader");
        th.setDaemon(true);
        th.start();
    }

    private void refreshAll() {
        model.refreshFromEngine();
        if (eventsTab != null) eventsTab.refresh();
        if (usersTab != null) usersTab.refresh();
    }

    public static void main(String[] args) { launch(args); }
}
