package org.cmps.tetrahedron.view.mesh;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.cmps.tetrahedron.controller.LocalizationController;
import org.cmps.tetrahedron.enums.MeshStatus;
import org.cmps.tetrahedron.exception.ModelValidationException;
import org.cmps.tetrahedron.utils.NativesExtractor;
import org.cmps.tetrahedron.view.common.ErrorDialog;
import org.cmps.tetrahedron.viewmodel.MeshViewModel;

import java.nio.file.Path;
import java.util.Objects;

import static org.cmps.tetrahedron.controller.LocalizationController.MESH_PROGRESS_DIALOG_BUNDLE;

public class MeshPage {

    private final MeshViewModel meshViewModel = MeshViewModel.getInstance();

    @FXML
    private HBox progressContainer;

    @FXML
    private Label meshStatus;

    @FXML
    private void initialize() {
        meshViewModel.getMeshStatus().addListener((_, _, newValue) -> {
            initMeshStatus(newValue);
        });

        String gmshName = "libgmsh" + NativesExtractor.getPlatformExtension();
        Path gmshPath = NativesExtractor.getNativesDir().resolve(gmshName);
        System.load(gmshPath.toString());

        if (meshViewModel.getStlFileName().get() != null) {
            initMeshStatus(meshViewModel.getMeshStatus().get());
            return;
        }

        if (Objects.equals(System.getProperty("debug"), "true")) {
            initModelIfInDebug();
        } else {
            Platform.runLater(MeshFilesPickerDialog::openDialogWindow);
        }
    }

    private void initModelIfInDebug() {
        try {
            meshViewModel.displayStlModel("models/model-2d.stl");
        } catch (ModelValidationException e) {
            new ErrorDialog(e);
        }
    }

    @FXML
    private void onProgressClicked() {
        var dialog = MeshProgressDialog.openDialogWindow();
        dialog.appendLog(meshViewModel.getMesherLogs().toString());
        meshViewModel.setMeshProgressDialog(dialog);
    }

    private void initMeshStatus(MeshStatus status) {
        if (status == null) {
            return;
        }
        progressContainer.visibleProperty().set(true);

        String localizedStatus = LocalizationController
                .getInstance()
                .getString(MESH_PROGRESS_DIALOG_BUNDLE, status.getLocalizedTextId());
        meshStatus.setText(localizedStatus);
    }
}
