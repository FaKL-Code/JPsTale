package org.pstale.app;

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
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;

/**
 * Allows the user to pick a surface on the map and inspect the texture file
 * name applied to it.
 * <p>
 * Press <b>T</b> to toggle picker mode. While active, a <b>left-click</b>
 * casts a ray from the cursor and reports the texture of the closest geometry.
 * The result is forwarded to {@link HudState} for display.
 */
public class TexturePickerAppState extends BaseAppState implements ActionListener {

    private static final Logger logger = LoggerFactory.getLogger(TexturePickerAppState.class);

    private static final String MAPPING_TOGGLE = "TexturePicker_Toggle";
    private static final String MAPPING_PICK = "TexturePicker_Pick";

    private SimpleApplication app;
    private boolean pickerActive = false;

    /** The last picked texture info (forwarded to HudState). */
    private String lastPickedInfo = null;

    @Override
    protected void initialize(Application application) {
        this.app = (SimpleApplication) application;
    }

    @Override
    protected void cleanup(Application application) {
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
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!isPressed)
            return; // react on key-down only

        if (MAPPING_TOGGLE.equals(name)) {
            pickerActive = !pickerActive;
            logger.info("Texture picker {}", pickerActive ? "ON" : "OFF");

            HudState hud = getStateManager().getState(HudState.class);
            if (hud != null) {
                if (pickerActive) {
                    hud.setPickerInfo("Seletor de Textura: ATIVO (clique do meio para selecionar)");
                } else {
                    hud.setPickerInfo(null);
                }
            }
            return;
        }

        if (MAPPING_PICK.equals(name) && pickerActive) {
            doPick();
        }
    }

    /**
     * Can also be triggered externally (e.g. from HudState).
     */
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
            updateHud(lastPickedInfo);
            return;
        }

        // Walk results from closest to find a meaningful texture
        for (int i = 0; i < results.size(); i++) {
            CollisionResult hit = results.getCollision(i);
            if (!(hit.getGeometry() instanceof Geometry))
                continue;

            Geometry geom = hit.getGeometry();
            Material mat = geom.getMaterial();
            if (mat == null)
                continue;

            String info = extractTextureInfo(geom, mat, hit.getContactPoint());
            if (info != null) {
                lastPickedInfo = info;
                updateHud(lastPickedInfo);
                logger.debug("Picked: {}", lastPickedInfo);
                return;
            }
        }

        lastPickedInfo = "Sem textura atribuida.";
        updateHud(lastPickedInfo);
    }

    private String extractTextureInfo(Geometry geom, Material mat, Vector3f contactPoint) {
        StringBuilder sb = new StringBuilder();
        sb.append("Objeto: ").append(geom.getName()).append("\n");

        // Try common texture parameter names
        String[] paramNames = { "DiffuseMap", "ColorMap", "LightMap",
                "Tex1", "Tex2", "Tex3", "Tex4" };

        boolean found = false;
        for (String paramName : paramNames) {
            MatParam param = mat.getParam(paramName);
            if (param != null && param.getValue() instanceof Texture) {
                Texture tex = (Texture) param.getValue();
                String texName = tex.getName();
                if (texName == null || texName.isEmpty()) {
                    // Try the asset key
                    if (tex.getKey() != null) {
                        texName = tex.getKey().getName();
                    }
                }
                if (texName != null && !texName.isEmpty()) {
                    sb.append(paramName).append(": ").append(texName).append("\n");
                    found = true;
                }
            }
        }

        // Material definition name
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
