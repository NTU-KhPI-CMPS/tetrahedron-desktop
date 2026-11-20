package org.cmps.tetrahedron.controller;

import lombok.Getter;
import lombok.Setter;
import org.cmps.tetrahedron.enums.Dimension;
import org.cmps.tetrahedron.exception.ModelValidationException;
import org.cmps.tetrahedron.model.ColorSettings;
import org.cmps.tetrahedron.model.CustomCharacteristic;
import org.cmps.tetrahedron.model.Model;
import org.cmps.tetrahedron.model.TetraModelApi;
import org.cmps.tetrahedron.utils.DataReader;
import org.cmps.tetrahedron.viewmodel.LegendData;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class ModelController {

    @Getter
    private static final ModelController instance = new ModelController();

    private Model model;
    @Setter
    private boolean modelReady = false;
    @Getter
    private CustomCharacteristic customCharacteristic;
    @Getter
    private List<Float> valuesToDisplay = null;

    private ModelController() {
        model = Model.builder()
                     .vertices(new HashMap<>())
                     .faces(new ArrayList<>())
                     .dimension(Dimension.THREE_D)
                     .build();
    }

    public void initModelData(TetraModelApi tetraModelApi) {
        Map<Integer, float[]> coordinates = tetraModelApi.coordinates();

        List<float[][]> faces = new ArrayList<>();

        int[][] indices = tetraModelApi.indices();
        for (int[] index : indices) {
            float[] vertex1 = coordinates.get(index[0]);
            float[] vertex2 = coordinates.get(index[1]);
            float[] vertex3 = coordinates.get(index[2]);
            faces.add(new float[][]{vertex1, vertex2, vertex3});
            if (index.length == 4) {
                float[] vertex4 = coordinates.get(index[3]);
                faces.add(new float[][]{vertex1, vertex2, vertex4});
                faces.add(new float[][]{vertex1, vertex4, vertex3});
                faces.add(new float[][]{vertex4, vertex2, vertex3});
            }
        }

        model = Model.builder()
                     .vertices(coordinates)
                     .faces(faces)
                     .dimension(tetraModelApi.dimension())
                     .build();
        modelReady = true;
    }

    public boolean hasModel() {
        return !model.getFaces().isEmpty();
    }

    public List<float[][]> getFaces() {
        return model.getFaces();
    }

    public Map<Integer, float[]> getVertices() {
        if (model == null) {
            throw new RuntimeException("Model is not initialized");
        }
        return model.getVertices();
    }

    public void initCustomCharacteristic(File customDataFile) throws ModelValidationException {
        customCharacteristic = DataReader.readCustomCharacteristic(customDataFile);
        LegendData.getInstance()
                  .updateValuesRange(customCharacteristic.getMinValue(), customCharacteristic.getMaxValue());
        setValuesToDisplay(customCharacteristic.getValues());
    }

    public void setValuesToDisplay(List<Float> valuesToDisplay) {
        this.valuesToDisplay = valuesToDisplay;

        ColorSettings.getInstance().setColoredInSelectedColor(false);
        modelReady = true;

        LegendData.getInstance().getItems().addListener((e1, e2, e3) -> {
            modelReady = true;
        });
    }

    public void clearModel() {
        valuesToDisplay = null;
        modelReady = true;
        model.clear();

        ColorSettings.getInstance().setColoredInSelectedColor(true);
        LegendData.getInstance().resetLegend();
    }

    public void clearDisplacement() {
        model.updateVertices(model.getOriginalVertices());
        modelReady = true;
    }
}
