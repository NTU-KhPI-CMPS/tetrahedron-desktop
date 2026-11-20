package org.cmps.tetrahedron.utils;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class DialogUtils {

    public static void displayDialogOnLeft(Pane content, double x, double y) {
        Stage dialog = buildDialogWindow(content);

        dialog.show();
        // -20 is a shift to have space between windows
        placeOnScreen(dialog, content, x - content.getWidth() - 20, y - content.getHeight() / 3);
    }

    public static void displayDialogOnRight(Pane content, double x, double y) {
        Stage dialog = buildDialogWindow(content);

        dialog.show();
        // +40 is a shift to have space between windows
        placeOnScreen(dialog, content, x + 40, y - content.getHeight());
    }

    /**
     * Moves the dialog to the given position, but keeps it inside the screen (small screens / small windows).
     */
    private static void placeOnScreen(Stage dialog, Pane content, double x, double y) {
        Rectangle2D screen = Screen.getScreensForRectangle(x, y, 1, 1).stream()
                                   .findFirst()
                                   .orElse(Screen.getPrimary())
                                   .getVisualBounds();
        double maxX = screen.getMaxX() - content.getWidth();
        double maxY = screen.getMaxY() - content.getHeight();
        dialog.setX(Math.max(screen.getMinX(), Math.min(x, maxX)));
        dialog.setY(Math.max(screen.getMinY(), Math.min(y, maxY)));
    }

    private static Stage buildDialogWindow(Pane content) {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);

        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);

        stage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                Platform.runLater(stage::close);
            }
        });

        return stage;
    }
}
