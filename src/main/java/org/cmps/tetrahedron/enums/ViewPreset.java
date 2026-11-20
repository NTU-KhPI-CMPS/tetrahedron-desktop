package org.cmps.tetrahedron.enums;

import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Vector3f;

/**
 * Standard CAD views. Models are treated as Z-up (X - right, Y - depth, Z - up).
 * <p>
 * Each view is a rotation that maps model coordinates to view coordinates:
 * view X points right on screen, view Y points up and view Z points towards the viewer.
 */
public enum ViewPreset {
    FRONT(new Vector3f(0, -1, 0), new Vector3f(0, 0, 1)),
    TOP(new Vector3f(0, 0, 1), new Vector3f(0, 1, 0)),
    RIGHT(new Vector3f(1, 0, 0), new Vector3f(0, 0, 1)),
    LEFT(new Vector3f(-1, 0, 0), new Vector3f(0, 0, 1)),
    /**
     * Looking from the front-right-top corner.
     */
    ISOMETRIC(new Vector3f(1, -1, 1), new Vector3f(0, 0, 1));

    private final Matrix3f rotation;

    /**
     * @param towardViewer model direction that will point at the viewer (the side of the model that is seen)
     * @param up           model direction that will point up on screen
     */
    ViewPreset(Vector3f towardViewer, Vector3f up) {
        Vector3f toward = new Vector3f(towardViewer).normalize();
        Vector3f right = new Vector3f(up).cross(toward).normalize();
        Vector3f screenUp = new Vector3f(toward).cross(right);
        // Rows of the rotation are the view axes expressed in model coordinates.
        this.rotation = new Matrix3f(right, screenUp, toward).transpose();
    }

    public Matrix3fc getRotation() {
        return rotation;
    }
}
