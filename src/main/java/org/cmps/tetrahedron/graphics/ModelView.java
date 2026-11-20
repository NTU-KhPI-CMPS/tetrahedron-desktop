package org.cmps.tetrahedron.graphics;

import com.sun.javafx.perf.PerformanceTracker;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.cmps.tetrahedron.config.CanvasProperties;
import org.cmps.tetrahedron.controller.ModelController;
import org.cmps.tetrahedron.controller.MouseController;
import org.cmps.tetrahedron.utils.NativesExtractor;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.Objects;

import static org.lwjgl.egl.EGL15.*;
import static org.lwjgl.opengles.GLES.*;
import static org.lwjgl.opengles.GLES30.*;
import static org.lwjgl.opengles.EXTTextureFormatBGRA8888.GL_BGRA_EXT;

public class ModelView extends Pane {

    private static final ModelRenderer modelRenderer = new ModelRenderer();
    private static final ImageView imageView = new ImageView();

    private static long display;
    private static long context;
    private static long surface;
    private static PointerBuffer configs;
    private static ByteBuffer buffer;
    private static PixelBuffer<ByteBuffer> pixelBuffer;
    private static PerformanceTracker performanceTracker;

    public ModelView() {
        Label label = new Label();
        StackPane labelPane = new StackPane(label);
        labelPane.setAlignment(Pos.TOP_LEFT);

        AxesGizmo axesGizmo = new AxesGizmo();
        StackPane.setAlignment(axesGizmo, Pos.BOTTOM_CENTER);
        StackPane.setMargin(axesGizmo, new Insets(0, 0, 15, 0));

        StackPane root = new StackPane(imageView, axesGizmo, labelPane);
        getChildren().add(root);

        this.widthProperty().addListener((obs, oldVal, newVal) -> {
            CanvasProperties.setWidth(newVal.intValue());
        });
        this.heightProperty().addListener((obs, oldVal, newVal) -> {
            CanvasProperties.setHeight(newVal.intValue());
        });
        this.sceneProperty().addListener((obs, oldScene, newScene) -> {
            // newScene is null when the page is replaced (e.g. navigating to the mesh page)
            if (newScene == null) {
                performanceTracker = null;
            } else if (Objects.equals(System.getProperty("glStats"), "true")) {
                performanceTracker = PerformanceTracker.getSceneTracker(newScene);
            }
        });

        initGl();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (CanvasProperties.isSizeChanged()) {
                    Dimension size = CanvasProperties.getSize();

                    createSurface(size.width, size.height);
                    createWritableImage(size.width, size.height);
                }

                drawFrame(CanvasProperties.getWidth(), CanvasProperties.getHeight());
                pixelBuffer.updateBuffer(b -> null);

                MouseController mouseController = MouseController.getInstance();
                axesGizmo.setVisible(ModelController.getInstance().hasModel());
                axesGizmo.update(mouseController.getX(), mouseController.getY());

                if (performanceTracker != null) {
                    label.setText(String.format(
                            "Current resolution: %dx%d, FPS: %f",
                            CanvasProperties.getWidth(),
                            CanvasProperties.getHeight(),
                            performanceTracker.getInstantFPS()
                    ));
                }
            }
        }.start();
    }

    private void initGl() {
        Configuration.LIBRARY_PATH.set(NativesExtractor.getNativesDir().toString());

        display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        eglInitialize(display, (IntBuffer) null, null);

        int[] configAttribs = {
                EGL_DEPTH_SIZE, 24,
                EGL_NONE
        };
        configs = MemoryStack.stackCallocPointer(1);
        eglChooseConfig(display, configAttribs, configs, new int[1]);

        int[] contextAttribs = {
                EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL_NONE
        };
        context = eglCreateContext(display, configs.get(0), EGL_NO_CONTEXT, contextAttribs);

        createSurface(CanvasProperties.getWidth(), CanvasProperties.getHeight());
        createWritableImage(CanvasProperties.getWidth(), CanvasProperties.getHeight());

        modelRenderer.initGL();
    }

    private void createSurface(int width, int height) {
        if (surface != 0) {
            eglDestroySurface(display, surface);
        }

        int[] surfaceAttribs = {
                EGL_WIDTH, width,
                EGL_HEIGHT, height,
                EGL_NONE
        };
        surface = eglCreatePbufferSurface(display, configs.get(0), surfaceAttribs);

        eglMakeCurrent(display, surface, surface, context);
        createCapabilities();
    }

    private void createWritableImage(int width, int height) {
        buffer = BufferUtils.createByteBuffer(width * height * 4);
        pixelBuffer = new PixelBuffer<>(width, height, buffer, PixelFormat.getByteBgraPreInstance());
        imageView.setImage(new WritableImage(pixelBuffer));
    }

    private void drawFrame(int width, int height) {
        glViewport(0, 0, width, height);

        modelRenderer.paintGL();

        glReadPixels(0, 0, width, height, GL_BGRA_EXT, GL_UNSIGNED_BYTE, buffer);
    }
}
