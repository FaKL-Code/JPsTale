package org.pstale.app;

import org.pstale.entity.field.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;

/**
 * Renders a sky dome using the game's original sky textures.
 * <p>
 * Each field's {@code BackImageCode} array contains three indices that
 * map into {@link #SKY_TEXTURES}. The dome displays one of them based
 * on the current {@link LightState} power.
 */
public class SkyboxAppState extends BaseAppState {

    private static final Logger logger = LoggerFactory.getLogger(SkyboxAppState.class);

    /**
     * BackImageCode → file path, matching the original Priston Tale client.
     * Index positions correspond to the constants in FieldConstant.
     */
    private static final String[] SKY_TEXTURES = {
            "sky/RainSky.bmp", // 0x00 RAIN
            "sky/Nsky00.tga", // 0x01 NIGHT
            "sky/Dsky00.tga", // 0x02 DAY
            "sky/Fsky00.tga", // 0x03 GLOWDAY (fire/sunset)
            "sky/DGsky00.tga", // 0x04 DESERT
            "sky/FGsky00.tga", // 0x05 GLOWDESERT
            "sky/DNsky00.tga", // 0x06 NIGHTDESERT
            "sky/Ruinsky.bmp", // 0x07 RUIN1
            "sky/RuinSky2.bmp", // 0x08 RUIN2
            "sky/RuinNsky.bmp", // 0x09 NIGHTRUIN1
            "sky/RuinSky2-n.bmp", // 0x0A NIGHTRUIN2
            null, // 0x0B GLOWRUIN1 (not in this client)
            null, // 0x0C GLOWRUIN2 (not in this client)
            null, null, null, null, // 0x0D-0x10 unused
            "sky/forever-Nsky00.tga", // 0x11 NIGHTFALL
            "sky/forever-Fsky00.tga", // 0x12 DAYFALL
            "sky/forever-Gsky00.tga",// 0x13 GLOWFALL
    };

    private Node skyNode;
    private AssetManager assetManager;
    private Camera camera;

    private Texture dayTexture;
    private Texture nightTexture;
    private Texture glowTexture;

    private Geometry skyDome;
    private Material skyMat;

    private float lastPower = -1f;

    @Override
    protected void initialize(Application app) {
        assetManager = app.getAssetManager();
        camera = app.getCamera();

        skyNode = new Node("SkyboxNode");
        skyNode.setQueueBucket(Bucket.Sky);
        skyNode.setShadowMode(ShadowMode.Off);

        // Inverted sphere (normals point inward) scaled each frame.
        Sphere sphere = new Sphere(32, 32, 1f, false, true);
        skyDome = new Geometry("SkyDome", sphere);

        skyMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        skyMat.setColor("Color", new ColorRGBA(0.4f, 0.6f, 1f, 1f));
        skyMat.getAdditionalRenderState().setDepthWrite(false);
        skyMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);

        skyDome.setMaterial(skyMat);
        skyDome.setCullHint(CullHint.Never);

        skyNode.attachChild(skyDome);
        logger.info("SkyDome initialized");
    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void onEnable() {
        ((SimpleApplication) getApplication()).getRootNode().attachChild(skyNode);
    }

    @Override
    protected void onDisable() {
        skyNode.removeFromParent();
    }

    /**
     * Load sky textures for the given field.
     */
    public void loadSky(Field field) {
        if (field == null)
            return;

        int[] codes = field.getBackImageCode();
        logger.info("BackImageCode = [{}, {}, {}]", codes[0], codes[1], codes[2]);

        // codes: [day, glow, night] based on FIELD.txt patterns like [2,3,1]
        dayTexture = loadSkyTexture(codes[0]);
        glowTexture = loadSkyTexture(codes[1]);
        nightTexture = loadSkyTexture(codes[2]);

        logger.info("Sky textures loaded — day={}, glow={}, night={}",
                dayTexture != null, glowTexture != null, nightTexture != null);

        // Apply whatever loaded first so the dome is never blank
        Texture first = dayTexture != null ? dayTexture
                : (glowTexture != null ? glowTexture : nightTexture);
        if (first != null) {
            skyMat.setTexture("ColorMap", first);
            if (skyMat.getParam("Color") != null) {
                skyMat.clearParam("Color");
            }
        }

        lastPower = -1f;
    }

    @Override
    public void update(float tpf) {
        skyNode.setLocalTranslation(camera.getLocation());

        float far = camera.getFrustumFar();
        float near = camera.getFrustumNear();
        skyNode.setLocalScale((near + far) / 2f);

        LightState lightState = getStateManager().getState(LightState.class);
        if (lightState == null)
            return;

        float power = lightState.getLightPower();
        if (Math.abs(power - lastPower) < 0.01f)
            return;
        lastPower = power;

        Texture tex;
        if (power > 0.7f) {
            tex = dayTexture;
        } else if (power > 0.3f) {
            tex = glowTexture != null ? glowTexture : dayTexture;
        } else {
            tex = nightTexture != null ? nightTexture
                    : (glowTexture != null ? glowTexture : dayTexture);
        }

        if (tex != null) {
            skyMat.setTexture("ColorMap", tex);
            if (skyMat.getParam("Color") != null) {
                skyMat.clearParam("Color");
            }
        }
    }

    /**
     * Try to load a sky texture by BackImageCode.
     * Tries the TGA/BMP path first, then falls back to the other extension.
     */
    private Texture loadSkyTexture(int code) {
        if (code < 0 || code >= SKY_TEXTURES.length) {
            logger.warn("Sky code {} out of range", code);
            return null;
        }
        String path = SKY_TEXTURES[code];
        if (path == null) {
            logger.debug("Sky code {} has no file mapping", code);
            return null;
        }

        // Try primary format
        Texture tex = tryLoad(path);
        if (tex != null)
            return tex;

        // Try alternate extension (tga↔bmp)
        String alt;
        if (path.endsWith(".tga")) {
            alt = path.substring(0, path.length() - 4) + ".bmp";
        } else if (path.endsWith(".bmp")) {
            alt = path.substring(0, path.length() - 4) + ".tga";
        } else {
            return null;
        }
        tex = tryLoad(alt);
        if (tex != null)
            return tex;

        logger.warn("Could not load sky texture for code {}: tried '{}' and '{}'", code, path, alt);
        return null;
    }

    private Texture tryLoad(String path) {
        try {
            Texture tex = assetManager.loadTexture(path);
            logger.info("Loaded sky texture: {}", path);
            return tex;
        } catch (Exception e) {
            logger.debug("Failed to load '{}': {}", path, e.getMessage());
            return null;
        }
    }
}
