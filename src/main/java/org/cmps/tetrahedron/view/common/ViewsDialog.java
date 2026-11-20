package org.cmps.tetrahedron.view.common;

import javafx.fxml.FXML;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;
import org.cmps.tetrahedron.controller.LocalizationController;
import org.cmps.tetrahedron.controller.MouseController;
import org.cmps.tetrahedron.enums.ViewPreset;
import org.cmps.tetrahedron.utils.DialogUtils;
import org.cmps.tetrahedron.utils.ResourceReader;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Popup with all standard views. Used instead of separate view buttons when the left toolbar doesn't fit the window.
 */
public class ViewsDialog {

    private static final LocalizationController locale = LocalizationController.getInstance();

    @FXML
    private DialogPane root;

    public static void showDialog(double x, double y) {
        Locale.setDefault(locale.getCurrentLocale());
        DialogPane pane = ResourceReader.readComponent("/view/common/ViewsDialog.fxml", DialogPane.class,
                                                       ResourceBundle.getBundle(LocalizationController.LEFT_TOOLBAR_BUNDLE));
        DialogUtils.displayDialogOnRight(pane, x, y);
    }

    @FXML
    private void clickOnFront() {
        selectView(ViewPreset.FRONT);
    }

    @FXML
    private void clickOnTop() {
        selectView(ViewPreset.TOP);
    }

    @FXML
    private void clickOnRight() {
        selectView(ViewPreset.RIGHT);
    }

    @FXML
    private void clickOnLeft() {
        selectView(ViewPreset.LEFT);
    }

    @FXML
    private void clickOnIsometric() {
        selectView(ViewPreset.ISOMETRIC);
    }

    private void selectView(ViewPreset viewPreset) {
        MouseController.getInstance().setView(viewPreset);
        Window window = root.getScene().getWindow();
        window.hide();
    }
}
