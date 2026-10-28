package org.cmps.tetrahedron.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cmps.tetrahedron.enums.StressDisplayOption;

import java.util.List;
import java.util.Map;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Stress {

    private Map<Integer, float[]> stress;
    private List<Float> stressToDisplay;
    private StressDisplayOption displayOption = StressDisplayOption.MISES;

    @Builder.Default
    private float minStress = Float.MAX_VALUE;
    @Builder.Default
    private float maxStress = -Float.MAX_VALUE;
}
