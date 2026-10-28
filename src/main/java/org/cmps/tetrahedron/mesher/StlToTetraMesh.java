package org.cmps.tetrahedron.mesher;

import javafx.util.Pair;
import org.cmps.tetrahedron.controller.LocalizationController;
import org.cmps.tetrahedron.enums.Dimension;
import org.cmps.tetrahedron.model.TetraModelApi;
import org.gmsh.Gmsh;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.function.Consumer;

import static java.lang.foreign.ValueLayout.*;

public class StlToTetraMesh {

    private record Edge(long u, long v) {
        public Edge {
            if (u > v) {
                long temp = u;
                u = v;
                v = temp;
            }
        }
    }

    private static class Cluster {
        double val;
        int count;

        Cluster(double val, int count) {
            this.val = val;
            this.count = count;
        }
    }

    private record StlTrianglesData(List<long[]> triangles, List<double[]> normals) {
    }

    private record LayerStructure(boolean useLayered, int bestAxis, List<Double> bestPlanes) {
    }

    public static TetraModelApi generateMesh(String inputStl, double minMeshSize, double maxMeshSize,
                                             double angleToFindSurfaces, Consumer<String> logger) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ierr = arena.allocate(JAVA_INT);
            gmshInit(arena, ierr, logger);
            Thread logPoller = startGmshLoggerPoller(ierr, logger);

            try {
                loadStl(arena, inputStl, ierr, logger);
                Map<Long, double[]> nodeCoords = getRawStlNodes(arena, ierr);
                StlTrianglesData triData = getRawStlTriangles(arena, ierr, nodeCoords);

                List<Edge> nonManifoldEdges = detectNonManifoldEdges(triData.triangles());
                LayerStructure layerData = analyzeLayerStructure(nonManifoldEdges, nodeCoords, logger);

                if (layerData.useLayered()) {
                    List<List<Integer>> shells = partitionIntoShells(triData.triangles(), nodeCoords, triData.normals(),
                                                                     layerData, logger);
                    rebuildDiscreteModel(arena, ierr, shells, triData.triangles(), nodeCoords, minMeshSize, maxMeshSize,
                                         logger);
                } else {
                    runCadRemeshing(arena, ierr, inputStl, nodeCoords, minMeshSize, maxMeshSize, angleToFindSurfaces,
                                    logger);
                }

                Map<Integer, float[]> finalCoords = exportNodes(arena, ierr, logger);
                int[][] finalIndices = exportTetrahedrons(arena, ierr);

                if (logger != null) logger.accept(
                        String.format("Finished successfully! Nodes: %d, Elements: %d\n", finalCoords.size(),
                                      finalIndices.length));

                return TetraModelApi.builder()
                                    .coordinates(finalCoords)
                                    .indices(finalIndices)
                                    .dimension(Dimension.THREE_D)
                                    .build();

            } finally {
                logPoller.interrupt();
                try {
                    logPoller.join(1000);
                } catch (InterruptedException ignored) {
                }
                Gmsh.gmshLoggerStop(ierr);
                Gmsh.gmshFinalize(ierr);
            }
        }
    }

    public static TetraModelApi generate2dMesh(String inputStl, double minMeshSize, double maxMeshSize,
                                               double angleToFindSurfaces, Consumer<String> logger) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ierr = arena.allocate(JAVA_INT);
            gmshInit(arena, ierr, logger);
            Thread logPoller = startGmshLoggerPoller(ierr, logger);

            try {
                loadStl(arena, inputStl, ierr, logger);
                runCadRemeshing2d(arena, ierr, inputStl, minMeshSize, maxMeshSize, angleToFindSurfaces, logger);

                Map<Integer, float[]> finalCoords = exportNodes(arena, ierr, logger);
                int[][] finalIndices = exportTriangles(arena, ierr, logger);

                if (logger != null) logger.accept(
                        String.format("Finished successfully! Nodes: %d, Elements: %d\n", finalCoords.size(),
                                      finalIndices.length));

                return TetraModelApi.builder()
                                    .coordinates(finalCoords)
                                    .indices(finalIndices)
                                    .dimension(Dimension.TWO_D)
                                    .zeroCoordinateIndex(2)
                                    .build();

            } finally {
                logPoller.interrupt();
                try {
                    logPoller.join(1000);
                } catch (InterruptedException ignored) {
                }
                Gmsh.gmshLoggerStop(ierr);
                Gmsh.gmshFinalize(ierr);
            }
        }
    }

    public static TetraModelApi extractStlData(String inputStl, Consumer<String> logger) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ierr = arena.allocate(JAVA_INT);
            gmshInit(arena, ierr, logger);
            Thread logPoller = startGmshLoggerPoller(ierr, logger);

            try {
                loadStl(arena, inputStl, ierr, logger);
                Map<Integer, float[]> coordinates = exportNodes(arena, ierr, logger);
                int[][] indices = exportTriangles(arena, ierr, logger);
                Pair<Double, Double> minMaxMeshSize = recommendedMeshSize(coordinates);
                return TetraModelApi.builder()
                                    .coordinates(coordinates)
                                    .indices(indices)
                                    .minMeshSize(minMaxMeshSize.getKey())
                                    .maxMeshSize(minMaxMeshSize.getValue())
                                    .build();
            } finally {
                logPoller.interrupt();
                try {
                    logPoller.join(1000);
                } catch (InterruptedException ignored) {
                }
                Gmsh.gmshLoggerStop(ierr);
                Gmsh.gmshFinalize(ierr);
            }
        }
    }

    public static TetraModelApi extractMultiLayerStepData(String inputStepFile, Consumer<String> logger) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ierr = arena.allocate(JAVA_INT);
            gmshInit(arena, ierr, logger);
            Thread logPoller = startGmshLoggerPoller(ierr, logger);

            try {
                Gmsh.gmshModelAdd(arena.allocateFrom("MultiLayer_Preview"), ierr);
                boolean hasStep = false;
                if (logger != null) logger.accept("Loading file: " + inputStepFile);
                Gmsh.gmshMerge(arena.allocateFrom(inputStepFile), ierr);
                if (ierr.get(JAVA_INT, 0) != 0) {
                    throw new RuntimeException("error-loading-file");
                }
                if (inputStepFile.toLowerCase().endsWith(".step") || inputStepFile.toLowerCase().endsWith(".stp")) {
                    hasStep = true;
                }

                if (hasStep) {
                    Gmsh.gmshModelOccSynchronize(ierr);
                    Gmsh.gmshModelMeshGenerate(2, ierr);
                }

                Map<Integer, float[]> coordinates = exportNodes(arena, ierr, logger);
                int[][] indices = exportTriangles(arena, ierr, logger);
                Pair<Double, Double> minMaxMeshSize = recommendedMeshSize(coordinates);
                return TetraModelApi.builder()
                                    .coordinates(coordinates)
                                    .indices(indices)
                                    .minMeshSize(minMaxMeshSize.getKey())
                                    .maxMeshSize(minMaxMeshSize.getValue())
                                    .build();
            } finally {
                logPoller.interrupt();
                try {
                    logPoller.join(1000);
                } catch (InterruptedException ignored) {
                }
                Gmsh.gmshLoggerStop(ierr);
                Gmsh.gmshFinalize(ierr);
            }
        }
    }

    private static Pair<Double, Double> recommendedMeshSize(Map<Integer, float[]> coordinates) {
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

        for (float[] coord : coordinates.values()) {
            minX = Math.min(minX, coord[0]);
            maxX = Math.max(maxX, coord[0]);
            minY = Math.min(minY, coord[1]);
            maxY = Math.max(maxY, coord[1]);
            minZ = Math.min(minZ, coord[2]);
            maxZ = Math.max(maxZ, coord[2]);
        }
        double dx = maxX - minX;
        double dy = maxY - minY;
        double dz = maxZ - minZ;
        double maxDim = Math.max(dx, Math.max(dy, dz));
        if (Double.isInfinite(maxDim) || maxDim <= 0) {
            maxDim = 100.0;
        }

        return new Pair<>(maxDim * 0.05 * 0.3, maxDim * 0.05 * 1.2);
    }

    private static void gmshInit(Arena arena, MemorySegment ierr, Consumer<String> logger) {
        if (logger != null) logger.accept("Step 1: gmshInitialize");
        MemorySegment arg0 = arena.allocateFrom("gmsh");
        MemorySegment arg1 = arena.allocateFrom("-no_signal_handler");
        MemorySegment argv = arena.allocate(ADDRESS, 3);
        argv.setAtIndex(ADDRESS, 0, arg0);
        argv.setAtIndex(ADDRESS, 1, arg1);
        argv.setAtIndex(ADDRESS, 2, MemorySegment.NULL);
        Gmsh.gmshInitialize(2, argv, 0, 0, ierr);
        if (ierr.get(JAVA_INT, 0) != 0) {
            System.err.println("gmshInitialize failed");
            throw new RuntimeException("gmshInitialize-failed");
        }
    }

    private static Thread startGmshLoggerPoller(MemorySegment mainIerr, Consumer<String> logger) {
        Gmsh.gmshLoggerStart(mainIerr);
        Thread logPoller = new Thread(() -> {
            long lastLogIndex = 0;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    try (Arena logArena = Arena.ofConfined()) {
                        MemorySegment logIerr = logArena.allocate(JAVA_INT);
                        MemorySegment logPtr = logArena.allocate(ADDRESS);
                        MemorySegment logNPtr = logArena.allocate(JAVA_LONG);

                        Gmsh.gmshLoggerGet(logPtr, logNPtr, logIerr);

                        long n = logNPtr.get(JAVA_LONG, 0);
                        MemorySegment strArray = logPtr.get(ADDRESS, 0);
                        if (strArray != MemorySegment.NULL && n > 0) {
                            MemorySegment strArrayReinterpreted = strArray.reinterpret(n * 8);
                            for (long i = lastLogIndex; i < n; i++) {
                                MemorySegment strPtr = strArrayReinterpreted.getAtIndex(ADDRESS, i);
                                if (strPtr != MemorySegment.NULL) {
                                    String msg = strPtr.reinterpret(Integer.MAX_VALUE).getString(0);
                                    if (logger != null) {
                                        logger.accept("Gmsh: " + msg);
                                    }
                                }
                            }
                            lastLogIndex = n;

                            for (long i = 0; i < n; i++) {
                                MemorySegment strPtr = strArrayReinterpreted.getAtIndex(ADDRESS, i);
                                if (strPtr != MemorySegment.NULL) {
                                    Gmsh.gmshFree(strPtr);
                                }
                            }
                            Gmsh.gmshFree(strArray);
                        }
                    }
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        logPoller.setDaemon(true);
        logPoller.start();
        return logPoller;
    }

    private static void loadStl(Arena arena, String inputStl, MemorySegment ierr, Consumer<String> logger) {
        if (logger != null) logger.accept("Loading STL: " + inputStl);
        Gmsh.gmshModelAdd(arena.allocateFrom("STL_Loader"), ierr);
        Gmsh.gmshMerge(arena.allocateFrom(inputStl), ierr);
        if (ierr.get(JAVA_INT, 0) != 0) {
            System.err.println("Error loading STL");
            throw new RuntimeException("error-loading-stl");
        }
    }

    private static Map<Long, double[]> getRawStlNodes(Arena arena, MemorySegment ierr) {
        MemorySegment nodeTagsPtr = arena.allocate(ADDRESS);
        MemorySegment nodeTags_n = arena.allocate(JAVA_LONG);
        MemorySegment coordsPtr = arena.allocate(ADDRESS);
        MemorySegment coords_n = arena.allocate(JAVA_LONG);
        MemorySegment parametricCoordPtr = arena.allocate(ADDRESS);
        MemorySegment parametricCoord_n = arena.allocate(JAVA_LONG);

        Gmsh.gmshModelMeshGetNodes(nodeTagsPtr, nodeTags_n, coordsPtr, coords_n, parametricCoordPtr, parametricCoord_n,
                                   -1, -1, 0, 0, ierr);

        long numNodes = nodeTags_n.get(JAVA_LONG, 0);
        MemorySegment nodeTagsArray = nodeTagsPtr.get(ADDRESS, 0).reinterpret(numNodes * 8);
        MemorySegment coordsArray = coordsPtr.get(ADDRESS, 0).reinterpret(numNodes * 3 * 8);

        Map<Long, double[]> nodeCoords = new HashMap<>();
        for (long i = 0; i < numNodes; i++) {
            long tag = nodeTagsArray.getAtIndex(JAVA_LONG, i);
            double x = coordsArray.getAtIndex(JAVA_DOUBLE, i * 3);
            double y = coordsArray.getAtIndex(JAVA_DOUBLE, i * 3 + 1);
            double z = coordsArray.getAtIndex(JAVA_DOUBLE, i * 3 + 2);
            nodeCoords.put(tag, new double[]{x, y, z});
        }

        Gmsh.gmshFree(nodeTagsPtr.get(ADDRESS, 0));
        Gmsh.gmshFree(coordsPtr.get(ADDRESS, 0));
        Gmsh.gmshFree(parametricCoordPtr.get(ADDRESS, 0));

        return nodeCoords;
    }

    private static StlTrianglesData getRawStlTriangles(Arena arena, MemorySegment ierr,
                                                       Map<Long, double[]> nodeCoords) {
        MemorySegment elementTypePtr = arena.allocate(ADDRESS);
        MemorySegment elementType_n = arena.allocate(JAVA_LONG);
        MemorySegment elementTagsPtr = arena.allocate(ADDRESS);
        MemorySegment elementTags_n = arena.allocate(ADDRESS);
        MemorySegment elementTags_nn = arena.allocate(JAVA_LONG);
        MemorySegment elementNodeTagsPtr = arena.allocate(ADDRESS);
        MemorySegment elementNodeTags_n = arena.allocate(ADDRESS);
        MemorySegment elementNodeTags_nn = arena.allocate(JAVA_LONG);

        Gmsh.gmshModelMeshGetElements(elementTypePtr, elementType_n, elementTagsPtr, elementTags_n, elementTags_nn,
                                      elementNodeTagsPtr, elementNodeTags_n, elementNodeTags_nn, 2, -1, ierr);

        long numTypes = elementType_n.get(JAVA_LONG, 0);
        MemorySegment elemTypesArray = elementTypePtr.get(ADDRESS, 0).reinterpret(numTypes * 4);
        MemorySegment elemTagsPtrArray = elementTagsPtr.get(ADDRESS, 0).reinterpret(numTypes * 8);
        MemorySegment elemTags_nArray = elementTags_n.get(ADDRESS, 0).reinterpret(numTypes * 8);
        MemorySegment nodeTagsPerElemPtrArray = elementNodeTagsPtr.get(ADDRESS, 0).reinterpret(numTypes * 8);

        List<long[]> triangles = new ArrayList<>();
        List<double[]> triangleNormals = new ArrayList<>();

        for (long i = 0; i < numTypes; i++) {
            int eType = elemTypesArray.getAtIndex(JAVA_INT, i);
            if (eType == 2) { // Triangle
                long nElems = elemTags_nArray.getAtIndex(JAVA_LONG, i);
                MemorySegment conns = nodeTagsPerElemPtrArray.getAtIndex(ADDRESS, i).reinterpret(nElems * 3 * 8);
                for (long j = 0; j < nElems; j++) {
                    long n1 = conns.getAtIndex(JAVA_LONG, j * 3);
                    long n2 = conns.getAtIndex(JAVA_LONG, j * 3 + 1);
                    long n3 = conns.getAtIndex(JAVA_LONG, j * 3 + 2);
                    triangles.add(new long[]{n1, n2, n3});

                    // Calculate normal
                    double[] c1 = nodeCoords.get(n1);
                    double[] c2 = nodeCoords.get(n2);
                    double[] c3 = nodeCoords.get(n3);
                    if (c1 != null && c2 != null && c3 != null) {
                        double ux = c2[0] - c1[0];
                        double uy = c2[1] - c1[1];
                        double uz = c2[2] - c1[2];
                        double vx = c3[0] - c1[0];
                        double vy = c3[1] - c1[1];
                        double vz = c3[2] - c1[2];
                        double nx = uy * vz - uz * vy;
                        double ny = uz * vx - ux * vz;
                        double nz = ux * vy - uy * vx;
                        triangleNormals.add(new double[]{nx, ny, nz});
                    } else {
                        triangleNormals.add(new double[]{0.0, 0.0, 0.0});
                    }
                }
            }
        }

        for (long i = 0; i < numTypes; i++) {
            Gmsh.gmshFree(elemTagsPtrArray.getAtIndex(ADDRESS, i));
            Gmsh.gmshFree(nodeTagsPerElemPtrArray.getAtIndex(ADDRESS, i));
        }
        Gmsh.gmshFree(elementTypePtr.get(ADDRESS, 0));
        Gmsh.gmshFree(elementTagsPtr.get(ADDRESS, 0));
        Gmsh.gmshFree(elementTags_n.get(ADDRESS, 0));
        Gmsh.gmshFree(elementNodeTagsPtr.get(ADDRESS, 0));
        Gmsh.gmshFree(elementNodeTags_n.get(ADDRESS, 0));

        return new StlTrianglesData(triangles, triangleNormals);
    }

    private static List<Edge> detectNonManifoldEdges(List<long[]> triangles) {
        Map<Edge, List<Integer>> edgeToTriangles = new HashMap<>();
        for (int idx = 0; idx < triangles.size(); idx++) {
            long[] tri = triangles.get(idx);
            Edge e1 = new Edge(tri[0], tri[1]);
            Edge e2 = new Edge(tri[1], tri[2]);
            Edge e3 = new Edge(tri[2], tri[0]);
            edgeToTriangles.computeIfAbsent(e1, k -> new ArrayList<>()).add(idx);
            edgeToTriangles.computeIfAbsent(e2, k -> new ArrayList<>()).add(idx);
            edgeToTriangles.computeIfAbsent(e3, k -> new ArrayList<>()).add(idx);
        }

        List<Edge> nonManifoldEdges = new ArrayList<>();
        for (Map.Entry<Edge, List<Integer>> entry : edgeToTriangles.entrySet()) {
            if (entry.getValue().size() > 2) {
                nonManifoldEdges.add(entry.getKey());
            }
        }
        return nonManifoldEdges;
    }

    private static LayerStructure analyzeLayerStructure(List<Edge> nonManifoldEdges, Map<Long, double[]> nodeCoords,
                                                        Consumer<String> logger) {
        if (nonManifoldEdges.isEmpty()) {
            if (logger != null) logger.accept("No non-manifold edges found. The model is a single watertight solid.");
            return new LayerStructure(false, -1, Collections.emptyList());
        }

        if (logger != null)
            logger.accept("Found " + nonManifoldEdges.size() + " non-manifold edges. Analyzing layer structure...");
        double[][] mids = new double[nonManifoldEdges.size()][3];
        for (int i = 0; i < nonManifoldEdges.size(); i++) {
            Edge edge = nonManifoldEdges.get(i);
            double[] c1 = nodeCoords.get(edge.u);
            double[] c2 = nodeCoords.get(edge.v);
            if (c1 != null && c2 != null) {
                mids[i][0] = (c1[0] + c2[0]) / 2.0;
                mids[i][1] = (c1[1] + c2[1]) / 2.0;
                mids[i][2] = (c1[2] + c2[2]) / 2.0;
            }
        }

        double maxClustered = 0;
        int bestAxis = -1;
        List<Double> bestPlanes = new ArrayList<>();

        for (int axis = 0; axis < 3; axis++) {
            List<Double> coords = new ArrayList<>();
            for (double[] mid : mids) {
                coords.add(mid[axis]);
            }

            List<Cluster> clusters = new ArrayList<>();
            for (double c : coords) {
                boolean found = false;
                for (Cluster cluster : clusters) {
                    if (Math.abs(c - cluster.val) < 1e-5) {
                        cluster.count++;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    clusters.add(new Cluster(c, 1));
                }
            }

            double threshold = nonManifoldEdges.size() * 0.1;
            List<Double> sigClusters = new ArrayList<>();
            double totalClustered = 0;
            for (Cluster cluster : clusters) {
                if (cluster.count > threshold) {
                    sigClusters.add(cluster.val);
                    totalClustered += cluster.count;
                }
            }

            if (totalClustered > maxClustered) {
                maxClustered = totalClustered;
                bestAxis = axis;
                Collections.sort(sigClusters);
                bestPlanes = sigClusters;
            }
        }

        if (bestAxis != -1 && maxClustered > nonManifoldEdges.size() * 0.8 && !bestPlanes.isEmpty()) {
            String[] axisNames = {"X", "Y", "Z"};
            if (logger != null) logger.accept(
                    "Detected layered structure along " + axisNames[bestAxis] + " axis at interface planes: " + bestPlanes);
            return new LayerStructure(true, bestAxis, bestPlanes);
        } else {
            if (logger != null) logger.accept(
                    "No clear planar layered structure detected for non-manifold edges. Falling back to CAD mode.");
            return new LayerStructure(false, -1, Collections.emptyList());
        }
    }

    private static List<List<Integer>> partitionIntoShells(List<long[]> triangles, Map<Long, double[]> nodeCoords,
                                                           List<double[]> triangleNormals, LayerStructure layerData,
                                                           Consumer<String> logger) {
        int bestAxis = layerData.bestAxis();
        List<Double> bestPlanes = layerData.bestPlanes();
        int numLayers = bestPlanes.size() + 1;
        List<List<Integer>> shells = new ArrayList<>();
        for (int i = 0; i < numLayers; i++) {
            shells.add(new ArrayList<>());
        }

        for (int idx = 0; idx < triangles.size(); idx++) {
            long[] tri = triangles.get(idx);
            double[] c1 = nodeCoords.get(tri[0]);
            double[] c2 = nodeCoords.get(tri[1]);
            double[] c3 = nodeCoords.get(tri[2]);

            double valMin = Math.min(c1[bestAxis], Math.min(c2[bestAxis], c3[bestAxis]));
            double valMax = Math.max(c1[bestAxis], Math.max(c2[bestAxis], c3[bestAxis]));
            double nVal = triangleNormals.get(idx)[bestAxis];

            int onInterface = -1;
            for (int pIdx = 0; pIdx < bestPlanes.size(); pIdx++) {
                double p = bestPlanes.get(pIdx);
                if (valMin >= p - 1e-6 && valMax <= p + 1e-6) {
                    onInterface = pIdx;
                    break;
                }
            }

            if (onInterface != -1) {
                if (nVal > 0) {
                    shells.get(onInterface).add(idx);
                } else {
                    shells.get(onInterface + 1).add(idx);
                }
            } else {
                double valCenter = (c1[bestAxis] + c2[bestAxis] + c3[bestAxis]) / 3.0;
                boolean classified = false;
                for (int pIdx = 0; pIdx < bestPlanes.size(); pIdx++) {
                    double p = bestPlanes.get(pIdx);
                    if (valCenter < p) {
                        shells.get(pIdx).add(idx);
                        classified = true;
                        break;
                    }
                }
                if (!classified) {
                    shells.get(numLayers - 1).add(idx);
                }
            }
        }

        if (logger != null) logger.accept("Partitioned model into " + shells.size() + " shells.");
        for (int idx = 0; idx < shells.size(); idx++) {
            if (logger != null) logger.accept("  Shell " + (idx + 1) + ": " + shells.get(idx).size() + " facets");
        }
        return shells;
    }

    private static void rebuildDiscreteModel(Arena arena, MemorySegment ierr, List<List<Integer>> shells,
                                             List<long[]> triangles, Map<Long, double[]> nodeCoords, double minMeshSize,
                                             double maxMeshSize, Consumer<String> logger) {
        Gmsh.gmshClear(ierr);
        Gmsh.gmshModelAdd(arena.allocateFrom("STL_Converter"), ierr);
        Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.Algorithm3D"), 10.0, ierr); // HXT algorithm
        if (minMeshSize > 0) {
            Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.MeshSizeMin"), minMeshSize, ierr);
        }
        if (maxMeshSize > 0) {
            Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.MeshSizeMax"), maxMeshSize, ierr);
        }

        Set<Long> addedNodes = new HashSet<>();

        for (int idx = 1; idx <= shells.size(); idx++) {
            List<Integer> shell = shells.get(idx - 1);
            Gmsh.gmshModelAddDiscreteEntity(2, idx, MemorySegment.NULL, 0, ierr);

            Set<Long> refVerts = new HashSet<>();
            for (int triIdx : shell) {
                long[] tri = triangles.get(triIdx);
                refVerts.add(tri[0]);
                refVerts.add(tri[1]);
                refVerts.add(tri[2]);
            }

            List<Long> vertsToAdd = new ArrayList<>();
            for (long v : refVerts) {
                if (!addedNodes.contains(v)) {
                    vertsToAdd.add(v);
                }
            }
            Collections.sort(vertsToAdd);

            int numVertsToAdd = vertsToAdd.size();
            if (numVertsToAdd > 0) {
                MemorySegment nodeTagsSeg = arena.allocate(JAVA_LONG, numVertsToAdd);
                MemorySegment coordSeg = arena.allocate(JAVA_DOUBLE, numVertsToAdd * 3);

                for (int i = 0; i < numVertsToAdd; i++) {
                    long tag = vertsToAdd.get(i);
                    nodeTagsSeg.setAtIndex(JAVA_LONG, i, tag);
                    double[] coord = nodeCoords.get(tag);
                    coordSeg.setAtIndex(JAVA_DOUBLE, i * 3, coord[0]);
                    coordSeg.setAtIndex(JAVA_DOUBLE, i * 3 + 1, coord[1]);
                    coordSeg.setAtIndex(JAVA_DOUBLE, i * 3 + 2, coord[2]);
                }

                Gmsh.gmshModelMeshAddNodes(2, idx, nodeTagsSeg, numVertsToAdd, coordSeg, numVertsToAdd * 3L,
                                           MemorySegment.NULL, 0, ierr);
                addedNodes.addAll(vertsToAdd);
            }

            int numElems = shell.size();
            MemorySegment elemTagsSeg = arena.allocate(JAVA_LONG, numElems);
            MemorySegment elemNodesSeg = arena.allocate(JAVA_LONG, numElems * 3);

            long baseTag = idx * 200000L;
            for (int i = 0; i < numElems; i++) {
                elemTagsSeg.setAtIndex(JAVA_LONG, i, baseTag + i);
                long[] tri = triangles.get(shell.get(i));
                elemNodesSeg.setAtIndex(JAVA_LONG, i * 3, tri[0]);
                elemNodesSeg.setAtIndex(JAVA_LONG, i * 3 + 1, tri[1]);
                elemNodesSeg.setAtIndex(JAVA_LONG, i * 3 + 2, tri[2]);
            }

            Gmsh.gmshModelMeshAddElementsByType(idx, 2, elemTagsSeg, numElems, elemNodesSeg, numElems * 3L, ierr);
        }

        for (int idx = 1; idx <= shells.size(); idx++) {
            MemorySegment surfaceTags = arena.allocate(JAVA_INT, 1);
            surfaceTags.setAtIndex(JAVA_INT, 0, idx);
            int loopTag = Gmsh.gmshModelGeoAddSurfaceLoop(surfaceTags, 1, -1, ierr);

            MemorySegment loopTags = arena.allocate(JAVA_INT, 1);
            loopTags.setAtIndex(JAVA_INT, 0, loopTag);
            Gmsh.gmshModelGeoAddVolume(loopTags, 1, -1, ierr);
        }

        Gmsh.gmshModelGeoSynchronize(ierr);

        if (logger != null) logger.accept("Generating 3D tetrahedral mesh...");
        Gmsh.gmshModelMeshGenerate(3, ierr);
        if (logger != null) logger.accept("Mesh generation complete!");
    }

    private static void runCadRemeshing(Arena arena, MemorySegment ierr, String inputStl,
                                        Map<Long, double[]> nodeCoords, double minMeshSize, double maxMeshSize,
                                        double angleToFindSurfaces, Consumer<String> logger) {
        double initialMin = minMeshSize;
        double initialMax = maxMeshSize;
        boolean success = false;

        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                Gmsh.gmshClear(ierr);
                Gmsh.gmshModelAdd(arena.allocateFrom("STL_Converter"), ierr);

                if (logger != null) logger.accept("Merging STL file: " + inputStl);
                Gmsh.gmshMerge(arena.allocateFrom(inputStl), ierr);
                if (ierr.get(JAVA_INT, 0) != 0) {
                    throw new RuntimeException("error-loading-stl");
                }

                if (logger != null) logger.accept("Classifying surfaces and creating CAD geometry...");
                double angle = angleToFindSurfaces * Math.PI / 180.0;
                Gmsh.gmshModelMeshClassifySurfaces(angle, 1, 1, Math.PI, 1, ierr);
                Gmsh.gmshModelMeshCreateGeometry(MemorySegment.NULL, 0, ierr);

                MemorySegment dimTagsPtr = arena.allocate(ADDRESS);
                MemorySegment dimTags_n = arena.allocate(JAVA_LONG);
                Gmsh.gmshModelGetEntities(dimTagsPtr, dimTags_n, 2, ierr);

                long dimTagsLen = dimTags_n.get(JAVA_LONG, 0);
                long numEntities = dimTagsLen / 2;
                if (logger != null) logger.accept("Entities found: " + numEntities);

                MemorySegment dimTags = dimTagsPtr.get(ADDRESS, 0).reinterpret(dimTagsLen * 4);

                MemorySegment surfaceTags = arena.allocate(JAVA_INT, numEntities);
                for (long i = 0; i < numEntities; i++) {
                    int dim = dimTags.getAtIndex(JAVA_INT, i * 2);
                    int tag = dimTags.getAtIndex(JAVA_INT, i * 2 + 1);
                    surfaceTags.setAtIndex(JAVA_INT, i, tag);
                }

                int loopTag = Gmsh.gmshModelGeoAddSurfaceLoop(surfaceTags, numEntities, -1, ierr);
                MemorySegment loopTags = arena.allocateFrom(JAVA_INT, loopTag);
                Gmsh.gmshModelGeoAddVolume(loopTags, 1, -1, ierr);

                Gmsh.gmshModelGeoSynchronize(ierr);
                Gmsh.gmshFree(dimTagsPtr.get(ADDRESS, 0));

                double meshMin = initialMin / Math.pow(1.5, attempt);
                double meshMax = initialMax / Math.pow(1.5, attempt);
                if (logger != null) logger.accept(
                        String.format("Attempt %d: target size range %.3f - %.3f\n", attempt + 1, meshMin, meshMax));

                Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.MeshSizeMin"), meshMin, ierr);
                Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.MeshSizeMax"), meshMax, ierr);
                Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.MeshSizeFromCurvature"), 12.0, ierr);
                Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.Algorithm3D"), 1.0, ierr); // Delaunay 3D

                Gmsh.gmshModelMeshGenerate(3, ierr);
                if (ierr.get(JAVA_INT, 0) != 0) {
                    throw new RuntimeException(LocalizationController.getInstance()
                                                                     .getString(
                                                                             LocalizationController.ERROR_DIALOG_BUNDLE,
                                                                             "mesh-error-with-error-code") + ": " + ierr.get(
                            JAVA_INT, 0) + ".");
                }

                Gmsh.gmshModelMeshOptimize(arena.allocateFrom("Netgen"), 0, 1, MemorySegment.NULL, 0, ierr);

                success = true;
                break;
            } catch (Exception e) {
                if (logger != null)
                    logger.accept("Meshing attempt " + (attempt + 1) + " failed: " + e.getMessage() + ". Retrying...");
                initialMin /= 1.5;
                initialMax /= 1.5;
            }
        }

        if (!success) {
            throw new RuntimeException("mesh-failed");
        }
    }

    private static void runCadRemeshing2d(Arena arena, MemorySegment ierr, String inputStl, double minMeshSize,
                                          double maxMeshSize, double angleToFindSurfaces, Consumer<String> logger) {
        double initialMin = minMeshSize;
        double initialMax = maxMeshSize;
        boolean success = false;

        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                Gmsh.gmshClear(ierr);
                Gmsh.gmshModelAdd(arena.allocateFrom("STL_Converter"), ierr);

                if (logger != null) logger.accept("Merging STL file: " + inputStl);
                Gmsh.gmshMerge(arena.allocateFrom(inputStl), ierr);
                if (ierr.get(JAVA_INT, 0) != 0) {
                    throw new RuntimeException("error-loading-stl");
                }

                if (logger != null) logger.accept("Classifying surfaces and creating CAD geometry...");
                double angle = angleToFindSurfaces * Math.PI / 180.0;
                Gmsh.gmshModelMeshClassifySurfaces(angle, 1, 1, Math.PI, 1, ierr);
                Gmsh.gmshModelMeshCreateGeometry(MemorySegment.NULL, 0, ierr);

                Gmsh.gmshModelGeoSynchronize(ierr);

                double meshMin = initialMin / Math.pow(1.5, attempt);
                double meshMax = initialMax / Math.pow(1.5, attempt);
                if (logger != null) logger.accept(
                        String.format("Attempt %d: target size range %.3f - %.3f\n", attempt + 1, meshMin, meshMax));

                Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.MeshSizeMin"), meshMin, ierr);
                Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.MeshSizeMax"), meshMax, ierr);
                Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.MeshSizeFromCurvature"), 12.0, ierr);
                Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.Algorithm"), 6.0, ierr); // Frontal-Delaunay 2D

                Gmsh.gmshModelMeshGenerate(2, ierr);
                if (ierr.get(JAVA_INT, 0) != 0) {
                    throw new RuntimeException(LocalizationController.getInstance()
                                                                     .getString(
                                                                             LocalizationController.ERROR_DIALOG_BUNDLE,
                                                                             "mesh-error-with-error-code") + ": " + ierr.get(
                            JAVA_INT, 0) + ".");
                }

                Gmsh.gmshModelMeshOptimize(arena.allocateFrom("Netgen"), 0, 1, MemorySegment.NULL, 0, ierr);

                success = true;
                break;
            } catch (Exception e) {
                if (logger != null)
                    logger.accept("Meshing attempt " + (attempt + 1) + " failed: " + e.getMessage() + ". Retrying...");
                initialMin /= 1.5;
                initialMax /= 1.5;
            }
        }

        if (!success) {
            throw new RuntimeException("mesh-failed");
        }
    }

    private static Map<Integer, float[]> exportNodes(Arena arena, MemorySegment ierr, Consumer<String> logger) {
        if (logger != null) logger.accept("Step: Exporting Nodes");
        MemorySegment nodeTagsPtr = arena.allocate(ADDRESS);
        MemorySegment nodeTags_n = arena.allocate(JAVA_LONG);
        MemorySegment coordsPtr = arena.allocate(ADDRESS);
        MemorySegment coords_n = arena.allocate(JAVA_LONG);
        MemorySegment parametricCoordPtr = arena.allocate(ADDRESS);
        MemorySegment parametricCoord_n = arena.allocate(JAVA_LONG);

        Gmsh.gmshModelMeshGetNodes(nodeTagsPtr, nodeTags_n, coordsPtr, coords_n, parametricCoordPtr, parametricCoord_n,
                                   -1, -1, 0, 0, ierr);

        long numNodes = nodeTags_n.get(JAVA_LONG, 0);
        MemorySegment nodeTagsArray = nodeTagsPtr.get(ADDRESS, 0).reinterpret(numNodes * 8);
        MemorySegment coordsArray = coordsPtr.get(ADDRESS, 0).reinterpret(numNodes * 3 * 8);

        Map<Integer, float[]> coordinates = new HashMap<>();

        for (long i = 0; i < numNodes; i++) {
            int tag = Math.toIntExact(nodeTagsArray.getAtIndex(JAVA_LONG, i));
            float x = (float) coordsArray.getAtIndex(JAVA_DOUBLE, i * 3);
            float y = (float) coordsArray.getAtIndex(JAVA_DOUBLE, i * 3 + 1);
            float z = (float) coordsArray.getAtIndex(JAVA_DOUBLE, i * 3 + 2);
            coordinates.put(tag, new float[]{x, y, z});
        }

        Gmsh.gmshFree(nodeTagsPtr.get(ADDRESS, 0));
        Gmsh.gmshFree(coordsPtr.get(ADDRESS, 0));
        Gmsh.gmshFree(parametricCoordPtr.get(ADDRESS, 0));

        if (logger != null) logger.accept(String.format("Nodes exported! Count: %d\n", numNodes));
        return coordinates;
    }

    private static int[][] exportTetrahedrons(Arena arena, MemorySegment ierr) {
        MemorySegment finalTypePtr = arena.allocate(ADDRESS);
        MemorySegment finalType_n = arena.allocate(JAVA_LONG);
        MemorySegment finalTagsPtr = arena.allocate(ADDRESS);
        MemorySegment finalTags_n = arena.allocate(ADDRESS);
        MemorySegment finalTags_nn = arena.allocate(JAVA_LONG);
        MemorySegment finalNodeTagsPtr = arena.allocate(ADDRESS);
        MemorySegment finalNodeTags_n = arena.allocate(ADDRESS);
        MemorySegment finalNodeTags_nn = arena.allocate(JAVA_LONG);

        Gmsh.gmshModelMeshGetElements(finalTypePtr, finalType_n, finalTagsPtr, finalTags_n, finalTags_nn,
                                      finalNodeTagsPtr, finalNodeTags_n, finalNodeTags_nn, 3, -1, ierr);

        long finalTypes = finalType_n.get(JAVA_LONG, 0);
        MemorySegment finalTypesArray = finalTypePtr.get(ADDRESS, 0).reinterpret(finalTypes * 4);
        MemorySegment finalTagsPtrArray = finalTagsPtr.get(ADDRESS, 0).reinterpret(finalTypes * 8);
        MemorySegment finalTags_nArray = finalTags_n.get(ADDRESS, 0).reinterpret(finalTypes * 8);
        MemorySegment finalNodeTagsPerElemPtrArray = finalNodeTagsPtr.get(ADDRESS, 0).reinterpret(finalTypes * 8);

        int[][] finalIndices = new int[0][4];

        for (long i = 0; i < finalTypes; i++) {
            int eType = finalTypesArray.getAtIndex(JAVA_INT, i);
            if (eType == 4) { // Tetrahedron
                int nElems = Math.toIntExact(finalTags_nArray.getAtIndex(JAVA_LONG, i));
                finalIndices = new int[nElems][4];

                MemorySegment conns = finalNodeTagsPerElemPtrArray.getAtIndex(ADDRESS, i).reinterpret(nElems * 4L * 8);
                for (int j = 0; j < nElems; j++) {
                    long n1 = conns.getAtIndex(JAVA_LONG, j * 4L);
                    long n2 = conns.getAtIndex(JAVA_LONG, j * 4L + 1);
                    long n3 = conns.getAtIndex(JAVA_LONG, j * 4L + 2);
                    long n4 = conns.getAtIndex(JAVA_LONG, j * 4L + 3);
                    finalIndices[j][0] = Math.toIntExact(n1);
                    finalIndices[j][1] = Math.toIntExact(n2);
                    finalIndices[j][2] = Math.toIntExact(n3);
                    finalIndices[j][3] = Math.toIntExact(n4);
                }
            }
        }

        for (long i = 0; i < finalTypes; i++) {
            Gmsh.gmshFree(finalTagsPtrArray.getAtIndex(ADDRESS, i));
            Gmsh.gmshFree(finalNodeTagsPerElemPtrArray.getAtIndex(ADDRESS, i));
        }
        Gmsh.gmshFree(finalTypePtr.get(ADDRESS, 0));
        Gmsh.gmshFree(finalTagsPtr.get(ADDRESS, 0));
        Gmsh.gmshFree(finalTags_n.get(ADDRESS, 0));
        Gmsh.gmshFree(finalNodeTagsPtr.get(ADDRESS, 0));
        Gmsh.gmshFree(finalNodeTags_n.get(ADDRESS, 0));

        return finalIndices;
    }

    private static int[][] exportTriangles(Arena arena, MemorySegment ierr, Consumer<String> logger) {
        if (logger != null) logger.accept("Step: Exporting Triangles");
        MemorySegment elementTypePtr = arena.allocate(ADDRESS);
        MemorySegment elementType_n = arena.allocate(JAVA_LONG);
        MemorySegment elementTagsPtr = arena.allocate(ADDRESS);
        MemorySegment elementTags_n = arena.allocate(ADDRESS);
        MemorySegment elementTags_nn = arena.allocate(JAVA_LONG);
        MemorySegment elementNodeTagsPtr = arena.allocate(ADDRESS);
        MemorySegment elementNodeTags_n = arena.allocate(ADDRESS);
        MemorySegment elementNodeTags_nn = arena.allocate(JAVA_LONG);

        Gmsh.gmshModelMeshGetElements(elementTypePtr, elementType_n, elementTagsPtr, elementTags_n, elementTags_nn,
                                      elementNodeTagsPtr, elementNodeTags_n, elementNodeTags_nn, 2, -1, ierr);

        long numTypes = elementType_n.get(JAVA_LONG, 0);
        MemorySegment elemTypesArray = elementTypePtr.get(ADDRESS, 0).reinterpret(numTypes * 4);
        MemorySegment elemTagsPtrArray = elementTagsPtr.get(ADDRESS, 0).reinterpret(numTypes * 8);
        MemorySegment elemTags_nArray = elementTags_n.get(ADDRESS, 0).reinterpret(numTypes * 8);
        MemorySegment nodeTagsPerElemPtrArray = elementNodeTagsPtr.get(ADDRESS, 0).reinterpret(numTypes * 8);

        long totalElems = 0;
        int[][] indices = new int[0][3];

        for (long i = 0; i < numTypes; i++) {
            int eType = elemTypesArray.getAtIndex(JAVA_INT, i);
            if (eType == 2) {
                int nElems = Math.toIntExact(elemTags_nArray.getAtIndex(JAVA_LONG, i));
                totalElems += nElems;
                indices = new int[nElems][3];

                MemorySegment conns = nodeTagsPerElemPtrArray.getAtIndex(ADDRESS, i).reinterpret(nElems * 3 * 8);
                for (int j = 0; j < nElems; j++) {
                    long n1 = conns.getAtIndex(JAVA_LONG, j * 3L);
                    long n2 = conns.getAtIndex(JAVA_LONG, j * 3L + 1);
                    long n3 = conns.getAtIndex(JAVA_LONG, j * 3L + 2);

                    indices[j][0] = Math.toIntExact(n1);
                    indices[j][1] = Math.toIntExact(n2);
                    indices[j][2] = Math.toIntExact(n3);
                }
            }
        }

        for (long i = 0; i < numTypes; i++) {
            Gmsh.gmshFree(elemTagsPtrArray.getAtIndex(ADDRESS, i));
            Gmsh.gmshFree(nodeTagsPerElemPtrArray.getAtIndex(ADDRESS, i));
        }
        Gmsh.gmshFree(elementTypePtr.get(ADDRESS, 0));
        Gmsh.gmshFree(elementTagsPtr.get(ADDRESS, 0));
        Gmsh.gmshFree(elementTags_n.get(ADDRESS, 0));
        Gmsh.gmshFree(elementNodeTagsPtr.get(ADDRESS, 0));
        Gmsh.gmshFree(elementNodeTags_n.get(ADDRESS, 0));

        if (logger != null) logger.accept(String.format("Triangles exported! Count: %d\n", totalElems));
        return indices;
    }

    public static TetraModelApi generateStepMultiLayerMesh(List<String> stepFiles, double minMeshSize,
                                                           double maxMeshSize, double angleToFindSurfaces,
                                                           Consumer<String> logger) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ierr = arena.allocate(JAVA_INT);
            gmshInit(arena, ierr, logger);
            Thread logPoller = startGmshLoggerPoller(ierr, logger);

            try {
                Gmsh.gmshClear(ierr);
                Gmsh.gmshModelAdd(arena.allocateFrom("STEP_MultiLayer"), ierr);

                for (String stepFile : stepFiles) {
                    if (logger != null) logger.accept("Loading STEP file: " + stepFile);
                    Gmsh.gmshMerge(arena.allocateFrom(stepFile), ierr);
                    if (ierr.get(JAVA_INT, 0) != 0) {
                        throw new RuntimeException("error-loading-step");
                    }
                }

                MemorySegment dimTagsPtr = arena.allocate(ADDRESS);
                MemorySegment dimTags_n = arena.allocate(JAVA_LONG);
                Gmsh.gmshModelGetEntities(dimTagsPtr, dimTags_n, 3, ierr);

                long numVols = dimTags_n.get(JAVA_LONG, 0) / 2;
                if (logger != null)
                    logger.accept("Found " + numVols + " volume(s) in STEP file(s). Applying Boolean Fragments...");

                if (numVols > 0) {
                    MemorySegment objectDimTags = dimTagsPtr.get(ADDRESS, 0).reinterpret(numVols * 2 * 4);
                    MemorySegment toolDimTags = arena.allocate(JAVA_INT, 0);

                    MemorySegment outDimTagsPtr = arena.allocate(ADDRESS);
                    MemorySegment outDimTags_n = arena.allocate(JAVA_LONG);
                    MemorySegment outDimTagsMapPtr = arena.allocate(ADDRESS);
                    MemorySegment outDimTagsMap_n = arena.allocate(ADDRESS);
                    MemorySegment outDimTagsMap_nn = arena.allocate(JAVA_LONG);

                    // The C API takes the number of ints in the flat (dim, tag) array, not the number of pairs.
                    Gmsh.gmshModelOccFragment(objectDimTags, numVols * 2, toolDimTags, 0, outDimTagsPtr,
                                              outDimTags_n, outDimTagsMapPtr, outDimTagsMap_n, outDimTagsMap_nn, -1,
                                              1, 1, ierr);
                    if (ierr.get(JAVA_INT, 0) != 0) {
                        throw new RuntimeException("fragment-error-with-error-code: " + ierr.get(JAVA_INT, 0));
                    }

                    Gmsh.gmshModelOccSynchronize(ierr);
                } else {
                    Gmsh.gmshModelOccSynchronize(ierr);
                }

                Gmsh.gmshModelGetEntities(dimTagsPtr, dimTags_n, 3, ierr);
                long newNumVols = dimTags_n.get(JAVA_LONG, 0) / 2;
                List<Integer> volumeTags = new ArrayList<>();
                if (newNumVols > 0) {
                    MemorySegment newDimTags = dimTagsPtr.get(ADDRESS, 0).reinterpret(newNumVols * 2 * 4);
                    for (long i = 0; i < newNumVols; i++) {
                        volumeTags.add(newDimTags.getAtIndex(JAVA_INT, i * 2 + 1));
                    }
                }

                if (minMeshSize > 0)
                    Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.MeshSizeMin"), minMeshSize, ierr);
                if (maxMeshSize > 0)
                    Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.MeshSizeMax"), maxMeshSize, ierr);
                Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.Algorithm3D"), 1.0, ierr);

                if (logger != null) logger.accept("Generating conformal 3D tetrahedral mesh for STEP...");
                Gmsh.gmshModelMeshGenerate(3, ierr);
                if (ierr.get(JAVA_INT, 0) != 0) {
                    throw new RuntimeException("mesh-error-with-error-code: " + ierr.get(JAVA_INT, 0));
                }

                Gmsh.gmshModelMeshOptimize(arena.allocateFrom("Netgen"), 0, 1, MemorySegment.NULL, 0, ierr);

                Map<Integer, float[]> finalCoords = exportNodes(arena, ierr, logger);
                Pair<int[][], int[]> finalElements = exportTetrahedronsMultiLayer(arena, ierr, volumeTags);

                if (logger != null) logger.accept(
                        String.format("Multi-layer mesh finished successfully! Nodes: %d, Elements: %d, Layers: %d\n",
                                      finalCoords.size(), finalElements.getKey().length, volumeTags.size()));

                return TetraModelApi.builder()
                                    .coordinates(finalCoords)
                                    .indices(finalElements.getKey())
                                    .dimension(Dimension.THREE_D)
                                    .elementLayers(finalElements.getValue())
                                    .build();
            } finally {
                logPoller.interrupt();
                try {
                    logPoller.join(1000);
                } catch (InterruptedException ignored) {
                }
                Gmsh.gmshLoggerStop(ierr);
                Gmsh.gmshFinalize(ierr);
            }
        }
    }

    public static TetraModelApi generateMultiLayerMesh(List<String> inputStls, double minMeshSize, double maxMeshSize,
                                                       double angleToFindSurfaces, double tolerance,
                                                       Consumer<String> logger) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ierr = arena.allocate(JAVA_INT);
            gmshInit(arena, ierr, logger);
            Thread logPoller = startGmshLoggerPoller(ierr, logger);

            try {
                if (logger != null) logger.accept("Analyzing multi-layer topology...");

                List<Map<Integer, float[]>> allCoords = new ArrayList<>();
                List<int[][]> allIndices = new ArrayList<>();

                for (String stl : inputStls) {
                    Gmsh.gmshClear(ierr);
                    Gmsh.gmshModelAdd(arena.allocateFrom("Temp_STL"), ierr);
                    Gmsh.gmshMerge(arena.allocateFrom(stl), ierr);
                    if (ierr.get(JAVA_INT, 0) != 0) {
                        throw new RuntimeException("error-loading-stl");
                    }
                    allCoords.add(exportNodes(arena, ierr, null));
                    allIndices.add(exportTriangles(arena, ierr, null));
                }

                long gridFactor = (long) (1.0 / (tolerance > 0 ? tolerance : 1e-5));
                double tol = tolerance > 0 ? tolerance : 1e-5;
                Map<String, List<Integer>> grid = new HashMap<>();
                Map<Integer, float[]> unifiedCoords = new HashMap<>();
                int nextNodeId = 1;

                List<int[][]> unifiedIndices = new ArrayList<>();

                for (int i = 0; i < inputStls.size(); i++) {
                    Map<Integer, float[]> coords = allCoords.get(i);
                    int[][] indices = allIndices.get(i);
                    int[][] uIndices = new int[indices.length][3];

                    Map<Integer, Integer> localToGlobal = new HashMap<>();

                    for (Map.Entry<Integer, float[]> entry : coords.entrySet()) {
                        float[] c = entry.getValue();
                        long gx = Math.round(c[0] * gridFactor);
                        long gy = Math.round(c[1] * gridFactor);
                        long gz = Math.round(c[2] * gridFactor);

                        int globalId = -1;
                        outer:
                        for (long dx = -1; dx <= 1; dx++) {
                            for (long dy = -1; dy <= 1; dy++) {
                                for (long dz = -1; dz <= 1; dz++) {
                                    String key = (gx + dx) + "_" + (gy + dy) + "_" + (gz + dz);
                                    List<Integer> bucket = grid.get(key);
                                    if (bucket != null) {
                                        for (int id : bucket) {
                                            float[] uc = unifiedCoords.get(id);
                                            double dist = Math.sqrt(
                                                    Math.pow(c[0] - uc[0], 2) + Math.pow(c[1] - uc[1], 2) + Math.pow(
                                                            c[2] - uc[2], 2));
                                            if (dist <= tol) {
                                                globalId = id;
                                                break outer;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (globalId == -1) {
                            globalId = nextNodeId++;
                            unifiedCoords.put(globalId, c);
                            String key = gx + "_" + gy + "_" + gz;
                            grid.computeIfAbsent(key, k -> new ArrayList<>()).add(globalId);
                        }
                        localToGlobal.put(entry.getKey(), globalId);
                    }

                    List<int[]> validIndices = new ArrayList<>();
                    for (int j = 0; j < indices.length; j++) {
                        int n1 = localToGlobal.get(indices[j][0]);
                        int n2 = localToGlobal.get(indices[j][1]);
                        int n3 = localToGlobal.get(indices[j][2]);
                        if (n1 != n2 && n2 != n3 && n1 != n3) {
                            validIndices.add(new int[]{n1, n2, n3});
                        }
                    }
                    uIndices = validIndices.toArray(new int[0][3]);
                    unifiedIndices.add(uIndices);
                }

                record TriangleKey(int n1, int n2, int n3) {
                    public TriangleKey {
                        int[] arr = {n1, n2, n3};
                        Arrays.sort(arr);
                        n1 = arr[0];
                        n2 = arr[1];
                        n3 = arr[2];
                    }
                }

                Map<TriangleKey, Set<Integer>> triangleOwnership = new HashMap<>();
                Map<TriangleKey, int[]> triangleVertices = new HashMap<>();

                for (int layer = 0; layer < unifiedIndices.size(); layer++) {
                    for (int[] tri : unifiedIndices.get(layer)) {
                        TriangleKey key = new TriangleKey(tri[0], tri[1], tri[2]);
                        triangleOwnership.computeIfAbsent(key, k -> new HashSet<>()).add(layer);
                        triangleVertices.putIfAbsent(key, tri);
                    }
                }

                Map<Set<Integer>, List<int[]>> surfaces = new HashMap<>();
                for (Map.Entry<TriangleKey, Set<Integer>> entry : triangleOwnership.entrySet()) {
                    surfaces.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                            .add(triangleVertices.get(entry.getKey()));
                }

                Gmsh.gmshClear(ierr);
                Gmsh.gmshModelAdd(arena.allocateFrom("MultiLayerConformal"), ierr);

                MemorySegment nodeTagsSeg = arena.allocate(JAVA_LONG, unifiedCoords.size());
                MemorySegment coordSeg = arena.allocate(JAVA_DOUBLE, unifiedCoords.size() * 3L);
                int nodeIdx = 0;
                for (Map.Entry<Integer, float[]> entry : unifiedCoords.entrySet()) {
                    nodeTagsSeg.setAtIndex(JAVA_LONG, nodeIdx, entry.getKey());
                    coordSeg.setAtIndex(JAVA_DOUBLE, nodeIdx * 3L, entry.getValue()[0]);
                    coordSeg.setAtIndex(JAVA_DOUBLE, nodeIdx * 3L + 1, entry.getValue()[1]);
                    coordSeg.setAtIndex(JAVA_DOUBLE, nodeIdx * 3L + 2, entry.getValue()[2]);
                    nodeIdx++;
                }

                // Add nodes to entity dim=2, tag=1 (dummy container)
                Gmsh.gmshModelAddDiscreteEntity(2, 1, MemorySegment.NULL, 0, ierr);
                Gmsh.gmshModelMeshAddNodes(2, 1, nodeTagsSeg, unifiedCoords.size(), coordSeg, unifiedCoords.size() * 3L,
                                           MemorySegment.NULL, 0, ierr);

                int surfaceTag = 2;
                Map<Set<Integer>, Integer> surfaceTagMap = new HashMap<>();

                for (Map.Entry<Set<Integer>, List<int[]>> entry : surfaces.entrySet()) {
                    List<int[]> tris = entry.getValue();
                    Gmsh.gmshModelAddDiscreteEntity(2, surfaceTag, MemorySegment.NULL, 0, ierr);

                    MemorySegment elemTagsSeg = arena.allocate(JAVA_LONG, tris.size());
                    MemorySegment elemNodesSeg = arena.allocate(JAVA_LONG, tris.size() * 3L);

                    long baseTag = surfaceTag * 200000L;
                    for (int i = 0; i < tris.size(); i++) {
                        elemTagsSeg.setAtIndex(JAVA_LONG, i, baseTag + i);
                        elemNodesSeg.setAtIndex(JAVA_LONG, i * 3L, tris.get(i)[0]);
                        elemNodesSeg.setAtIndex(JAVA_LONG, i * 3L + 1, tris.get(i)[1]);
                        elemNodesSeg.setAtIndex(JAVA_LONG, i * 3L + 2, tris.get(i)[2]);
                    }

                    Gmsh.gmshModelMeshAddElementsByType(surfaceTag, 2, elemTagsSeg, tris.size(), elemNodesSeg,
                                                        tris.size() * 3L, ierr);
                    surfaceTagMap.put(entry.getKey(), surfaceTag);
                    surfaceTag++;
                }

                List<Integer> volumeTags = new ArrayList<>();
                for (int layer = 0; layer < inputStls.size(); layer++) {
                    List<Integer> layerSurfaces = new ArrayList<>();
                    for (Map.Entry<Set<Integer>, Integer> entry : surfaceTagMap.entrySet()) {
                        if (entry.getKey().contains(layer)) {
                            layerSurfaces.add(entry.getValue());
                        }
                    }

                    MemorySegment stags = arena.allocate(JAVA_INT, layerSurfaces.size());
                    for (int i = 0; i < layerSurfaces.size(); i++) {
                        stags.setAtIndex(JAVA_INT, i, layerSurfaces.get(i));
                    }

                    int loopTag = Gmsh.gmshModelGeoAddSurfaceLoop(stags, layerSurfaces.size(), -1, ierr);
                    int volTag = Gmsh.gmshModelGeoAddVolume(arena.allocateFrom(JAVA_INT, loopTag), 1, -1, ierr);
                    volumeTags.add(volTag);
                }

                Gmsh.gmshModelGeoSynchronize(ierr);

                if (logger != null) logger.accept("Generated combined boundary model. Preparing 3D mesher...");

                if (minMeshSize > 0)
                    Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.MeshSizeMin"), minMeshSize, ierr);
                if (maxMeshSize > 0)
                    Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.MeshSizeMax"), maxMeshSize, ierr);
                Gmsh.gmshOptionSetNumber(arena.allocateFrom("Mesh.Algorithm3D"), 1.0, ierr); // Delaunay 3D

                Gmsh.gmshModelMeshGenerate(3, ierr);
                if (ierr.get(JAVA_INT, 0) != 0) {
                    throw new RuntimeException("mesh-error-with-error-code: " + ierr.get(JAVA_INT, 0));
                }

                Gmsh.gmshModelMeshOptimize(arena.allocateFrom("Netgen"), 0, 1, MemorySegment.NULL, 0, ierr);

                Map<Integer, float[]> finalCoords = exportNodes(arena, ierr, logger);
                Pair<int[][], int[]> finalElements = exportTetrahedronsMultiLayer(arena, ierr, volumeTags);

                if (logger != null) logger.accept(
                        String.format("Multi-layer mesh finished successfully! Nodes: %d, Elements: %d, Layers: %d\n",
                                      finalCoords.size(), finalElements.getKey().length, volumeTags.size()));

                return TetraModelApi.builder()
                                    .coordinates(finalCoords)
                                    .indices(finalElements.getKey())
                                    .dimension(Dimension.THREE_D)
                                    .elementLayers(finalElements.getValue())
                                    .build();
            } finally {
                logPoller.interrupt();
                try {
                    logPoller.join(1000);
                } catch (InterruptedException ignored) {
                }
                Gmsh.gmshLoggerStop(ierr);
                Gmsh.gmshFinalize(ierr);
            }
        }
    }

    private static Pair<int[][], int[]> exportTetrahedronsMultiLayer(Arena arena, MemorySegment ierr,
                                                                     List<Integer> volumeTags) {
        List<int[]> allElements = new ArrayList<>();
        List<Integer> allLayers = new ArrayList<>();

        for (int layerIndex = 0; layerIndex < volumeTags.size(); layerIndex++) {
            int volTag = volumeTags.get(layerIndex);

            MemorySegment finalTypePtr = arena.allocate(ADDRESS);
            MemorySegment finalType_n = arena.allocate(JAVA_LONG);
            MemorySegment finalTagsPtr = arena.allocate(ADDRESS);
            MemorySegment finalTags_n = arena.allocate(ADDRESS);
            MemorySegment finalTags_nn = arena.allocate(JAVA_LONG);
            MemorySegment finalNodeTagsPtr = arena.allocate(ADDRESS);
            MemorySegment finalNodeTags_n = arena.allocate(ADDRESS);
            MemorySegment finalNodeTags_nn = arena.allocate(JAVA_LONG);

            Gmsh.gmshModelMeshGetElements(finalTypePtr, finalType_n, finalTagsPtr, finalTags_n, finalTags_nn,
                                          finalNodeTagsPtr, finalNodeTags_n, finalNodeTags_nn, 3, volTag, ierr);

            long finalTypes = finalType_n.get(JAVA_LONG, 0);
            if (finalTypes == 0) {
                continue;
            }

            MemorySegment elemTypesArray = finalTypePtr.get(ADDRESS, 0).reinterpret(finalTypes * 4);
            MemorySegment elemTagsPtrArray = finalTagsPtr.get(ADDRESS, 0).reinterpret(finalTypes * 8);
            MemorySegment elemTags_nArray = finalTags_n.get(ADDRESS, 0).reinterpret(finalTypes * 8);
            MemorySegment nodeTagsPerElemPtrArray = finalNodeTagsPtr.get(ADDRESS, 0).reinterpret(finalTypes * 8);

            for (long i = 0; i < finalTypes; i++) {
                int eType = elemTypesArray.getAtIndex(JAVA_INT, i);
                if (eType == 4) { // Tetrahedron
                    int nElems = Math.toIntExact(elemTags_nArray.getAtIndex(JAVA_LONG, i));
                    MemorySegment conns = nodeTagsPerElemPtrArray.getAtIndex(ADDRESS, i).reinterpret(nElems * 4L * 8);
                    for (int j = 0; j < nElems; j++) {
                        long n1 = conns.getAtIndex(JAVA_LONG, j * 4L);
                        long n2 = conns.getAtIndex(JAVA_LONG, j * 4L + 1);
                        long n3 = conns.getAtIndex(JAVA_LONG, j * 4L + 2);
                        long n4 = conns.getAtIndex(JAVA_LONG, j * 4L + 3);
                        allElements.add(new int[]{Math.toIntExact(n1), Math.toIntExact(n2), Math.toIntExact(
                                n3), Math.toIntExact(n4)});
                        allLayers.add(layerIndex);
                    }
                }
            }

            for (long i = 0; i < finalTypes; i++) {
                Gmsh.gmshFree(elemTagsPtrArray.getAtIndex(ADDRESS, i));
                Gmsh.gmshFree(nodeTagsPerElemPtrArray.getAtIndex(ADDRESS, i));
            }
            Gmsh.gmshFree(finalTypePtr.get(ADDRESS, 0));
            Gmsh.gmshFree(finalTagsPtr.get(ADDRESS, 0));
            Gmsh.gmshFree(finalTags_n.get(ADDRESS, 0));
            Gmsh.gmshFree(finalNodeTagsPtr.get(ADDRESS, 0));
            Gmsh.gmshFree(finalNodeTags_n.get(ADDRESS, 0));
        }

        int[][] elementsArray = allElements.toArray(new int[0][0]);
        int[] layersArray = new int[allLayers.size()];
        for (int i = 0; i < allLayers.size(); i++) {
            layersArray[i] = allLayers.get(i);
        }

        return new Pair<>(elementsArray, layersArray);
    }
}
