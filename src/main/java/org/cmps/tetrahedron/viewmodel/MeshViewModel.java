package org.cmps.tetrahedron.viewmodel;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import lombok.Getter;
import lombok.Setter;
import org.cmps.tetrahedron.controller.ModelController;
import org.cmps.tetrahedron.enums.MeshStatus;
import org.cmps.tetrahedron.exception.ModelValidationException;
import org.cmps.tetrahedron.mesher.StlToTetraMesh;
import org.cmps.tetrahedron.model.TetraModelApi;
import org.cmps.tetrahedron.utils.DataWriter;
import org.cmps.tetrahedron.utils.ModelDimensionUtils;
import org.cmps.tetrahedron.view.common.ErrorDialog;
import org.cmps.tetrahedron.view.mesh.MeshProgressDialog;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MeshViewModel {

    @Getter
    private static final MeshViewModel instance = new MeshViewModel();

    private final ModelController modelController = ModelController.getInstance();
    private final DimensionViewModel dimensionViewModel = DimensionViewModel.getInstance();

    @Getter
    private final StringProperty stlFileName = new SimpleStringProperty();

    @Getter
    @Setter
    private boolean multiLayerMode = false;

    @Getter
    private final javafx.beans.property.DoubleProperty tolerance = new javafx.beans.property.SimpleDoubleProperty(1e-5);

    @Getter
    private final StringProperty minMeshSize = new SimpleStringProperty("-");
    @Getter
    private final StringProperty maxMeshSize = new SimpleStringProperty("-");
    @Getter
    private final StringProperty angle = new SimpleStringProperty("40");

    @Getter
    private final StringProperty nodesCount = new SimpleStringProperty("-");
    @Getter
    private final StringProperty elementsCount = new SimpleStringProperty("-");

    @Getter
    private final SimpleObjectProperty<MeshStatus> meshStatus = new SimpleObjectProperty<>();

    private TetraModelApi tetraModelApi;
    private Thread meshThread;
    @Setter
    private MeshProgressDialog meshProgressDialog;
    @Getter
    private StringJoiner mesherLogs;

    public void displayStlModel(String filePath) throws ModelValidationException {
        try {
            boolean isStep = filePath.toLowerCase().endsWith(".step") || filePath.toLowerCase().endsWith(".stp");
            TetraModelApi model;
            if (isStep) {
                model = StlToTetraMesh.extractMultiLayerStepData(filePath, null);
            } else {
                model = StlToTetraMesh.extractStlData(filePath, null);
            }
            if (dimensionViewModel.is2D()) {
                model = ModelDimensionUtils.addInfoAboutZeroCoordinateOrThrowException(model);
            }
            tetraModelApi = model;
            stlFileName.set(filePath);

            nodesCount.set(String.valueOf(tetraModelApi.coordinates().size()));
            elementsCount.set("-");
            minMeshSize.set(String.format("%.5f", tetraModelApi.minMeshSize()));
            maxMeshSize.set(String.format("%.5f", tetraModelApi.maxMeshSize()));
            modelController.initModelData(tetraModelApi);
        } catch (Throwable e) {
            throw new ModelValidationException("mesh-error", e.getMessage());
        }
    }

    public void meshModel() {
        meshStatus.set(MeshStatus.IN_PROGRESS);

        meshProgressDialog = MeshProgressDialog.openDialogWindow();
        mesherLogs = new StringJoiner(System.lineSeparator());

        meshThread = new Thread(new MeshTask());
        meshThread.start();
    }

    public void saveModel(File directory) {
        var sortedNodeIndices = sortCoordinates();
        Map<Integer, Integer> indexToSortedIndex = new HashMap<>();
        for (int i = 0; i < sortedNodeIndices.size(); i++) {
            indexToSortedIndex.put(sortedNodeIndices.get(i), i + 1);
        }

        try {
            DataWriter.writeConstantsToFile(directory, tetraModelApi);
            DataWriter.writeCoordinatesToFile(directory, tetraModelApi, sortedNodeIndices);
            DataWriter.writeIndicesToFile(directory, tetraModelApi, indexToSortedIndex);

            if (tetraModelApi.elementLayers() != null) {
                DataWriter.writeElementLayersToFile(directory, tetraModelApi);
            }
        } catch (IOException | RuntimeException e) {
            new ErrorDialog(new ModelValidationException("save-mesh-error", e.getMessage()));
        }
    }

    private List<Integer> sortCoordinates() {
        return tetraModelApi
                .coordinates()
                .entrySet()
                .stream()
                .sorted(Comparator.comparingDouble((Map.Entry<Integer, float[]> entry) -> entry.getValue()[0])
                                  .thenComparingDouble(entry -> entry.getValue()[1])
                                  .thenComparingDouble(entry -> entry.getValue()[2]))
                .map(Map.Entry::getKey)
                .toList();
    }

    private class MeshTask extends Task<Void> {

        private MeshTask() {
            this.setOnFailed(_ -> {
                meshStatus.set(MeshStatus.FAILURE);
            });
        }

        @Override
        protected Void call() {
            double processedMinMesh = Double.parseDouble(minMeshSize.getValue().replace(",", "."));
            double processedMaxMesh = Double.parseDouble(maxMeshSize.getValue().replace(",", "."));
            double processedAngle = Double.parseDouble(angle.getValue().replace(",", "."));

            String fileName = stlFileName.get();
            boolean isStep = fileName != null
                    && (fileName.toLowerCase().endsWith(".step")
                    || fileName.toLowerCase().endsWith(".stp"));

            if (isStep) {
                tetraModelApi = StlToTetraMesh.generateStepMultiLayerMesh(List.of(fileName), processedMinMesh,
                                                                          processedMaxMesh, processedAngle,
                                                                          this::appendLog);
            } else {
                if (dimensionViewModel.is2D()) {
                    tetraModelApi = StlToTetraMesh.generate2dMesh(fileName, processedMinMesh, processedMaxMesh,
                                                                  processedAngle, this::appendLog);
                } else {
                    tetraModelApi = StlToTetraMesh.generateMesh(fileName, processedMinMesh, processedMaxMesh,
                                                                processedAngle, this::appendLog);
                }
            }

            Platform.runLater(() -> {
                meshStatus.set(MeshStatus.SUCCESS);
                modelController.clearModel();
                modelController.initModelData(tetraModelApi);
                
                if (tetraModelApi.elementLayers() != null) {
                    int[] layers = tetraModelApi.elementLayers();
                    int min = Integer.MAX_VALUE;
                    int max = Integer.MIN_VALUE;
                    List<Float> values = new ArrayList<>(layers.length);
                    for (int layer : layers) {
                        if (layer < min) min = layer;
                        if (layer > max) max = layer;
                        values.add((float) layer);
                    }
                    LegendData.getInstance().updateValuesRange(min, max);
                    modelController.setValuesToDisplay(values);
                }

                nodesCount.set(String.valueOf(tetraModelApi.coordinates().size()));
                elementsCount.set(String.valueOf(tetraModelApi.indices().length));
            });
            return null;
        }

        private void appendLog(String log) {
            mesherLogs.add(log);
            Platform.runLater(() -> meshProgressDialog.appendLog(log));
        }
    }
}
