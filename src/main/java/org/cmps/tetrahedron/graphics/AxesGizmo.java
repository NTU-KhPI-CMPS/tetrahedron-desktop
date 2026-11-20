package org.cmps.tetrahedron.graphics;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Small axes indicator (X, Y, Z) that rotates together with the model,
 * so it is always clear how the model is oriented.
 * <p>
 * Uses the same rotation as {@link ModelRenderer} builds for the model matrix.
 */
public class AxesGizmo extends Canvas {

    private static final double SIZE = 100;
    private static final double AXIS_LENGTH = 34;
    private static final double LABEL_OFFSET = 12;
    private static final double ARROW_SIZE = 7;

    private static final Color X_COLOR = Color.web("#D93025");
    private static final Color Y_COLOR = Color.web("#188038");
    private static final Color Z_COLOR = Color.web("#1A56DB");

    private final Matrix3f lastRotation = new Matrix3f().zero();

    public AxesGizmo() {
        super(SIZE, SIZE);
        setMouseTransparent(true);
    }

    /**
     * Redraws the gizmo for the given model rotation (the same one ModelRenderer uses).
     */
    public void update(Matrix3fc rotation) {
        if (lastRotation.equals(rotation)) {
            return;
        }
        lastRotation.set(rotation);

        Axis[] axes = {
                new Axis("X", X_COLOR, rotation.transform(new Vector3f(1, 0, 0))),
                new Axis("Y", Y_COLOR, rotation.transform(new Vector3f(0, 1, 0))),
                new Axis("Z", Z_COLOR, rotation.transform(new Vector3f(0, 0, 1)))
        };
        // Painter's algorithm: draw axes pointing away from the viewer first.
        Arrays.sort(axes, Comparator.comparingDouble(a -> a.dir.z));

        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, SIZE, SIZE);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setFont(Font.font("System", FontWeight.BOLD, 13));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        double cx = SIZE / 2;
        double cy = SIZE / 2;

        for (Axis axis : axes) {
            // View-space +Y points up on screen, canvas Y points down.
            double dx = axis.dir.x;
            double dy = -axis.dir.y;
            double ex = cx + dx * AXIS_LENGTH;
            double ey = cy + dy * AXIS_LENGTH;

            // Axes pointing away from the viewer are drawn semi-transparent.
            double opacity = axis.dir.z < -0.05 ? 0.45 : 1.0;
            Color color = axis.color.deriveColor(0, 1, 1, opacity);

            gc.setStroke(color);
            gc.setFill(color);
            gc.setLineWidth(2.5);
            gc.strokeLine(cx, cy, ex, ey);

            double len = Math.hypot(dx, dy);
            if (len > 0.15) {
                drawArrowHead(gc, ex, ey, dx / len, dy / len);
            }

            double lx = cx + dx * (AXIS_LENGTH + LABEL_OFFSET);
            double ly = cy + dy * (AXIS_LENGTH + LABEL_OFFSET);
            if (len <= 0.15) {
                // Axis points (almost) straight at / away from the viewer: keep the label next to the origin.
                lx = cx + LABEL_OFFSET;
                ly = cy - LABEL_OFFSET;
            }
            gc.fillText(axis.name, lx, ly);
        }

        gc.setFill(Color.web("#1B018E"));
        gc.fillOval(cx - 3, cy - 3, 6, 6);
    }

    private void drawArrowHead(GraphicsContext gc, double x, double y, double ux, double uy) {
        double bx = x - ux * ARROW_SIZE;
        double by = y - uy * ARROW_SIZE;
        double px = -uy * ARROW_SIZE * 0.5;
        double py = ux * ARROW_SIZE * 0.5;
        gc.fillPolygon(new double[]{x, bx + px, bx - px}, new double[]{y, by + py, by - py}, 3);
    }

    private record Axis(String name, Color color, Vector3f dir) {
    }
}
