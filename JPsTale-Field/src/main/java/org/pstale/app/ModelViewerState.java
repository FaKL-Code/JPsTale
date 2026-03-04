package org.pstale.app;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import org.pstale.assets.AssetFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.animation.AnimControl;
import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;

/**
 * AppState responsible for loading and displaying individual character/item
 * models for texture customization. Works alongside LoaderAppState — when a
 * model is loaded here the map scene is hidden and vice-versa.
 */
public class ModelViewerState extends SubAppState {

    private static final Logger log = LoggerFactory.getLogger(ModelViewerState.class);

    /** Scale applied to character models (same as TestAnimation) */
    private static final float MODEL_SCALE = 0.1f;

    /** Categories available for browsing */
    public enum Category {
        MONSTER("Monstros", "char/monster"),
        NPC("NPCs", "char/npc"),
        PLAYER("Personagens", "char/tmABCD"),
        MOUNT("Montarias", "char/mount"),
        ITEM("Itens", "image/Sinimage/Items/DropItem");

        public final String label;
        public final String relativePath;

        Category(String label, String relativePath) {
            this.label = label;
            this.relativePath = relativePath;
        }
    }

    /** Entry representing a loadable model */
    public static class ModelEntry {
        public final String displayName;
        public final String loadPath;
        public final boolean isCharacter; // true = .inx (loadCharacter), false = .smd (loadStageObj)

        public ModelEntry(String displayName, String loadPath, boolean isCharacter) {
            this.displayName = displayName;
            this.loadPath = loadPath;
            this.isCharacter = isCharacter;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /** Currently loaded model node */
    private Node currentModel;
    /** Auto-rotation control attached to the model */
    private AutoRotateControl rotateControl;
    /** Whether auto-rotation is enabled */
    private boolean autoRotate = false;

    /** Whether we are currently in model-viewer mode (map hidden) */
    private boolean viewerActive = false;

    /** Saved camera position/direction so we can restore when leaving viewer */
    private Vector3f savedCamLocation;
    private Vector3f savedCamDirection;

    /** Client root resolved from settings */
    private String clientRoot;

    @Override
    public void initialize(Application app) {
        clientRoot = app.getContext().getSettings().getString("ClientRoot");
    }

    @Override
    protected void cleanup(Application app) {
        unloadCurrentModel();
    }

    /**
     * Scan a category folder for available models.
     * For MONSTER/NPC/PLAYER/MOUNT: looks for .inx files inside sub-folders.
     * For ITEM: looks for .smd files directly.
     */
    public List<ModelEntry> scanCategory(Category category) {
        List<ModelEntry> entries = new ArrayList<>();
        if (clientRoot == null || clientRoot.isEmpty()) {
            log.warn("ClientRoot not set — cannot scan models");
            return entries;
        }

        File baseDir = new File(clientRoot, category.relativePath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            log.warn("Category folder not found: {}", baseDir.getAbsolutePath());
            return entries;
        }

        if (category == Category.ITEM) {
            // Items: .smd files directly in the folder
            File[] files = baseDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".smd")) {
                        String name = f.getName().replaceFirst("(?i)\\.smd$", "");
                        String loadPath = category.relativePath + "/" + f.getName();
                        entries.add(new ModelEntry(name, loadPath, false));
                    }
                }
            }
        } else {
            // Characters: scan sub-folders for .inx files
            File[] subDirs = baseDir.listFiles();
            if (subDirs != null) {
                for (File dir : subDirs) {
                    if (dir.isDirectory()) {
                        File[] inxFiles = dir.listFiles(new FilenameFilter() {
                            @Override
                            public boolean accept(File d, String n) {
                                return n.toLowerCase().endsWith(".inx");
                            }
                        });
                        if (inxFiles != null && inxFiles.length > 0) {
                            // Use the first .inx found
                            File inx = inxFiles[0];
                            String displayName = dir.getName();
                            String loadPath = category.relativePath + "/" + dir.getName() + "/" + inx.getName();
                            entries.add(new ModelEntry(displayName, loadPath, true));
                        }
                    }
                }
            }
        }

        Collections.sort(entries, new Comparator<ModelEntry>() {
            @Override
            public int compare(ModelEntry a, ModelEntry b) {
                return a.displayName.compareToIgnoreCase(b.displayName);
            }
        });
        return entries;
    }

    /**
     * Load and display the given model. Hides the current map scene.
     */
    public void loadModel(final ModelEntry entry) {
        final SimpleApplication app = (SimpleApplication) getApplication();

        // Save camera state
        Camera cam = app.getCamera();
        savedCamLocation = cam.getLocation().clone();
        savedCamDirection = cam.getDirection().clone();

        // Hide map scene
        LoaderAppState loader = getStateManager().getState(LoaderAppState.class);
        if (loader != null) {
            loader.rootNode.setCullHint(Spatial.CullHint.Always);
        }

        // Remove previous model
        unloadCurrentModel();

        viewerActive = true;

        // Load on background thread to avoid freezing
        app.enqueue(new Callable<Void>() {
            @Override
            public Void call() {
                try {
                    Node model;
                    if (entry.isCharacter) {
                        model = AssetFactory.loadCharacter(entry.loadPath);
                    } else {
                        model = AssetFactory.loadStageObj(entry.loadPath, false);
                    }

                    if (model == null) {
                        log.error("Failed to load model: {}", entry.loadPath);
                        return null;
                    }

                    model.scale(MODEL_SCALE);

                    // Disable animation so the model stays in its bind pose
                    // (static for easier texture analysis and editing)
                    AnimControl ac = model.getControl(AnimControl.class);
                    if (ac != null) {
                        ac.setEnabled(false);
                    }

                    // Add auto-rotation (off by default)
                    rotateControl = new AutoRotateControl();
                    rotateControl.setEnabled(autoRotate);
                    model.addControl(rotateControl);

                    currentModel = model;
                    rootNode.attachChild(model);

                    // Position camera to frame the model
                    positionCameraForModel(model);

                    log.info("Loaded model: {}", entry.displayName);
                } catch (Exception e) {
                    log.error("Error loading model: {}", entry.loadPath, e);
                }
                return null;
            }
        });
    }

    /**
     * Unload the current model and restore map scene visibility.
     */
    public void unloadCurrentModel() {
        if (currentModel != null) {
            currentModel.removeFromParent();
            currentModel = null;
            rotateControl = null;
        }
    }

    /**
     * Exit model viewer mode — restore the map scene and camera.
     */
    public void exitViewer() {
        unloadCurrentModel();
        viewerActive = false;

        // Show map scene again
        LoaderAppState loader = getStateManager().getState(LoaderAppState.class);
        if (loader != null) {
            loader.rootNode.setCullHint(Spatial.CullHint.Inherit);
        }

        // Restore camera
        if (savedCamLocation != null) {
            SimpleApplication app = (SimpleApplication) getApplication();
            app.getCamera().setLocation(savedCamLocation);
            app.getCamera().lookAtDirection(savedCamDirection, Vector3f.UNIT_Y);
        }
    }

    /**
     * Position the camera to nicely frame the loaded model.
     */
    private void positionCameraForModel(Spatial model) {
        SimpleApplication app = (SimpleApplication) getApplication();
        Camera cam = app.getCamera();

        // Models are typically centered near origin after scaling
        // Place camera at a comfortable viewing distance
        float distance = 15f;
        cam.setLocation(new Vector3f(0, 5, distance));
        cam.lookAt(new Vector3f(0, 3, 0), Vector3f.UNIT_Y);
    }

    /**
     * Toggle auto-rotation of the displayed model.
     */
    public void setAutoRotate(boolean rotate) {
        autoRotate = rotate;
        if (rotateControl != null) {
            rotateControl.setEnabled(rotate);
        }
    }

    public boolean isAutoRotate() {
        return autoRotate;
    }

    public boolean isViewerActive() {
        return viewerActive;
    }

    public Node getCurrentModel() {
        return currentModel;
    }

    /**
     * Simple control that rotates the spatial around the Y axis.
     */
    private static class AutoRotateControl extends AbstractControl {
        private static final float SPEED = 0.5f; // radians per second

        @Override
        protected void controlUpdate(float tpf) {
            spatial.rotate(0, SPEED * tpf, 0);
        }

        @Override
        protected void controlRender(RenderManager rm, ViewPort vp) {
            // no-op
        }
    }
}
