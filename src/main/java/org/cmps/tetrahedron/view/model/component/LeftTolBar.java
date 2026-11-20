package org.cmps.tetrahedron.view.model.component;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.control.Button;
import org.cmps.tetrahedron.controller.ModelController;
import org.cmps.tetrahedron.view.common.BaseLeftTolBar;
import org.cmps.tetrahedron.view.model.LegendDialog;
import org.cmps.tetrahedron.view.model.ModelFilesPickerDialog;

public class LeftTolBar extends BaseLeftTolBar {

    private final ModelController modelController = ModelController.getInstance();

    @FXML
    public Button legendSettings;
    @FXML
    public Button deleteModel;

    @Override
    @FXML
    public void initialize() {
        super.initialize();
        setTooltip(legendSettings, "legend-settings");
        setTooltip(deleteModel, "delete-model");
        legendSettings.setFocusTraversable(false);
        deleteModel.setFocusTraversable(false);
    }

    @FXML
    public void clickOnLegendSettings() {
        Bounds bounds = colorPicker.localToScreen(colorPicker.getBoundsInParent());
        LegendDialog.showDialog(bounds.getMinX(), bounds.getMinY());
        highlightWhileOpen(legendSettings);
    }

    @FXML
    public void clickOnDelete() {
        modelController.clearModel();
        Platform.runLater(ModelFilesPickerDialog::openDialogWindow);
    }
}
