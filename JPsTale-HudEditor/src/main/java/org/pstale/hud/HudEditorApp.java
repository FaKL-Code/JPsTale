package org.pstale.hud;

import org.pstale.assets.AssetFactory;
import org.pstale.utils.FileLocator;

import com.jme3.app.SimpleApplication;
import com.jme3.input.KeyInput;
import com.jme3.math.ColorRGBA;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.focus.FocusNavigationFunctions;
import com.simsilica.lemur.input.InputMapper;

/**
 * jME3 application for the HUD Editor.
 * Sets up a 2D orthographic view for editing HUD layouts.
 */
public class HudEditorApp extends SimpleApplication {

    public HudEditorApp() {
        super(new HudEditorState());
    }

    @Override
    public void simpleInitApp() {
        // Register client asset root
        String clientRoot = settings.getString("ClientRoot");
        if (clientRoot != null) {
            assetManager.registerLocator(clientRoot, FileLocator.class);
        }

        AssetFactory.setAssetManager(assetManager);

        // Initialize Lemur GUI
        GuiGlobals.initialize(this);
        InputMapper inputMapper = GuiGlobals.getInstance().getInputMapper();
        inputMapper.removeMapping(FocusNavigationFunctions.F_ACTIVATE, KeyInput.KEY_SPACE);

        // Dark background so HUD elements are clearly visible
        viewPort.setBackgroundColor(new ColorRGBA(0.15f, 0.15f, 0.18f, 1f));

        // No fly camera — we use mouse for drag/drop
        inputManager.setCursorVisible(true);

        // Set up orthographic camera matching the screen resolution
        cam.setParallelProjection(true);
        float w = cam.getWidth();
        float h = cam.getHeight();
        cam.setFrustum(-1000, 1000, 0, w, h, 0);
    }
}
