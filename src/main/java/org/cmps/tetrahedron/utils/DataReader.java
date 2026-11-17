package org.cmps.tetrahedron.utils;

import org.cmps.tetrahedron.enums.Dimension;
import org.cmps.tetrahedron.exception.InternalValidationException;
import org.cmps.tetrahedron.exception.ModelValidationException;
import org.cmps.tetrahedron.model.CustomCharacteristic;
import org.cmps.tetrahedron.model.TetraModelApi;
import org.cmps.tetrahedron.view.common.WarningDialog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static java.util.Locale.US;

public class DataReader {

    private static final int VERTICES_WITH_INDICES = 4;
    private static final int FACE_WITH_INDICES = 5;
    private static final int STRESS_WITH_INDICES = 7;

    public static TetraModelApi readModel(File nodes, File elements,
                                          Dimension dimension) throws ModelValidationException, InternalValidationException {
        boolean is2D = dimension == Dimension.TWO_D;
        Map<Integer, float[]> coordinates = readCoordinates(nodes, is2D);
        TetraModelApi model = TetraModelApi.builder()
                                           .coordinates(coordinates)
                                           .indices(DataReader.readIndexes(elements, coordinates, is2D)
                                                              .toArray(new int[0][0]))
                                           .dimension(dimension)
                                           .build();

        if (is2D) {
            return ModelDimensionUtils.addInfoAboutZeroCoordinateOrThrowException(model);
        }
        return model;
    }

    public static Map<Integer, float[]> readStress(File stressData) throws ModelValidationException {
        Locale.setDefault(US);

        int i = 1, index, startIndex;
        // Sorted by element index: values are later displayed in iteration order, element by element.
        // HashMap does not keep that order for indices >= 65536.
        Map<Integer, float[]> stressOneElement = new TreeMap<>();

        try (Scanner fid = new Scanner(stressData)) {
            while (fid.hasNextLine()) {
                String line = fid.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] elements = line.split("\\s+");
                float[] stressValues = new float[6];

                try {
                    if (elements.length < 6 || elements.length > 7) {
                        throw new ModelValidationException("stress-format");
                    }

                    if (elements.length == STRESS_WITH_INDICES) {
                        index = Integer.parseInt(elements[0]);
                        startIndex = 1;
                    } else {
                        index = i++;
                        startIndex = 0;
                    }

                    if (stressOneElement.containsKey(index)) {
                        throw new ModelValidationException("repeating-index", "check", index);
                    }

                    for (int j = startIndex; j < elements.length; j++) {
                        stressValues[j - startIndex] = Float.parseFloat(elements[j]);
                    }
                } catch (NumberFormatException e) {
                    throw new ModelValidationException("read-number", "check-string", line);
                }

                stressOneElement.put(index, stressValues);
            }

            return stressOneElement;
        } catch (FileNotFoundException e) {
            throw new ModelValidationException("not-found-file");
        }
    }

    public static CustomCharacteristic readCustomCharacteristic(File customData) throws ModelValidationException {
        Locale.setDefault(Locale.US);

        CustomCharacteristic customModel = new CustomCharacteristic();
        List<Float> values = new ArrayList<>();
        int index = 1;

        try {
            List<String> allLines = Files.readAllLines(customData.toPath());
            if (allLines.isEmpty()) {
                throw new ModelValidationException("file-is-empty-or-inaccessible");
            }

            for (String line : allLines) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 1 || parts.length > 2) {
                    throw new ModelValidationException("characteristic-format", "check-string", line);
                }

                String token = parts.length == 1 ? parts[0] : parts[1];

                try {
                    float value = Float.parseFloat(token);

                    if (Float.isNaN(value) || Float.isInfinite(value)) {
                        throw new ModelValidationException("read-number", "check-string", line);
                    }

                    if (value < customModel.getMinValue()) {
                        customModel.setMinValue(value);
                    } else if (value > customModel.getMaxValue()) {
                        customModel.setMaxValue(value);
                    }

                    index++;
                    values.add(value);
                } catch (NumberFormatException e) {
                    throw new ModelValidationException("read-number", "check-string", line);
                }
            }

            customModel.setValues(values);
            return customModel;

        } catch (IOException e) {
            throw new ModelValidationException("not-found-file");
        }
    }

    public static Map<Integer, float[]> readCoordinates(File coordinatesTableFile, boolean is2D)
            throws ModelValidationException {
        Locale.setDefault(US);

        int i = 1;
        String line = null;
        try (Scanner fid = new Scanner(coordinatesTableFile)) {
            Map<Integer, float[]> coordinates = new HashMap<>();

            while (fid.hasNextLine()) {
                line = fid.nextLine();
                String[] elements = line.trim().split("\\s+");
                int index, startIndex;

                if (!is2D && (elements.length < 3 || elements.length > 4)) {
                    throw new ModelValidationException("vertices-count", "check-string", line);
                }
                if (is2D && (elements.length < 2 || elements.length > 3)) {
                    throw new ModelValidationException("vertices-count", "check-string", line);
                }

                if (!is2D && elements.length == VERTICES_WITH_INDICES
                        || is2D && elements.length == 3) {
                    index = Integer.parseInt(elements[0]);
                    startIndex = 1;
                } else {
                    index = i++;
                    startIndex = 0;
                }

                float[] vertex = new float[3];
                for (int j = startIndex; j < elements.length; j++) {
                    vertex[j - startIndex] = Float.parseFloat(elements[j].replace(",", "."));
                }

                coordinates.put(index, vertex);
            }

            return coordinates;
        } catch (FileNotFoundException | NumberFormatException e) {
            throw new ModelValidationException("vertices-read", "check-string", line);
        }
    }

    private static List<int[]> readIndexes(File indicesMatrix,
                                           Map<Integer, float[]> verticesCoordinates,
                                           boolean is2D)
            throws InternalValidationException, ModelValidationException {
        Locale.setDefault(US);

        Set<Integer> usedIndices = new HashSet<>();
        Set<Integer> availableVertices = verticesCoordinates.keySet();
        List<Integer> invalidIndices = new ArrayList<>();

        String line = null;
        try (Scanner fid = new Scanner(indicesMatrix)) {
            List<int[]> elements = new ArrayList<>();
            while (fid.hasNextLine()) {
                line = fid.nextLine();
                String[] element = line.trim().split("\\s+");

                if (!is2D && (element.length < 4 || element.length > 5)) {
                    throw new ModelValidationException("faces-count", "check-string", line);
                }
                if (is2D && (element.length < 3 || element.length > 4)) {
                    throw new ModelValidationException("faces-count", "check-string", line);
                }

                int[] elementIndices = new int[is2D ? 3 : 4];
                int startIndex;

                startIndex = !is2D && element.length == FACE_WITH_INDICES || is2D && element.length == 4 ? 1 : 0;
                for (int j = startIndex; j < element.length; j++) {
                    int index = Integer.parseInt(element[j]);
                    usedIndices.add(index);

                    if (!verticesCoordinates.containsKey(index)) {
                        invalidIndices.add(index);
                    } else {
                        elementIndices[j - startIndex] = index;
                    }
                }

                elements.add(elementIndices);

                if (!invalidIndices.isEmpty()) {
                    throw new ModelValidationException("faces-not-exist", invalidIndices);
                }
            }

            Set<Integer> unusedVertices = new HashSet<>(availableVertices);
            unusedVertices.removeAll(usedIndices);

            if (!unusedVertices.isEmpty()) {
                WarningDialog dialog = new WarningDialog("attention", "vertices-not-used-in-faces", "continue");
                boolean userChoice = dialog.showAndWait();

                if (!userChoice) {
                    throw new InternalValidationException("Stop flow as user decided to select another file");
                }
            }

            return elements;
        } catch (FileNotFoundException | NumberFormatException e) {
            throw new ModelValidationException("faces-read", "check-string", line);
        }
    }
}
