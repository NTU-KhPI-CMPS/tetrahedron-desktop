package org.cmps.tetrahedron.controller;

import lombok.Getter;
import org.cmps.tetrahedron.enums.Dimension;
import org.cmps.tetrahedron.exception.ModelValidationException;
import org.cmps.tetrahedron.model.ColorSettings;
import org.cmps.tetrahedron.model.Model;
import org.cmps.tetrahedron.utils.DataReader;
import org.cmps.tetrahedron.viewmodel.LegendData;

import java.io.File;
import java.util.*;

public class DeformationController {

    private List<float[]> lastAppliedDeformations;

    public static final float DEFAULT_SCALE = 1.0f;

    @Getter
    private float currentScale = DEFAULT_SCALE;
    @Getter
    private Set<String> deformationComponents = null;

    @Getter
    private static final DeformationController instance = new DeformationController();

    private DeformationController() {}

    public void applyDisplacements(File file) throws ModelValidationException {
        Model model = ModelController.getInstance().getModel();
        Map<Integer, float[]> originalVertices = model.getOriginalVertices();

        if (originalVertices.isEmpty()) {
            return;
        }

        lastAppliedDeformations = readDeformations(file, model);

        System.out.println("[DEBUG] Deformations loaded: " + lastAppliedDeformations.size());

        ColorSettings.getInstance().setColoredInSelectedColor(true);
        LegendData.getInstance().setVisible(false);
        applyDeformationScale(DEFAULT_SCALE, null);
    }

    public void applyDeformationScale(float scale, Set<String> components) {
        Model model = ModelController.getInstance().getModel();
        Map<Integer, float[]> originalVertices = ModelController.getInstance().getModel().getOriginalVertices();

        if (lastAppliedDeformations == null || originalVertices == null) {
            return;
        }

        deformationComponents = components;
        currentScale = scale;

        Map<Integer, float[]> modelVertices = new HashMap<>();
        for (Integer idx : originalVertices.keySet()) {
            float[] orig = originalVertices.get(idx);
            // deformations are stored by node index (see readDeformations), not in map iteration order
            float[] def = lastAppliedDeformations.get(idx - 1);
            float[] current = new float[] {orig[0], orig[1], orig[2]};
            modelVertices.put(idx, current);

            for (String component : Optional.ofNullable(deformationComponents).orElse(Set.of("x", "y", "z"))) {
                switch (component) {
                    case "x" -> current[0] = orig[0] + def[0] * currentScale;
                    case "y" -> current[1] = orig[1] + def[1] * currentScale;
                    case "z" -> current[2] = orig[2] + def[2] * currentScale;
                }
            }
        }

        model.updateVertices(modelVertices);
        ModelController.getInstance().setModelReady(true);
    }

    private List<float[]>  readDeformations(File file, Model model) throws ModelValidationException {
        var deformationsMap = DataReader.readCoordinates(file, model.getDimension() == Dimension.TWO_D);
        var expectedVerticesCount = model.getVertices().size();

        List<float[]> result = new ArrayList<>(Collections.nCopies(expectedVerticesCount, null));

        for (Map.Entry<Integer, float[]> e : deformationsMap.entrySet()) {
            int idx = e.getKey();
            float[] def = e.getValue();

            if (idx < 1 || idx > expectedVerticesCount) {
                throw new ModelValidationException("displacements-out-range", "check", idx);
            }
            result.set(idx - 1, def);
        }
        return result;
    }
}
