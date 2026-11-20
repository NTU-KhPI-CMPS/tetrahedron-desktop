package org.cmps.tetrahedron.view.mesh;

import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.cmps.tetrahedron.controller.LocalizationController;
import org.cmps.tetrahedron.enums.MeshStatus;
import org.cmps.tetrahedron.exception.ModelValidationException;
import org.cmps.tetrahedron.model.ModelViewSettings;
import org.cmps.tetrahedron.utils.NativesExtractor;
import org.cmps.tetrahedron.view.common.ErrorDialog;
import org.cmps.tetrahedron.viewmodel.MeshViewModel;

import java.nio.file.Path;
import java.util.Objects;

import static org.cmps.tetrahedron.controller.LocalizationController.MESH_PROGRESS_DIALOG_BUNDLE;

public class MeshPage {

    private final MeshViewModel meshViewModel = MeshViewModel.getInstance();

    @FXML
    private HBox navbar;
    @FXML
    private HBox progressContainer;

    private final BooleanProperty meshStatusShown = new SimpleBooleanProperty(false);

    @FXML
    private Label meshStatus;

    @FXML
    private void initialize() {
        // hide logo and mesh status on screenshots for articles
        BooleanBinding notPublicationMode = ModelViewSettings.getInstance().getPublicationMode().not();
        navbar.lookup("#logo").visibleProperty().bind(notPublicationMode);
        progressContainer.visibleProperty().bind(meshStatusShown.and(notPublicationMode));

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
        meshStatusShown.set(true);

        String localizedStatus = LocalizationController
                .getInstance()
                .getString(MESH_PROGRESS_DIALOG_BUNDLE, status.getLocalizedTextId());
        meshStatus.setText(localizedStatus);
    }
}
