package org.pstale.app;

import org.pstale.assets.AssetFactory;
import org.pstale.constants.SceneConstants;
import org.pstale.gui.Style;
import org.pstale.utils.FileLocator;

import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.Vector3f;

public class FieldApp extends SimpleApplication implements ActionListener {

    private boolean moveUp = false;
    private boolean moveDown = false;

    public FieldApp() {
        super(new LoadingAppState(), new StatsAppState(), new FlyCamAppState(), new ScreenshotAppState());
    }

    @Override
    public void simpleInitApp() {
        /**
         * 客户端资源的根目录
         */
        String clientRoot = settings.getString("ClientRoot");
        if (clientRoot != null) {
            assetManager.registerLocator(clientRoot, FileLocator.class);
        }

        /**
         * 服务端资源的根目录
         */
        String serverRoot = settings.getString("ServerRoot");
        boolean checkServer = settings.getBoolean("CheckServer");
        if (checkServer && serverRoot != null) {
            assetManager.registerLocator(serverRoot, FileLocator.class);

            LoadingAppState.CHECK_SERVER = true;
            LoadingAppState.SERVER_ROOT = serverRoot;
        }

        /**
         * 是否使用灯光、法线
         */
        boolean useLight = settings.getBoolean("UseLight");
        SceneConstants.USE_LIGHT = useLight;
        LightState.USE_LIGHT = useLight;

        /**
         * 设置模型工厂
         */
        AssetFactory.setAssetManager(assetManager);

        /**
         * 初始化Lemur样式
         */
        Style.initStyle(this);

        /**
         * Configurar distância de renderização (frustum far plane)
         */
        int renderDist = settings.getInteger("RenderDistance");
        if (renderDist > 0) {
            SceneConstants.RENDER_DISTANCE = renderDist;
        }
        cam.setFrustumFar(SceneConstants.RENDER_DISTANCE);
        cam.setFrustumNear(1f);

        flyCam.setMoveSpeed(1000);
        flyCam.setZoomSpeed(-50);
        flyCam.setDragToRotate(true);
        inputManager.addMapping("FLYCAM_RotateDrag", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));

        // Remove default FlyCam vertical mappings (they move along camera direction,
        // not world Y)
        if (inputManager.hasMapping("FLYCAM_Rise")) {
            inputManager.deleteTrigger("FLYCAM_Rise", new KeyTrigger(KeyInput.KEY_SPACE));
        }
        if (inputManager.hasMapping("FLYCAM_Lower")) {
            inputManager.deleteTrigger("FLYCAM_Lower", new KeyTrigger(KeyInput.KEY_LCONTROL));
        }

        // Vertical movement: Space = up, Ctrl = down (purely along world Y axis)
        inputManager.addMapping("VerticalUp", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("VerticalDown", new KeyTrigger(KeyInput.KEY_LCONTROL),
                new KeyTrigger(KeyInput.KEY_RCONTROL));
        inputManager.addListener(this, "VerticalUp", "VerticalDown");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if ("VerticalUp".equals(name)) {
            moveUp = isPressed;
        } else if ("VerticalDown".equals(name)) {
            moveDown = isPressed;
        }
    }

    @Override
    public void simpleUpdate(float tpf) {
        float speed = flyCam.getMoveSpeed();
        if (moveUp) {
            cam.setLocation(cam.getLocation().add(0, speed * tpf, 0));
        }
        if (moveDown) {
            cam.setLocation(cam.getLocation().add(0, -speed * tpf, 0));
        }
    }

}
