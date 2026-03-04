package org.pstale.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;

/**
 * Allows the user to pick a surface on the map and inspect the texture file
 * name applied to it. All geometries sharing the same texture(s) are
 * highlighted with a wireframe overlay so the user can see every place
 * the texture is used across the map.
 * <p>
 * Press <b>T</b> to toggle picker mode. While active, a <b>middle-click</b>
 * casts a ray from the cursor, reports the texture of the closest geometry,
 * and highlights every geometry that shares the same texture.
 */
public class TexturePickerAppState extends BaseAppState implements ActionListener {

    private static final Logger logger = LoggerFactory.getLogger(TexturePickerAppState.class);

    private static final String MAPPING_TOGGLE = "TexturePicker_Toggle";
    private static final String MAPPING_PICK = "TexturePicker_Pick";

    /** Common material parameter names that may hold textures. */
    private static final String[] PARAM_NAMES = {
            "DiffuseMap", "ColorMap", "LightMap",
            "Tex1", "Tex2", "Tex3", "Tex4"
    };

    /** Wireframe overlay colour (bright green). */
    private static final ColorRGBA HIGHLIGHT_COLOR = new ColorRGBA(0f, 1f, 0.3f, 1f);

    private SimpleApplication app;
    private boolean pickerActive = false;

    /** The last picked texture info (forwarded to HudState). */
    private String lastPickedInfo = null;

    /**
     * A dedicated node for highlight wireframe clones, attached directly to
     * rootNode so we can use world transforms.
     */
    private final Node highlightNode = new Node("_TextureHighlights_");

    /** Texture names currently being highlighted. */
    private final Set<String> highlightedTextureNames = new HashSet<String>();

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    @Override
    protected void initialize(Application application) {
        this.app = (SimpleApplication) application;
    }

    @Override
    protected void cleanup(Application application) {
        clearHighlights();
    }

    @Override
    protected void onEnable() {
        InputManager im = app.getInputManager();
        im.addMapping(MAPPING_TOGGLE, new KeyTrigger(KeyInput.KEY_T));
        im.addMapping(MAPPING_PICK, new MouseButtonTrigger(MouseInput.BUTTON_MIDDLE));
        im.addListener(this, MAPPING_TOGGLE, MAPPING_PICK);
    }

    @Override
    protected void onDisable() {
        InputManager im = app.getInputManager();
        im.deleteMapping(MAPPING_TOGGLE);
        im.deleteMapping(MAPPING_PICK);
        im.removeListener(this);
        clearHighlights();
    }

    // ---------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!isPressed)
            return;

        if (MAPPING_TOGGLE.equals(name)) {
            pickerActive = !pickerActive;
            logger.info("Texture picker {}", pickerActive ? "ON" : "OFF");

            HudState hud = getStateManager().getState(HudState.class);
            if (hud != null) {
                if (pickerActive) {
                    hud.setPickerInfo("Seletor de Textura: ATIVO (clique do meio para selecionar)");
                } else {
                    hud.setPickerInfo(null);
                    clearHighlights();
                }
            } else if (!pickerActive) {
                clearHighlights();
            }
            return;
        }

        if (MAPPING_PICK.equals(name) && pickerActive) {
            doPick();
        }
    }

    public boolean isPickerActive() {
        return pickerActive;
    }

    public String getLastPickedInfo() {
        return lastPickedInfo;
    }

    // ---------------------------------------------------------------
    // Ray-cast picking
    // ---------------------------------------------------------------

    private void doPick() {
        Camera cam = app.getCamera();
        InputManager im = app.getInputManager();
        Vector2f click2d = im.getCursorPosition().clone();
        Vector3f click3d = cam.getWorldCoordinates(click2d, 0f).clone();
        Vector3f dir = cam.getWorldCoordinates(click2d, 1f).subtractLocal(click3d).normalizeLocal();

        Ray ray = new Ray(click3d, dir);
        CollisionResults results = new CollisionResults();
        app.getRootNode().collideWith(ray, results);

        if (results.size() == 0) {
            lastPickedInfo = "Nenhuma geometria encontrada.";
            clearHighlights();
            updateHud(lastPickedInfo);
            return;
        }

        // Walk results from closest to find a meaningful texture
        for (int i = 0; i < results.size(); i++) {
            CollisionResult hit = results.getCollision(i);
            Geometry geom = hit.getGeometry();
            if (geom == null)
                continue;

            // Skip our own highlight overlays
            if (geom.getName() != null && geom.getName().startsWith("_hl_"))
                continue;

            Material mat = geom.getMaterial();
            if (mat == null)
                continue;

            Set<String> texNames = collectTextureNames(mat);
            String info = extractTextureInfo(geom, mat);

            if (info != null && !texNames.isEmpty()) {
                lastPickedInfo = info;
                int count = highlightAllUsing(texNames);
                lastPickedInfo += "\n[Geometrias com esta textura: " + count + "]";
                updateHud(lastPickedInfo);
                logger.debug("Picked: {}", lastPickedInfo);
                return;
            }
        }

        lastPickedInfo = "Sem textura atribuida.";
        clearHighlights();
        updateHud(lastPickedInfo);
    }

    // ---------------------------------------------------------------
    // Texture name helpers
    // ---------------------------------------------------------------

    /** Collect all non-empty texture names from a material. */
    private Set<String> collectTextureNames(Material mat) {
        Set<String> names = new HashSet<String>();
        for (String paramName : PARAM_NAMES) {
            MatParam param = mat.getParam(paramName);
            if (param != null && param.getValue() instanceof Texture) {
                String name = getTextureName((Texture) param.getValue());
                if (name != null)
                    names.add(name);
            }
        }
        return names;
    }

    private String getTextureName(Texture tex) {
        String name = tex.getName();
        if ((name == null || name.isEmpty()) && tex.getKey() != null) {
            name = tex.getKey().getName();
        }
        return (name != null && !name.isEmpty()) ? name : null;
    }

    /** Check whether a geometry uses any of the given texture names. */
    private boolean geomUsesAnyTexture(Geometry geom, Set<String> textureNames) {
        Material mat = geom.getMaterial();
        if (mat == null)
            return false;
        for (String paramName : PARAM_NAMES) {
            MatParam param = mat.getParam(paramName);
            if (param != null && param.getValue() instanceof Texture) {
                String name = getTextureName((Texture) param.getValue());
                if (name != null && textureNames.contains(name))
                    return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Highlight logic
    // ---------------------------------------------------------------

    /** Remove all existing highlight overlays from the scene. */
    public void clearHighlights() {
        highlightNode.detachAllChildren();
        highlightNode.removeFromParent();
        highlightedTextureNames.clear();
    }

    /**
     * Scan the entire scene and add a wireframe overlay for every geometry
     * that uses any of the given texture names.
     *
     * @return the number of matched geometries.
     */
    private int highlightAllUsing(final Set<String> textureNames) {
        clearHighlights();
        highlightedTextureNames.addAll(textureNames);

        final List<Geometry> matches = new ArrayList<Geometry>();

        app.getRootNode().depthFirstTraversal(new SceneGraphVisitor() {
            @Override
            public void visit(Spatial spatial) {
                if (!(spatial instanceof Geometry))
                    return;
                Geometry geom = (Geometry) spatial;
                // Skip highlight overlays and sky dome
                String geoName = geom.getName();
                if (geoName != null && (geoName.startsWith("_hl_") || geoName.startsWith("_sky")))
                    return;
                if (geomUsesAnyTexture(geom, textureNames)) {
                    matches.add(geom);
                }
            }
        });

        if (matches.isEmpty())
            return 0;

        // Create a shared wireframe material
        Material hlMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        hlMat.setColor("Color", HIGHLIGHT_COLOR);
        hlMat.getAdditionalRenderState().setWireframe(true);
        // Push wireframe slightly towards camera to avoid z-fighting
        hlMat.getAdditionalRenderState().setPolyOffset(-1, -1);

        for (Geometry src : matches) {
            Geometry hl = new Geometry("_hl_" + src.getName(), src.getMesh());
            // Use world transform since highlightNode sits directly under rootNode
            hl.setLocalTransform(src.getWorldTransform().clone());
            hl.setMaterial(hlMat);
            highlightNode.attachChild(hl);
        }

        app.getRootNode().attachChild(highlightNode);
        return matches.size();
    }

    // ---------------------------------------------------------------
    // Info extraction (display only)
    // ---------------------------------------------------------------

    private String extractTextureInfo(Geometry geom, Material mat) {
        StringBuilder sb = new StringBuilder();
        sb.append("Objeto: ").append(geom.getName()).append("\n");

        boolean found = false;
        for (String paramName : PARAM_NAMES) {
            MatParam param = mat.getParam(paramName);
            if (param != null && param.getValue() instanceof Texture) {
                String texName = getTextureName((Texture) param.getValue());
                if (texName != null) {
                    sb.append(paramName).append(": ").append(texName).append("\n");
                    found = true;
                }
            }
        }

        if (mat.getMaterialDef() != null) {
            sb.append("Material: ").append(mat.getMaterialDef().getName());
        }

        return found ? sb.toString() : sb.append("(sem textura)").toString();
    }

    private void updateHud(String info) {
        HudState hud = getStateManager().getState(HudState.class);
        if (hud != null) {
            hud.setPickerInfo(info);
        }
    }
}
