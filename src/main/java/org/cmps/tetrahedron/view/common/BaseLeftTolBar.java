package org.cmps.tetrahedron.view.common;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

import org.cmps.tetrahedron.controller.LocalizationController;
import org.cmps.tetrahedron.controller.MouseController;
import org.cmps.tetrahedron.enums.VerticeMoveMode;
import org.cmps.tetrahedron.enums.ViewPreset;
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
    private Button viewFront;
    @FXML
    private Button viewTop;
    @FXML
    private Button viewRight;
    @FXML
    private Button viewLeft;
    @FXML
    private Button viewIsometric;
    @FXML
    public Button colorPicker;

    @FXML
    public Button publicationMode;

    @FXML
    public void initialize() {
        setTooltip(cursor, "rotate-free");
        setTooltip(topBottom, "rotate-up-down");
        setTooltip(leftRight, "rotate-left-right");
        setTooltip(colorPicker, "colors");
        setTooltip(publicationMode, "publication-mode");
        setTooltip(viewFront, "view-front");
        setTooltip(viewTop, "view-top");
        setTooltip(viewRight, "view-right");
        setTooltip(viewLeft, "view-left");
        setTooltip(viewIsometric, "view-isometric");

        // toggle button: don't take focus on click, otherwise ":focused" style keeps it highlighted when mode is off
        publicationMode.setFocusTraversable(false);
        colorPicker.setFocusTraversable(false);
        updatePublicationModeStyle();
    }

    /**
     * Keeps a menu button selected only while its dialog is open (main window gets focus back when dialog closes).
     */
    protected void highlightWhileOpen(Button button) {
        focus(button);
        button.getScene().getWindow().focusedProperty().addListener(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean wasFocused, Boolean isFocused) {
                if (isFocused) {
                    unFocus(button);
                    observable.removeListener(this);
                }
            }
        });
    }

    protected void setTooltip(Button button, String key) {
        String text = LocalizationController.getInstance().getString(LocalizationController.LEFT_TOOLBAR_BUNDLE, key);
        button.setTooltip(new Tooltip(text));
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
    public void clickOnViewFront() {
        mouseController.setView(ViewPreset.FRONT);
    }

    @FXML
    public void clickOnViewTop() {
        mouseController.setView(ViewPreset.TOP);
    }

    @FXML
    public void clickOnViewRight() {
        mouseController.setView(ViewPreset.RIGHT);
    }

    @FXML
    public void clickOnViewLeft() {
        mouseController.setView(ViewPreset.LEFT);
    }

    @FXML
    public void clickOnViewIsometric() {
        mouseController.setView(ViewPreset.ISOMETRIC);
    }

    @FXML
    public void clickOnColorPicker() {
        Bounds bounds = colorPicker.localToScreen(colorPicker.getBoundsInParent());
        ColorPickerDialog.showColorPicker(bounds.getMinX(), bounds.getMinY());
        highlightWhileOpen(colorPicker);
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
