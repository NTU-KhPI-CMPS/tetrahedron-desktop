package org.cmps.tetrahedron.view.common;

import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.cmps.tetrahedron.controller.LocalizationController;
import org.cmps.tetrahedron.controller.MouseController;
import org.cmps.tetrahedron.enums.VerticeMoveMode;
import org.cmps.tetrahedron.model.ModelViewSettings;
import org.cmps.tetrahedron.view.model.ColorPickerDialog;

public class BaseLeftTolBar {

    private final MouseController mouseController = MouseController.getInstance();

    @FXML
    private Button cursor;
    @FXML
    private Button topBottom;
    @FXML
    private Button leftRight;
    @FXML
    public Button colorPicker;

    @FXML
    public Button publicationMode;

    @FXML
    public void initialize() {
        String tooltip = LocalizationController.getInstance()
                .getString(LocalizationController.RIGHT_TOOLBAR_BUNDLE, "publication-mode");
        publicationMode.setTooltip(new Tooltip(tooltip));
        // toggle button: don't take focus on click, otherwise ":focused" style keeps it highlighted when mode is off
        publicationMode.setFocusTraversable(false);
        updatePublicationModeStyle();
    }

    @FXML
    public void clickOnPublicationMode() {
        ModelViewSettings settings = ModelViewSettings.getInstance();
        settings.setPublicationMode(!settings.isPublicationMode());
        updatePublicationModeStyle();
    }

    private void updatePublicationModeStyle() {
        if (ModelViewSettings.getInstance().isPublicationMode()) {
            focus(publicationMode);
        } else {
            unFocus(publicationMode);
        }
    }

    @FXML
    public void clickOnCursor() {
        mouseController.setVerticalMoveMode(VerticeMoveMode.CURSOR);

        focus(cursor);
        unFocus(leftRight);
        unFocus(topBottom);
    }

    @FXML
    public void clickOnTopBottom() {
        mouseController.setVerticalMoveMode(VerticeMoveMode.UP_DOWN);

        focus(topBottom);
        unFocus(leftRight);
        unFocus(cursor);
    }

    @FXML
    public void clickOnLeftRight() {
        mouseController.setVerticalMoveMode(VerticeMoveMode.LEFT_RIGHT);

        focus(leftRight);
        unFocus(topBottom);
        unFocus(cursor);
    }

    @FXML
    public void clickOnColorPicker() {
        Bounds bounds = colorPicker.localToScreen(colorPicker.getBoundsInParent());
        ColorPickerDialog.showColorPicker(bounds.getMinX(), bounds.getMinY());
    }

    protected void focus(Button button) {
        if (!button.getStyleClass().contains("button-selected")) {
            button.getStyleClass().add("button-selected");
        }

        Node graphic = button.getGraphic();
        if (graphic == null) {
            return;
        }

        if (!graphic.getStyleClass().contains("button-selected")) {
            graphic.getStyleClass().add("button-selected");
        }
    }

    protected void unFocus(Button button) {
        button.getStyleClass().remove("button-selected");
        Node graphic = button.getGraphic();
        if (graphic == null) {
            return;
        }

        graphic.getStyleClass().removeAll("button-selected");
    }
}
