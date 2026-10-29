package org.cmps.tetrahedron.utils;

import org.cmps.tetrahedron.enums.Dimension;
import org.cmps.tetrahedron.model.TetraModelApi;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DataWriter {

    private static final String CONSTANTS_FILE = "0_constants.txt";
    private static final String COORDINATES_FILE = "1_coordinates_matrix.txt";
    private static final String INDICES_FILE = "2_indices_matrix.txt";
    private static final String LAYER_IDS_FILE = "3_layer_ids.txt";

    public static void writeConstantsToFile(File directory, TetraModelApi tetraModelApi) throws IOException {
        File file = FileUtils.createFile(directory, CONSTANTS_FILE);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(String.format("FiniteElementNodesCount=%d", tetraModelApi.indices()[0].length));
            writer.newLine();
            writer.write(String.format("NodesCount=%d", tetraModelApi.coordinates().size()));
            writer.newLine();
            writer.write(String.format("ElementsCount=%d", tetraModelApi.indices().length));
            writer.newLine();
        }
    }

    public static void writeCoordinatesToFile(File directory, TetraModelApi tetraModelApi,
                                              List<Integer> sortedNodeIndices) throws IOException {
        File file = FileUtils.createFile(directory, COORDINATES_FILE);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            var coordinates = tetraModelApi.coordinates();

            for (int i = 0; i < sortedNodeIndices.size(); i++) {
                int originalIndex = sortedNodeIndices.get(i);

                writer.write(String.format("%7d ", i + 1));

                for (int j = 0; j < 3; j++) {
                    if (tetraModelApi.dimension() == Dimension.TWO_D && tetraModelApi.zeroCoordinateIndex() == j) {
                        continue;
                    }
                    writer.write(String.format("%7.12E ", coordinates.get(originalIndex)[j]));
                }

                writer.newLine();
            }
        }
    }

    public static void writeIndicesToFile(File directory, TetraModelApi tetraModelApi,
                                          Map<Integer, Integer> coordIndexToSortedIndex) throws IOException {
        File file = FileUtils.createFile(directory, INDICES_FILE);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            var elements = tetraModelApi.indices();

            for (int i = 0; i < elements.length; i++) {
                writer.write(String.format("%7d ", i + 1));

                int[] element = elements[i];
                writer.write(String.format("%7d ", coordIndexToSortedIndex.get(element[0])));
                writer.write(String.format("%7d ", coordIndexToSortedIndex.get(element[1])));
                writer.write(String.format("%7d ", coordIndexToSortedIndex.get(element[2])));
                if (element.length == 4) {
                    writer.write(String.format("%7d ", coordIndexToSortedIndex.get(element[3])));
                }

                writer.newLine();
            }
        }
    }

    public static void writeElementLayersToFile(File directory, TetraModelApi tetraModelApi) throws IOException {
        if (tetraModelApi.elementLayers() == null) {
            return;
        }
        File file = FileUtils.createFile(directory, LAYER_IDS_FILE);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            int[] layers = tetraModelApi.elementLayers();
            for (int i = 1; i <= layers.length; i++) {
                writer.write(String.format("%7d %7d", i + 1, layers[i]));
                writer.newLine();
            }
        }
    }
}
