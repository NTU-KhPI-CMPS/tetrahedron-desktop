package org.cmps.tetrahedron.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Data;
import lombok.Getter;

@Data
public class ModelViewSettings {

    public static final float[] PUBLICATION_BACKGROUND_COLOR = new float[] { 1f, 1f, 1f };

    @Getter
    private static final ModelViewSettings instance = new ModelViewSettings();

    private boolean showElementMesh = true;
    private boolean showLight = true;

    /**
     * Publication mode is used to make screenshots for articles: white background and enlarged legend.
     */
    private final BooleanProperty publicationMode = new SimpleBooleanProperty(false);

    public boolean isPublicationMode() {
        return publicationMode.get();
    }

    public void setPublicationMode(boolean value) {
        publicationMode.set(value);
    }
}
