package org.cmps.tetrahedron.controller;

import lombok.Getter;
import org.cmps.tetrahedron.enums.Language;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Stream;

public class LocalizationController {

    @Getter
    private static LocalizationController instance = new LocalizationController();
    @Getter
    private Locale currentLocale;
    private final Map<String, ResourceBundle> bundles = new HashMap<>();

    public static final String RIGHT_TOOLBAR_BUNDLE = "i18n.right-toolbar";
    public static final String LEFT_TOOLBAR_BUNDLE = "i18n.left-toolbar";
    public static final String INDEX_FILE_SELECTOR_BUNDLE = "i18n.index-file-selector";
    public static final String NODE_FILE_SELECTOR_BUNDLE = "i18n.node-file-selector";
    public static final String ERROR_DIALOG_BUNDLE = "i18n.error-dialog";
    public static final String WARNING_DIALOG_BUNDLE = "i18n.warning-dialog";
    public static final String MODEL_FILES_PICKER_BUNDLE = "i18n.model-files-picker";
    public static final String STRESS_DIALOG_BUNDLE = "i18n.stress-dialog";
    public static final String DEFORMATION_DIALOG_BUNDLE = "i18n.deformation-dialog";
    public static final String VERTEX_INFO_BUNDLE = "i18n.vertex-info";
    public static final String COLOR_PICKER_BUNDLE = "i18n.color-picker";
    public static final String LEGEND_BUNDLE = "i18n.legend";
    public static final String MESH_PAGE_BUNDLE = "i18n.mesh-page";
    public static final String MESH_FILE_PICKER_BUNDLE = "i18n.mesh-file-picker";
    public static final String MESH_PROGRESS_DIALOG_BUNDLE = "i18n.mesh-progress-dialog";

    private LocalizationController() {
        Language appLanguage = Language.getLanguage(Locale.getDefault());
        currentLocale = appLanguage.getLocale();
        loadBundles();
    }

    private void loadBundles() {
        bundles.clear();
        Stream.of(RIGHT_TOOLBAR_BUNDLE, LEFT_TOOLBAR_BUNDLE, INDEX_FILE_SELECTOR_BUNDLE, NODE_FILE_SELECTOR_BUNDLE,
                  ERROR_DIALOG_BUNDLE, WARNING_DIALOG_BUNDLE, MODEL_FILES_PICKER_BUNDLE,
                  STRESS_DIALOG_BUNDLE, VERTEX_INFO_BUNDLE, COLOR_PICKER_BUNDLE, DEFORMATION_DIALOG_BUNDLE,
                  LEGEND_BUNDLE, MESH_PAGE_BUNDLE, MESH_FILE_PICKER_BUNDLE, MESH_PROGRESS_DIALOG_BUNDLE)
              .forEach(bundle -> bundles.put(bundle, ResourceBundle.getBundle(bundle, currentLocale)));
    }

    public ResourceBundle getBundle(String name) {
        return bundles.get(name);
    }

    public String getString(String bundleName, String key) {
        ResourceBundle bundle = getBundle(bundleName);
        return (bundle != null && bundle.containsKey(key)) ? bundle.getString(key) : "!" + key + "!";
    }

    public void setLocale(Locale locale) {
        currentLocale = locale;
        Locale.setDefault(locale);
        loadBundles();
    }
}
