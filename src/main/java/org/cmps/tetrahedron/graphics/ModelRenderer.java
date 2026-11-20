package org.cmps.tetrahedron.graphics;

import org.cmps.tetrahedron.config.CanvasProperties;
import org.cmps.tetrahedron.controller.ModelController;
import org.cmps.tetrahedron.controller.MouseController;
import org.cmps.tetrahedron.controller.VertexInfoController;
import org.cmps.tetrahedron.enums.Dimension;
import org.cmps.tetrahedron.model.ColorSettings;
import org.cmps.tetrahedron.model.Model;
import org.cmps.tetrahedron.model.ModelViewSettings;
import org.cmps.tetrahedron.utils.CoordinatesConvertor;
import org.cmps.tetrahedron.viewmodel.DimensionViewModel;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

import static org.cmps.tetrahedron.utils.ColorUtils.getColorsForValues;
import static org.cmps.tetrahedron.utils.ShaderLoader.createShader;
import static org.lwjgl.opengles.EXTTextureFormatBGRA8888.GL_BGRA_EXT;
import static org.lwjgl.opengles.GLES30.*;

/**
 * Class responsible for painting of a 3D model.
 *
 * @author Mariia Borodin (HappyMary16)
 * @since 1.0
 */
public class ModelRenderer {

    private final ModelController modelController = ModelController.getInstance();

    private int vao;

    private int viewportSizeUniform;
    private int viewMatrixUniform;
    private int projMatrixUniform;
    private int modelMatrixUniform;

    private int coloredInSelectedColor;
    private int modelColor;
    private int showElementMesh;
    private int showLight;
    private int isDepthReading;

    private final Matrix4f modelMatrix = new Matrix4f();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projMatrix = new Matrix4f();
    private final Matrix3f rotation = new Matrix3f();

    private final MouseController mouseController = MouseController.getInstance();
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    public void initGL() {
        float[] backgroundColor = ColorSettings.getInstance().getBackgroundColor();
        glClearColor(backgroundColor[0], backgroundColor[1], backgroundColor[2], 1.0f);

        // Enable depth test
        glEnable(GL_DEPTH_TEST);
        // Accept fragment if it closer to the camera than the former one
        glDepthFunc(GL_LESS);

        // Display only faces edges
//        glPolygonMode( GL_FRONT_AND_BACK, GL_LINE );

        /* Create all needed GL resources */
        createVao();
        int program = createRasterProgram();
        initProgram(program);

        CoordinatesConvertor.initInstance(projMatrix, viewMatrix, modelMatrix);
    }

    public void paintGL() {
        float[] backgroundColor = ModelViewSettings.getInstance().isPublicationMode()
                ? ModelViewSettings.PUBLICATION_BACKGROUND_COLOR
                : ColorSettings.getInstance().getBackgroundColor();
        glClearColor(backgroundColor[0], backgroundColor[1], backgroundColor[2], 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        updateMatrix(mouseController.getZoomFactor(), mouseController.getRotation(rotation));
        renderModel();

        VertexInfoController vertexInfoController = VertexInfoController.getInstance();
        if (vertexInfoController.isClicked()) {
            // Reading depth value packed as 4 channel color (RGBA)
            ByteBuffer depthBuffer = BufferUtils.createByteBuffer(4);
            glReadPixels(vertexInfoController.getX(), vertexInfoController.getY(),
                         1, 1, GL_BGRA_EXT, GL_UNSIGNED_BYTE, depthBuffer);

            float depth = unpackDepth(depthBuffer);

            vertexInfoController.updateVertexInfoToDisplay(depth);
            paintGL();
        }
    }

    /**
     * Reconstructs a floating-point depth value from four 8-bit RGBA components.
     * <p>
     * This reverses the operation of a typical GLSL "pack depth into RGBA" shader,
     * which spreads a 32-bit float across four 8-bit channels.
     */
    private float unpackDepth(ByteBuffer buffer) {
        // Read four unsigned bytes (0–255) from the buffer in RGBA order.
        // '& 0xFF' converts the signed Java byte (-128..127) to an unsigned int (0..255).
        int r = buffer.get() & 0xFF;
        int g = buffer.get() & 0xFF;
        int b = buffer.get() & 0xFF;
        int a = buffer.get() & 0xFF;

        // Normalize each byte to [0, 1] by dividing by 255.0f.
        // This matches how the shader encodes the values as normalized floats.
        float vecR = r / 255.0f;
        float vecG = g / 255.0f;
        float vecB = b / 255.0f;
        float vecA = a / 255.0f;

        // Combine the channels back into a single float value.
        // Each successive term represents finer precision bits of the depth value.
        return vecR
                + vecG / 255.0f
                + vecB / (255.0f * 255.0f)
                + vecA / (255.0f * 255.0f * 255.0f);
    }

    private void updateMatrix(float zoomFactor, Matrix3f rotation) {
        Model model = ModelController.getInstance().getModel();
        if (model.getVertices().isEmpty()) {
            return;
        }

        modelMatrix.set(rotation)
                   .translate(model.getCenter());

        float eyeZ = zoomFactor * model.getRadius();
        viewMatrix.setLookAt(0.0f, 0.0f, eyeZ, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        float aspectRatio = (float) CanvasProperties.getWidth() / CanvasProperties.getHeight();
        Vector4f min = new Vector4f(model.getMin(), 1);
        Vector4f max = new Vector4f(model.getMax(), 1);
        Vector2f planes = getPlanes(min, max, modelMatrix, viewMatrix);
        projMatrix.setPerspective(45f, aspectRatio, planes.x, planes.y)
                  // The frame is read with glReadPixels (rows bottom-up) and shown as is (rows top-down),
                  // so render it upside down to get it upright on screen instead of mirrored.
                  .scaleLocal(1f, -1f, 1f);
    }

    private Vector2f getPlanes(Vector4f min, Vector4f max, Matrix4f modelMatrix, Matrix4f viewMatrix) {
        Vector4f[] bounds = new Vector4f[]{
                new Vector4f(min.x, min.y, min.z, 1.0f),
                new Vector4f(max.x, min.y, min.z, 1.0f),
                new Vector4f(min.x, max.y, min.z, 1.0f),
                new Vector4f(max.x, max.y, min.z, 1.0f),
                new Vector4f(min.x, min.y, max.z, 1.0f),
                new Vector4f(max.x, min.y, max.z, 1.0f),
                new Vector4f(min.x, max.y, max.z, 1.0f),
                new Vector4f(max.x, max.y, max.z, 1.0f)
        };

        var minZ = Float.MAX_VALUE;
        var maxZ = -Float.MAX_VALUE;

        for (Vector4f v : bounds) {
            v.mul(modelMatrix)
             .mul(viewMatrix);

            minZ = Math.min(minZ, -v.z);
            maxZ = Math.max(maxZ, -v.z);
        }

        // The margin must scale with the model: faces lying exactly on the bounding box
        // (e.g. flat end caps) get partially clipped by float rounding otherwise.
        float depthRange = Math.max(maxZ - minZ, 1e-6f);
        float margin = depthRange * 0.05f;
        float far = maxZ + margin;
        // Keep near away from zero to preserve depth-buffer precision when zoomed in close.
        float near = Math.max(minZ - margin, far * 0.001f);
        return new Vector2f(near, far);
    }

    private void renderModel() {
        glUniformMatrix4fv(viewMatrixUniform, false, viewMatrix.get(matrixBuffer));
        glUniformMatrix4fv(modelMatrixUniform, false, modelMatrix.get(matrixBuffer));
        glUniformMatrix4fv(projMatrixUniform, false, projMatrix.get(matrixBuffer));

        initColors();
        initModelSettings();

        glUniform2f(viewportSizeUniform, CanvasProperties.getWidth(), CanvasProperties.getHeight());
        glBindVertexArray(vao);

        if (modelController.isModelReady()) {
            initVao();
            createNormalsBuffer(modelController.getModel().getNormals());
            modelController.setModelReady(false);

            if (modelController.getValuesToDisplay() != null) {
                createColorBuffer(getColorsForValues(modelController.getValuesToDisplay()));
            }
        }

        glDrawArrays(GL_TRIANGLES, 0, modelController.getFaces().size() * 3);
        glBindVertexArray(0);
    }

    private void createVao() {
        // vao - Vertex Array Object
        this.vao = glGenVertexArrays();
        glBindVertexArray(vao);
        initVao();
    }

    private void initVao() {
        List<float[][]> faces = modelController.getFaces();
        FloatBuffer pb = BufferUtils.createFloatBuffer(faces.size() * 3 * 3);
        for (float[][] face : faces) {
            for (float[] vertex : face) {
                pb.put(vertex[0]).put(vertex[1]).put(vertex[2]);
            }
        }
        pb.flip();

        // setup vertex positions buffer
        int posVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, posVbo);
        glBufferData(GL_ARRAY_BUFFER, pb, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0L);
    }

    //TODO: fix memory leak
    private void createColorBuffer(List<float[]> colorsList) {
        int colorBuffer = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, colorBuffer);

        // We have "* 3 * 3" because one face has 3 coordinates and color has tree values (r, g, b).
        FloatBuffer colors = BufferUtils.createFloatBuffer(modelController.getFaces().size() * 3 * 3);
        int finiteElementFaceCount = DimensionViewModel.getInstance().getDimension() == Dimension.TWO_D ? 1 : 4;
        for (int i = 0; i < modelController.getFaces().size() / finiteElementFaceCount; i++) {
            for (int j = 0; j < finiteElementFaceCount * 3; j++) {
                for (float colorPart : colorsList.get(i)) {
                    colors.put(colorPart);
                }
            }
        }
        colors.flip();

        // setup color positions buffer
        glBufferData(GL_ARRAY_BUFFER, colors, GL_STATIC_DRAW);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0L);
    }

    private void createNormalsBuffer(float[][] normals) {
        int normalsBufferId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, normalsBufferId);

        FloatBuffer normalsBuffer = BufferUtils.createFloatBuffer(modelController.getFaces().size() * 3 * 3);
        for (int i = 0; i < modelController.getFaces().size(); i++) {
            for (int j = 0; j < 3; j++) {
                normalsBuffer.put(normals[i]);
            }
        }
        normalsBuffer.flip();

        // setup color positions buffer
        glBufferData(GL_ARRAY_BUFFER, normalsBuffer, GL_STATIC_DRAW);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, 0, 0L);
    }

    private int createRasterProgram() {
        int program = glCreateProgram();

        int vShader = createShader("shaders/vs.glsl", GL_VERTEX_SHADER);
        int fShader = createShader("shaders/fs.glsl", GL_FRAGMENT_SHADER);
        glAttachShader(program, vShader);
        glAttachShader(program, fShader);

        glBindAttribLocation(program, 0, "position");
        glBindAttribLocation(program, 1, "color");
        glBindAttribLocation(program, 2, "normal");

        glLinkProgram(program);

        return program;
    }

    private void initProgram(int program) {
        glUseProgram(program);
        viewMatrixUniform = glGetUniformLocation(program, "viewMatrix");
        projMatrixUniform = glGetUniformLocation(program, "projMatrix");
        modelMatrixUniform = glGetUniformLocation(program, "modelMatrix");
        viewportSizeUniform = glGetUniformLocation(program, "viewportSize");

        coloredInSelectedColor = glGetUniformLocation(program, "coloredInSelectedColor");
        modelColor = glGetUniformLocation(program, "modelColor");
        showElementMesh = glGetUniformLocation(program, "showElementMesh");
        showLight = glGetUniformLocation(program, "showLight");
        isDepthReading = glGetUniformLocation(program, "isDepthReading");
    }

    private void initColors() {
        glUniform1i(coloredInSelectedColor, ColorSettings.getInstance().isColoredInSelectedColor() ? 1 : 0);

        float[] modelColorArr = ColorSettings.getInstance().getModelColor();
        glUniform3f(modelColor, modelColorArr[0], modelColorArr[1], modelColorArr[2]);
    }

    private void initModelSettings() {
        glUniform1i(showElementMesh, ModelViewSettings.getInstance().isShowElementMesh() ? 1 : 0);
        glUniform1i(showLight, ModelViewSettings.getInstance().isShowLight() ? 1 : 0);

        VertexInfoController vertexInfoController = VertexInfoController.getInstance();
        glUniform1i(isDepthReading, vertexInfoController.isClicked() ? 1 : 0);
    }
}
