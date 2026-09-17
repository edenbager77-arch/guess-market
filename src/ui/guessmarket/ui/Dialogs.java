package guessmarket.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

import java.util.List;

public final class Dialogs {
    private Dialogs() {}

    public static void error(String header, String content) {
        Alert a = new Alert(AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText(header);
        a.setContentText(content);
        a.getDialogPane().setMinWidth(480);
        a.showAndWait();
    }

    public static void errorList(String header, List<String> lines) {
        error(header, String.join("\n", lines));
    }

    public static void info(String header, String content) {
        Alert a = new Alert(AlertType.INFORMATION);
        a.setTitle("Guess Market");
        a.setHeaderText(header);
        a.setContentText(content);
        a.getDialogPane().setMinWidth(420);
        a.showAndWait();
    }
}
