package org.cmps.tetrahedron.view.model;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.scene.Group;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.*;
import org.cmps.tetrahedron.controller.ModelController;
import org.cmps.tetrahedron.enums.Dimension;
import org.cmps.tetrahedron.exception.InternalValidationException;
import org.cmps.tetrahedron.exception.ModelValidationException;
import org.cmps.tetrahedron.graphics.ModelView;
import org.cmps.tetrahedron.model.ModelViewSettings;
import org.cmps.tetrahedron.model.TetraModelApi;
import org.cmps.tetrahedron.utils.DataReader;
import org.cmps.tetrahedron.utils.ResourceReader;
import org.cmps.tetrahedron.view.model.component.InfoPanel;
import org.cmps.tetrahedron.view.model.component.Legend;

import java.io.File;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Page used to display model and calculation results (ex. stress, deformation, etc.)
 *
 * @author Mariia Borodin (HappyMary16)
 * @since 1.0
 */
public class ModelPage extends StackPane {

    private static final double PUBLICATION_LEGEND_SCALE = 3.0;

    public ModelPage() {
        HBox navbar = ResourceReader.readComponent("/view/common/Navbar.fxml", HBox.class);

        VBox instrumentSidebar = ResourceReader.readComponent("/view/model/component/LeftToolBar.fxml", VBox.class);
        Legend legend = new Legend();
        Group legendGroup = new Group(legend);
        Pane pane = new Pane();
        HBox.setHgrow(pane, Priority.ALWAYS);
        VBox rightToolbar = ResourceReader.readComponent("/view/model/component/RightToolbar.fxml", VBox.class,
                                                         ResourceBundle.getBundle("i18n.right-toolbar"));

        HBox mainBlock = new HBox(30, instrumentSidebar, legendGroup, pane, rightToolbar);
        mainBlock.setPadding(new Insets(0, 15, 0, 15));
        mainBlock.setAlignment(Pos.CENTER);
        mainBlock.setFillHeight(false);
        VBox.setVgrow(mainBlock, Priority.ALWAYS);

        InfoPanel infoPanel = InfoPanel.getInstance();
        infoPanel.setPadding(new Insets(0, 0, 54, 0));
        // hide "Selected node" info and logo on screenshots for articles
        BooleanBinding notPublicationMode = ModelViewSettings.getInstance().getPublicationMode().not();
        infoPanel.visibleProperty().bind(notPublicationMode);
        navbar.lookup("#logo").visibleProperty().bind(notPublicationMode);

        bindLegendScale(legend, mainBlock);

        VBox controls = new VBox(navbar, mainBlock, infoPanel);
        controls.getStyleClass().add("model-view-page");

        this.getChildren().addAll(new ModelView(), controls);

        if (Objects.equals(System.getProperty("debug"), "true")) {
            initModelIfInDebug();
        } else if (!ModelController.getInstance().hasModel()) {
            // page is also rebuilt on language change; don't ask for files again when a model is already shown
            Platform.runLater(ModelFilesPickerDialog::openDialogWindow);
        }
    }

    /**
     * Enlarges legend in publication mode, so it is readable on screenshots for articles.
     * Scale is limited by available height to keep the whole legend visible.
     */
    private void bindLegendScale(Legend legend, HBox mainBlock) {
        BooleanProperty publicationMode = ModelViewSettings.getInstance().getPublicationMode();
        DoubleBinding scale = Bindings.createDoubleBinding(() -> {
            if (!publicationMode.get()) {
                return 1.0;
            }
            double legendHeight = legend.getLayoutBounds().getHeight();
            double availableHeight = mainBlock.getHeight();
            if (legendHeight <= 0 || availableHeight <= 0) {
                return PUBLICATION_LEGEND_SCALE;
            }
            return Math.max(1.0, Math.min(PUBLICATION_LEGEND_SCALE, availableHeight / legendHeight));
        }, publicationMode, legend.layoutBoundsProperty(), mainBlock.heightProperty());

        legend.scaleXProperty().bind(scale);
        legend.scaleYProperty().bind(scale);
    }

    private void initModelIfInDebug() {
        try {
            TetraModelApi model = DataReader.readModel(new File("models/Vertices (model 1).txt"),
                                                       new File("models/Indices (model 1).txt"),
                                                       Dimension.THREE_D);
            ModelController.getInstance().initModelData(model);
        } catch (ModelValidationException | InternalValidationException e) {
            throw new RuntimeException(e);
        }
    }
}
