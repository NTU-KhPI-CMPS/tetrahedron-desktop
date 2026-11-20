package org.cmps.tetrahedron.view.common;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.cmps.tetrahedron.controller.LocalizationController;
import org.cmps.tetrahedron.controller.MouseController;
import org.cmps.tetrahedron.enums.VerticeMoveMode;
import org.cmps.tetrahedron.enums.ViewPreset;
import org.cmps.tetrahedron.model.ModelViewSettings;
import org.cmps.tetrahedron.view.model.ColorPickerDialog;

public class BaseLeftTolBar {

    /**
     * Buttons grow from 26px to 40px on hover, keep space for it so the toolbar doesn't overflow.
     */
    private static final double HOVER_RESERVE = 14;

    private final MouseController mouseController = MouseController.getInstance();

    @FXML
    private Button cursor;
    @FXML
    private Button topBottom;
    @FXML
    private Button leftRight;
    @FXML
    private VBox viewButtons;
    @FXML
    private Button viewsMenu;
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
        setTooltip(viewsMenu, "views");

        collapseViewButtonsWhenNotFit((VBox) viewButtons.getParent());

        // toggle button: don't take focus on click, otherwise ":focused" style keeps it highlighted when mode is off
        publicationMode.setFocusTraversable(false);
        colorPicker.setFocusTraversable(false);
        updatePublicationModeStyle();
    }

    /**
     * Replaces view buttons with a single views menu button when the toolbar doesn't fit the height of its container.
     * <p>
     * The container is expected to give the toolbar all the vertical space it can use (its height must not depend
     * on the toolbar). It is checked only when the container is resized, so hovering buttons never switches modes.
     */
    private void collapseViewButtonsWhenNotFit(VBox toolbar) {
        // don't force the container to grow, its height should show how much space there is
        toolbar.setMinHeight(0);

        ChangeListener<Number> onContainerResize = (obs, oldHeight, newHeight) -> updateViewButtonsCollapsed(toolbar);
        toolbar.parentProperty().addListener((obs, oldParent, newParent) -> {
            if (oldParent instanceof Region oldContainer) {
                oldContainer.heightProperty().removeListener(onContainerResize);
            }
            if (newParent instanceof Region newContainer) {
                newContainer.heightProperty().addListener(onContainerResize);
            }
        });
        if (toolbar.getParent() instanceof Region container) {
            container.heightProperty().addListener(onContainerResize);
        }
    }

    private void updateViewButtonsCollapsed(VBox toolbar) {
        Parent parent = toolbar.getParent();
        if (!(parent instanceof Region container) || container.getHeight() <= 0) {
            return;
        }
        double availableHeight = container.getHeight() - container.snappedTopInset() - container.snappedBottomInset();

        double expandedHeight = toolbar.prefHeight(-1);
        if (viewsMenu.isManaged()) {
            expandedHeight += viewButtons.prefHeight(-1) - viewsMenu.prefHeight(-1);
        }

        boolean collapsed = availableHeight < expandedHeight + HOVER_RESERVE;
        viewButtons.setVisible(!collapsed);
        viewButtons.setManaged(!collapsed);
        viewsMenu.setVisible(collapsed);
        viewsMenu.setManaged(collapsed);
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
    public void clickOnViewsMenu() {
        Bounds bounds = viewsMenu.localToScreen(viewsMenu.getBoundsInLocal());
        ViewsDialog.showDialog(bounds.getMinX(), bounds.getMaxY());
        highlightWhileOpen(viewsMenu);
    }

    @FXML
    public void clickOnColorPicker() {
        Bounds bounds = colorPicker.localToScreen(colorPicker.getBoundsInLocal());
        ColorPickerDialog.showColorPicker(bounds.getMinX(), bounds.getMaxY());
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
