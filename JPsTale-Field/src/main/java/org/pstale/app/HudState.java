package org.pstale.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.pstale.app.ModelViewerState.Category;
import org.pstale.app.ModelViewerState.ModelEntry;
import org.pstale.entity.field.Field;
import org.pstale.utils.GameDate;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.simsilica.lemur.Action;
import com.simsilica.lemur.ActionButton;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Checkbox;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.DefaultRangedValueModel;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.LayerComparator;
import com.simsilica.lemur.ListBox;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.Slider;
import com.simsilica.lemur.TabbedPanel;
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.SpringGridLayout;
import com.simsilica.lemur.core.VersionedList;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.lemur.event.CursorEventControl;
import com.simsilica.lemur.event.DragHandler;
import com.simsilica.lemur.style.ElementId;

/**
 * 主界面
 * 
 * @author yanmaoyuan
 *
 */
public class HudState extends BaseAppState {

    private ListBox<String> listBox;
    private VersionedList<String> fieldList = new VersionedList<String>();

    private VersionedReference<Boolean> showAxisRef;
    private VersionedReference<Boolean> showMeshRef;
    private VersionedReference<Boolean> collisionRef;
    private VersionedReference<Boolean> showEffectsRef;
    private VersionedReference<Boolean> showAlphaRef;
    private VersionedReference<Double> speedRef;

    /** Tracks whether alpha highlight is currently active */
    private boolean alphaHighlightActive = false;
    /** Stores original materials so we can restore them */
    private Map<Geometry, Material> originalMaterials = new HashMap<>();

    private VersionedList<String> npcList = new VersionedList<String>();
    private VersionedList<String> spawnPointList = new VersionedList<String>();
    private VersionedList<String> monsterList = new VersionedList<String>();
    private VersionedList<String> bossList = new VersionedList<String>();

    private float width;// 屏幕宽度
    private float height;// 屏幕高度

    private Node guiNode;

    /** Picker info label (bottom-center) */
    private Label pickerLabel;
    private Container pickerWindow;

    /** Help overlay */
    private Container helpWindow;
    private boolean helpVisible = false;

    public HudState() {
        guiNode = new Node("LemurGUI");
    }

    @Override
    protected void initialize(Application app) {
        // 记录屏幕高宽
        Camera cam = app.getCamera();
        width = cam.getWidth();
        height = cam.getHeight();

        /**
         * 地区列表窗口
         */
        createFieldListBox();
        /**
         * 配置面板
         */
        createOptionPanel();
        /**
         * 小地图
         */
        createMiniMap();

        /**
         * Painel de controle de tempo
         */
        createTimelinePanel();
        /**
         * Painel de bookmarks de camera
         */
        createBookmarkPanel();

        /**
         * Painel de informacao de textura
         */
        createPickerPanel();

        /**
         * Painel de ajuda (atalhos)
         */
        createHelpPanel();

        /**
         * Painel do visualizador de modelos
         */
        createModelViewerPanel();

        if (LoadingAppState.CHECK_SERVER) {
            createCreaturePanel();
        }
    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void onEnable() {
        SimpleApplication simpleApp = (SimpleApplication) getApplication();
        simpleApp.getGuiNode().attachChild(guiNode);
    }

    @Override
    protected void onDisable() {
        guiNode.removeFromParent();
    }

    public void update(float tpf) {
        if (showAxisRef.update()) {
            AxisAppState axis = getStateManager().getState(AxisAppState.class);
            if (axis != null) {
                axis.setEnabled(showAxisRef.get());
            }
        }
        if (showMeshRef.update()) {
            CollisionState collision = getStateManager().getState(CollisionState.class);
            if (collision != null) {
                collision.debug(showMeshRef.get());
            }
        }
        if (collisionRef.update()) {
            CollisionState collision = getStateManager().getState(CollisionState.class);
            if (collision != null) {
                collision.toggle(collisionRef.get());
            }
        }
        if (showEffectsRef.update()) {
            AmbientAppState ambient = getStateManager().getState(AmbientAppState.class);
            if (ambient != null) {
                ambient.setEnabled(showEffectsRef.get());
            }
        }
        if (showAlphaRef.update()) {
            toggleAlphaHighlight(showAlphaRef.get());
        }
        if (speedRef.update()) {
            double value = speedRef.get();
            SimpleApplication app = (SimpleApplication) getApplication();
            if (app.getFlyByCamera() != null) {
                app.getFlyByCamera().setMoveSpeed((float) value);
            }
        }

        // Search filter for model viewer — removed (button-driven now)

        // Timeline: sync slider ↔ game time
        LightState ls = getStateManager().getState(LightState.class);
        if (ls != null && timeSlider != null && timeLabel != null) {
            GameDate gd = ls.getGameDate();
            if (timeSliderRef.update()) {
                // User moved slider → set game time
                float t = (float) timeSlider.getModel().getValue();
                gd.setTimeOfDayNormalized(t);
            } else {
                // Game time progressing → update slider
                double current = gd.getTimeOfDayNormalized();
                timeSlider.getModel().setValue(current);
            }
            timeLabel.setText(String.format("%02d:%02d", gd.getHour(), gd.getMinute()));
        }

    }

    /**
     * 将小地图的标题和内容做成变量，这样就可以通过
     * {@code public void setMiniMap(Texture titleRes, Texture mapRes)}
     * 方法来修改小地图了。
     */
    private Container title;
    private Container map;

    /**
     * 创建小地图面板
     */
    private void createMiniMap() {
        Container window = new Container("glass");
        guiNode.attachChild(window);

        // 使其可以拖拽
        CursorEventControl.addListenersToSpatial(window, new DragHandler());

        // 标题
        // 地图的Title

        title = new Container("glass");
        title.setPreferredSize(new Vector3f(160, 24, 1));
        window.addChild(title);

        // 地图的图片
        map = new Container("glass");
        map.setPreferredSize(new Vector3f(160, 160, 1));
        window.addChild(map);

        // 限制窗口的最小宽度
        Vector3f hudSize = new Vector3f(160, 160, 0);
        hudSize.maxLocal(window.getPreferredSize());
        window.setPreferredSize(hudSize);

        // 将窗口添加到屏幕右下角。
        window.setLocalTranslation(width - 20 - hudSize.x, hudSize.y + 20, 0);
    }

    /**
     * 设置小地图
     * 
     * @param titleRes
     * @param mapRes
     */
    public void setMiniMap(Texture titleRes, Texture mapRes) {
        if (titleRes != null) {
            title.setBackground(new QuadBackgroundComponent(titleRes, 5, 5, 0.02f, false));
        } else {
            title.setBackground(new QuadBackgroundComponent(new ColorRGBA(0.1f, 0.1f, 0.1f, 0.5f), 5, 5, 0.02f, false));
        }

        if (mapRes != null) {
            map.setBackground(new QuadBackgroundComponent(mapRes, 5, 5, 0.02f, false));
        } else {
            map.setBackground(new QuadBackgroundComponent(new ColorRGBA(0.1f, 0.1f, 0.1f, 0.5f), 5, 5, 0.02f, false));
        }
    }

    /**
     * 创建区域列表
     */
    private void createFieldListBox() {
        /**
         * 地区列表窗口
         */
        Container window = new Container("glass");
        // 标题
        window.addChild(new Label("Lista de Regioes", new ElementId("title"), "glass"));
        // 初始化列表数据
        final DataState dataState = getStateManager().getState(DataState.class);
        if (dataState != null) {
            Field[] fields = dataState.getFields();
            for (int i = 0; i < fields.length; i++) {
                fieldList.add(fields[i].getId() + ":" + fields[i].getTitle());
            }
        }

        // 创建一个ListBox控件，并添加到窗口中
        listBox = new ListBox<String>(fieldList, "glass");
        listBox.setVisibleItems(12);
        window.addChild(listBox);

        // 载入按钮
        final Action load = new Action("Carregar Mapa") {
            @Override
            public void execute(Button b) {
                Integer selected = listBox.getSelectionModel().getSelection();
                if (selected != null && selected < fieldList.size()) {
                    if (dataState != null) {

                        // 获得被选中的区域
                        Field[] fields = dataState.getFields();
                        final Field field = fields[selected];

                        // 载入
                        getApplication().enqueue(new Callable<Void>() {
                            public Void call() {
                                LoaderAppState state = getStateManager().getState(LoaderAppState.class);
                                if (state != null) {
                                    state.loadModel(field);
                                }
                                return null;
                            }
                        });
                    }
                }
            }
        };

        // 编辑地图
        final Action edit = new Action("Modificar") {
            @Override
            public void execute(Button b) {
                getApplication().enqueue(new Callable<Void>() {
                    public Void call() {
                        LoaderAppState state = getStateManager().getState(LoaderAppState.class);
                        if (state != null) {
                            state.reloadModel();
                        }
                        return null;
                    }
                });
            }
        };

        // 创建按钮面板
        Container buttons = new Container(new SpringGridLayout(Axis.X, Axis.Y, FillMode.Even, FillMode.Even));
        window.addChild(buttons);
        buttons.addChild(new ActionButton(load, "glass"));
        buttons.addChild(new ActionButton(edit, "glass"));

        // 限制窗口的最小宽度
        Vector3f hudSize = new Vector3f(160, 0, 0);
        hudSize.maxLocal(window.getPreferredSize());
        window.setPreferredSize(hudSize);

        // 将窗口添加到屏幕右上角。
        window.setLocalTranslation(width - 20 - hudSize.x, height - 20, 0);

        // 使其可以拖拽
        CursorEventControl.addListenersToSpatial(window, new DragHandler());

        guiNode.attachChild(window);
    }

    /**
     * Create a top panel for some stats toggles.
     */
    private void createOptionPanel() {
        Container window = new Container("glass");
        window.setLocalTranslation(5, height - 20, 0);
        guiNode.attachChild(window);

        // 使其可以拖拽
        CursorEventControl.addListenersToSpatial(window, new DragHandler());

        window.setBackground(new QuadBackgroundComponent(new ColorRGBA(0, 0f, 0f, 0.5f), 5, 5, 0.02f, false));
        window.addChild(new Label("Opcoes", new ElementId("header"), "glass"));
        window.addChild(new Panel(2, 2, ColorRGBA.White, "glass")).setUserData(LayerComparator.LAYER, 2);

        // Adding components returns the component so we can set other things
        // if we want.
        Checkbox temp = window.addChild(new Checkbox("Mostrar Eixos"));
        temp.setChecked(true);
        showAxisRef = temp.getModel().createReference();

        temp = window.addChild(new Checkbox("Mostrar Grade"));
        temp.setChecked(false);
        showMeshRef = temp.getModel().createReference();

        temp = window.addChild(new Checkbox("Colisao"));
        temp.setChecked(false);
        collisionRef = temp.getModel().createReference();

        temp = window.addChild(new Checkbox("Mostrar Efeitos"));
        temp.setChecked(false);
        showEffectsRef = temp.getModel().createReference();

        temp = window.addChild(new Checkbox("Mostrar Alpha Channels"));
        temp.setChecked(false);
        showAlphaRef = temp.getModel().createReference();

        window.addChild(new Label("Vel. Camera:"));
        DefaultRangedValueModel model = new DefaultRangedValueModel(0, 1000, 1000);
        final Slider redSlider = new Slider(model, "glass");
        redSlider.setBackground(new QuadBackgroundComponent(new ColorRGBA(0.5f, 0.1f, 0.1f, 0.5f), 5, 5, 0.02f, false));
        speedRef = window.addChild(redSlider).getModel().createReference();

        window.addChild(new Panel(2, 2, ColorRGBA.White, "glass")).setUserData(LayerComparator.LAYER, 2);
        window.addChild(new ActionButton(new Action("? Atalhos (H)") {
            @Override
            public void execute(Button b) {
                toggleHelp();
            }
        }, "glass"));

        // 限制窗口的最小宽度
        Vector3f hudSize = new Vector3f(200, 0, 0);
        hudSize.maxLocal(window.getPreferredSize());
        window.setPreferredSize(hudSize);
    }

    /**
     * 生态信息面板
     */
    private void createCreaturePanel() {

        // 10:01:29,704 DEBUG [LoaderAppState] 生物数量上限:300
        // 10:01:29,705 DEBUG [LoaderAppState] 刷怪时间间隔:31
        // 10:01:29,705 DEBUG [LoaderAppState] 每次最多刷怪:3
        // 10:01:29,705 DEBUG [LoaderAppState] 怪物:独眼蜈蚣 几率:20
        // 10:01:29,705 DEBUG [LoaderAppState] 怪物:僵尸 几率:25
        // 10:01:29,705 DEBUG [LoaderAppState] 怪物:梦魇树 几率:30
        // 10:01:29,705 DEBUG [LoaderAppState] 怪物:独眼魔人 几率:5
        // 10:01:29,705 DEBUG [LoaderAppState] 怪物:青精灵 几率:15
        // 10:01:29,705 DEBUG [LoaderAppState] 怪物:浮灵 几率:5
        // 10:01:29,705 DEBUG [LoaderAppState] BOSS:超级盗尸贼 伴生小怪:僵尸*8 刷新时段:4
        // 10:01:29,739 DEBUG [LoaderAppState] BOSS:超级刀斧手 伴生小怪:青精灵*8 刷新时段:4

        Container window = new Container("glass");
        window.addChild(new Label("Regiao", new ElementId("title"), "glass"));

        // 初始化列表数据
        TabbedPanel tabPanel = new TabbedPanel("glass");
        window.addChild(tabPanel);

        /**
         * 生态信息界面
         */
        Container creaturePanel = new Container("glass");
        tabPanel.addTab("Monstros", creaturePanel);

        ListBox<String> mlistBox = new ListBox<String>(monsterList, "glass");
        mlistBox.setVisibleItems(10);
        creaturePanel.addChild(mlistBox);

        /**
         * 生态信息界面
         */
        Container bossPanel = new Container("glass");
        tabPanel.addTab("Chefes", bossPanel);

        ListBox<String> blistBox = new ListBox<String>(bossList, "glass");
        blistBox.setVisibleItems(10);
        bossPanel.addChild(blistBox);

        /**
         * 刷怪点信息界面
         */
        Container spawnPointPanel = new Container("glass");
        tabPanel.addTab("Spawns", spawnPointPanel);

        ListBox<String> spplistBox = new ListBox<String>(spawnPointList, "glass");
        spplistBox.setVisibleItems(10);
        spawnPointPanel.addChild(spplistBox);

        /**
         * NPC界面
         */
        Container npcPanel = new Container("glass");
        tabPanel.addTab("NPCs", npcPanel);

        // 创建一个ListBox控件，并添加到窗口中
        ListBox<String> listBox = new ListBox<String>(npcList, "glass");
        listBox.setVisibleItems(10);
        npcPanel.addChild(listBox);

        /**
         * 限制窗口的最小宽度
         */
        Vector3f hudSize = new Vector3f(200, 250, 0);
        hudSize.maxLocal(tabPanel.getPreferredSize());
        tabPanel.setPreferredSize(hudSize);

        // 将窗口添加到屏幕右上角。
        window.setLocalTranslation(5, height - 200, 0);
        // 使其可以拖拽
        CursorEventControl.addListenersToSpatial(window, new DragHandler());
        guiNode.attachChild(window);
    }

    public void setNpc(List<String> npcs) {
        npcList.clear();
        if (npcs != null) {
            npcList.addAll(npcs);
        }
    }

    public void setMonster(List<String> monster) {
        monsterList.clear();
        if (monster != null) {
            monsterList.addAll(monster);
        }
    }

    public void setBoss(List<String> boss) {
        bossList.clear();
        if (boss != null) {
            bossList.addAll(boss);
        }
    }

    public void setSpawnPoint(List<String> spp) {
        spawnPointList.clear();
        if (spp != null) {
            spawnPointList.addAll(spp);
        }
    }

    // ========================================================================
    // Timeline Control
    // ========================================================================

    private Slider timeSlider;
    private Label timeLabel;
    private VersionedReference<Double> timeSliderRef;
    private boolean timeSliderDragging = false;

    private void createTimelinePanel() {
        Container window = new Container("glass");
        window.addChild(new Label("Horario", new ElementId("title"), "glass"));

        // Time label
        timeLabel = window.addChild(new Label("00:00"));

        // Time slider (0..1 normalized)
        DefaultRangedValueModel timeModel = new DefaultRangedValueModel(0, 1, 0.25);
        timeSlider = window.addChild(new Slider(timeModel, "glass"));
        timeSliderRef = timeSlider.getModel().createReference();

        // Buttons row
        Container buttons = new Container(new SpringGridLayout(Axis.X, Axis.Y, FillMode.Even, FillMode.Even));
        window.addChild(buttons);

        buttons.addChild(new ActionButton(new Action("Pausar/Continuar") {
            @Override
            public void execute(Button b) {
                LightState ls = getStateManager().getState(LightState.class);
                if (ls != null) {
                    GameDate gd = ls.getGameDate();
                    gd.setPaused(!gd.isPaused());
                }
            }
        }, "glass"));

        Vector3f hudSize = new Vector3f(200, 0, 0);
        hudSize.maxLocal(window.getPreferredSize());
        window.setPreferredSize(hudSize);

        window.setLocalTranslation(5, 200, 0);
        CursorEventControl.addListenersToSpatial(window, new DragHandler());
        guiNode.attachChild(window);
    }

    // ========================================================================
    // Camera Bookmarks
    // ========================================================================

    private VersionedList<String> bookmarkList = new VersionedList<String>();
    private ListBox<String> bookmarkListBox;

    private void createBookmarkPanel() {
        Container window = new Container("glass");
        window.addChild(new Label("Bookmarks", new ElementId("title"), "glass"));

        bookmarkListBox = new ListBox<String>(bookmarkList, "glass");
        bookmarkListBox.setVisibleItems(6);
        window.addChild(bookmarkListBox);

        // Name input
        final TextField nameField = window.addChild(new TextField("Bookmark"));

        // Buttons row
        Container buttons = new Container(new SpringGridLayout(Axis.X, Axis.Y, FillMode.Even, FillMode.Even));
        window.addChild(buttons);

        buttons.addChild(new ActionButton(new Action("Salvar") {
            @Override
            public void execute(Button b) {
                final CameraBookmarkAppState bm = getStateManager().getState(CameraBookmarkAppState.class);
                if (bm != null) {
                    String name = nameField.getText();
                    bm.addBookmark(name);
                    bm.saveBookmarks();
                    refreshBookmarkList();
                }
            }
        }, "glass"));

        buttons.addChild(new ActionButton(new Action("Ir") {
            @Override
            public void execute(Button b) {
                Integer sel = bookmarkListBox.getSelectionModel().getSelection();
                if (sel != null) {
                    final CameraBookmarkAppState bm = getStateManager().getState(CameraBookmarkAppState.class);
                    if (bm != null) {
                        bm.goToBookmark(sel);
                    }
                }
            }
        }, "glass"));

        buttons.addChild(new ActionButton(new Action("Remover") {
            @Override
            public void execute(Button b) {
                Integer sel = bookmarkListBox.getSelectionModel().getSelection();
                if (sel != null) {
                    final CameraBookmarkAppState bm = getStateManager().getState(CameraBookmarkAppState.class);
                    if (bm != null) {
                        bm.removeBookmark(sel);
                        bm.saveBookmarks();
                        refreshBookmarkList();
                    }
                }
            }
        }, "glass"));

        // Size and position
        Vector3f hudSize = new Vector3f(200, 0, 0);
        hudSize.maxLocal(window.getPreferredSize());
        window.setPreferredSize(hudSize);

        window.setLocalTranslation(5, 500, 0);
        CursorEventControl.addListenersToSpatial(window, new DragHandler());
        guiNode.attachChild(window);

        // Load existing bookmarks
        final CameraBookmarkAppState bm = getStateManager().getState(CameraBookmarkAppState.class);
        if (bm != null) {
            bm.loadBookmarks();
            refreshBookmarkList();
        }
    }

    private void refreshBookmarkList() {
        bookmarkList.clear();
        final CameraBookmarkAppState bm = getStateManager().getState(CameraBookmarkAppState.class);
        if (bm != null) {
            bookmarkList.addAll(bm.getBookmarkNames());
        }
    }

    // ========================================================================
    // Texture Picker Info
    // ========================================================================

    private void createPickerPanel() {
        pickerWindow = new Container("glass");
        pickerWindow.setBackground(new QuadBackgroundComponent(new ColorRGBA(0, 0, 0, 0.75f), 5, 5, 0.02f, false));

        pickerLabel = pickerWindow.addChild(new Label("[T] Seletor de Textura"));

        Vector3f hudSize = new Vector3f(350, 100, 0);
        hudSize.maxLocal(pickerWindow.getPreferredSize());
        pickerWindow.setPreferredSize(hudSize);

        // Centered at the top of the screen
        pickerWindow.setLocalTranslation(width / 2 - 175, height - 10, 0);
        CursorEventControl.addListenersToSpatial(pickerWindow, new DragHandler());
        guiNode.attachChild(pickerWindow);
    }

    /**
     * Called by {@link TexturePickerAppState} to update the displayed info.
     * Pass {@code null} to clear.
     */
    public void setPickerInfo(String info) {
        if (pickerLabel != null) {
            pickerLabel.setText(info != null ? info : "[T] Seletor de Textura");
        }
    }

    // ========================================================================
    // Help / Keybind Overlay
    // ========================================================================

    private void createHelpPanel() {
        helpWindow = new Container("glass");
        helpWindow.setBackground(new QuadBackgroundComponent(new ColorRGBA(0, 0, 0, 0.85f), 5, 5, 0.02f, false));

        helpWindow.addChild(new Label("Atalhos do Teclado", new ElementId("title"), "glass"));
        helpWindow.addChild(new Panel(2, 2, ColorRGBA.White, "glass")).setUserData(LayerComparator.LAYER, 2);

        String[][] binds = {
                { "W A S D", "Mover camera" },
                { "Mouse Direito", "Rotacionar camera" },
                { "Scroll", "Zoom in / out" },
                { "Espaco", "Subir (eixo Y)" },
                { "Ctrl", "Descer (eixo Y)" },
                { "", "" },
                { "T", "Ativar seletor de textura" },
                { "Clique Meio", "Selecionar textura" },
                { "", "" },
                { "F1", "Mostrar / Esconder HUD" },
                { "H", "Mostrar / Esconder esta ajuda" },
                { "F12", "Capturar tela (screenshot)" },
                { "", "" },
                { "--- Visualizador de Modelos ---", "" },
                { "R", "Ligar/desligar rotacao do modelo" },
        };

        for (String[] bind : binds) {
            if (bind[0].isEmpty()) {
                helpWindow.addChild(new Label(" "));
                continue;
            }
            Container row = new Container(new SpringGridLayout(Axis.X, Axis.Y, FillMode.First, FillMode.Even));
            Label key = new Label(bind[0]);
            key.setColor(new ColorRGBA(1f, 0.9f, 0.4f, 1f));
            row.addChild(key);
            row.addChild(new Label("  " + bind[1]));
            helpWindow.addChild(row);
        }

        helpWindow.addChild(new Label(" "));
        Label hint = helpWindow.addChild(new Label("Pressione H para fechar"));
        hint.setColor(new ColorRGBA(0.6f, 0.6f, 0.6f, 1f));

        Vector3f hudSize = new Vector3f(300, 0, 0);
        hudSize.maxLocal(helpWindow.getPreferredSize());
        helpWindow.setPreferredSize(hudSize);

        helpWindow.setLocalTranslation(width / 2 - 150, height / 2 + 150, 1);
        // Starts hidden
        helpVisible = false;
    }

    public void toggleHelp() {
        helpVisible = !helpVisible;
        if (helpVisible) {
            guiNode.attachChild(helpWindow);
        } else {
            helpWindow.removeFromParent();
        }
    }

    // ========================================================================
    // Model Viewer Panel
    // ========================================================================

    private ListBox<String> modelListBox;
    private VersionedList<String> modelEntryList = new VersionedList<String>();
    private List<ModelEntry> currentModelEntries = new ArrayList<ModelEntry>();
    private List<ModelEntry> allModelEntries = new ArrayList<ModelEntry>();
    private Container modelViewerWindow;
    private TextField searchField;

    private void createModelViewerPanel() {
        modelViewerWindow = new Container("glass");
        modelViewerWindow.addChild(new Label("Visualizador de Modelos", new ElementId("title"), "glass"));

        // Category buttons — split across two rows to avoid layout overflow
        Container catRow1 = new Container(new SpringGridLayout(Axis.X, Axis.Y, FillMode.Even, FillMode.Even));
        modelViewerWindow.addChild(catRow1);
        Container catRow2 = new Container(new SpringGridLayout(Axis.X, Axis.Y, FillMode.Even, FillMode.Even));
        modelViewerWindow.addChild(catRow2);

        final Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            final Category cat = categories[i];
            ActionButton btn = new ActionButton(new Action(cat.label) {
                @Override
                public void execute(Button b) {
                    populateModelList(cat);
                }
            }, "glass");
            if (i < 3) {
                catRow1.addChild(btn);
            } else {
                catRow2.addChild(btn);
            }
        }

        // Search filter row (field + button)
        Container searchRow = new Container(new SpringGridLayout(Axis.X, Axis.Y, FillMode.Even, FillMode.Even));
        modelViewerWindow.addChild(searchRow);
        searchField = new TextField("", "glass");
        searchField.setPreferredWidth(170);
        searchRow.addChild(searchField);
        searchRow.addChild(new ActionButton(new Action("Buscar") {
            @Override
            public void execute(Button b) {
                performGlobalSearch(searchField.getText());
            }
        }, "glass"));

        // Model list
        modelListBox = new ListBox<String>(modelEntryList, "glass");
        modelListBox.setVisibleItems(12);
        modelViewerWindow.addChild(modelListBox);

        // Action button row
        Container buttons = new Container(new SpringGridLayout(Axis.X, Axis.Y, FillMode.Even, FillMode.Even));
        modelViewerWindow.addChild(buttons);

        buttons.addChild(new ActionButton(new Action("Carregar") {
            @Override
            public void execute(Button b) {
                Integer sel = modelListBox.getSelectionModel().getSelection();
                if (sel != null && sel < currentModelEntries.size()) {
                    final ModelEntry entry = currentModelEntries.get(sel);
                    getApplication().enqueue(new Callable<Void>() {
                        public Void call() {
                            ModelViewerState viewer = getStateManager().getState(ModelViewerState.class);
                            if (viewer != null) {
                                viewer.loadModel(entry);
                            }
                            return null;
                        }
                    });
                }
            }
        }, "glass"));

        buttons.addChild(new ActionButton(new Action("Voltar ao Mapa") {
            @Override
            public void execute(Button b) {
                getApplication().enqueue(new Callable<Void>() {
                    public Void call() {
                        ModelViewerState viewer = getStateManager().getState(ModelViewerState.class);
                        if (viewer != null) {
                            viewer.exitViewer();
                        }
                        return null;
                    }
                });
            }
        }, "glass"));

        // Animation play/pause button
        buttons.addChild(new ActionButton(new Action("Animacao") {
            @Override
            public void execute(Button b) {
                getApplication().enqueue(new Callable<Void>() {
                    public Void call() {
                        ModelViewerState viewer = getStateManager().getState(ModelViewerState.class);
                        if (viewer != null) {
                            viewer.toggleAnimation();
                        }
                        return null;
                    }
                });
            }
        }, "glass"));

        // Do NOT populate at init — ModelViewerState is not yet attached.
        // List starts empty; user clicks a category button to populate.

        // Position — let Lemur compute preferred size naturally
        modelViewerWindow.setLocalTranslation(width - 260, height - 20, 0);
        CursorEventControl.addListenersToSpatial(modelViewerWindow, new DragHandler());
        guiNode.attachChild(modelViewerWindow);
    }

    private void populateModelList(Category category) {
        allModelEntries.clear();

        ModelViewerState viewer = getStateManager().getState(ModelViewerState.class);
        if (viewer == null)
            return;

        allModelEntries.addAll(viewer.scanCategory(category));
        if (searchField != null) {
            searchField.setText("");
        }
        applySearchFilter("");
    }

    private void performGlobalSearch(String query) {
        allModelEntries.clear();
        modelEntryList.clear();
        currentModelEntries.clear();

        if (query == null || query.trim().isEmpty())
            return;

        ModelViewerState viewer = getStateManager().getState(ModelViewerState.class);
        if (viewer == null)
            return;

        String lowerQuery = query.trim().toLowerCase();
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            List<ModelEntry> entries = viewer.scanCategory(categories[i]);
            for (ModelEntry e : entries) {
                if (e.displayName.toLowerCase().contains(lowerQuery)) {
                    allModelEntries.add(e);
                    currentModelEntries.add(e);
                    modelEntryList.add(e.displayName);
                }
            }
        }
    }

    private void applySearchFilter(String filter) {
        modelEntryList.clear();
        currentModelEntries.clear();

        String lowerFilter = filter.toLowerCase();
        for (ModelEntry e : allModelEntries) {
            if (lowerFilter.isEmpty() || e.displayName.toLowerCase().contains(lowerFilter)) {
                currentModelEntries.add(e);
                modelEntryList.add(e.displayName);
            }
        }
    }

    /**
     * Toggle alpha channel highlight.
     * When enabled, geometries with MapOpacity or Transparency are tinted
     * with a distinct color so the user can easily identify them.
     * MapOpacity (cutout) = magenta, Transparency (semi-transparent) = cyan.
     * When disabled, original materials are restored.
     */
    private void toggleAlphaHighlight(boolean enabled) {
        LoaderAppState loader = getStateManager().getState(LoaderAppState.class);
        if (loader == null || loader.rootNode == null)
            return;

        if (enabled) {
            alphaHighlightActive = true;
            originalMaterials.clear();
            final SimpleApplication app = (SimpleApplication) getApplication();

            loader.rootNode.depthFirstTraversal(new SceneGraphVisitor() {
                @Override
                public void visit(Spatial spatial) {
                    if (!(spatial instanceof Geometry))
                        return;
                    Geometry geom = (Geometry) spatial;
                    Integer mapOpacity = geom.getUserData("MapOpacity");
                    Float transparency = geom.getUserData("Transparency");

                    boolean hasCutout = (mapOpacity != null && mapOpacity != 0);
                    boolean hasTransparency = (transparency != null && transparency != 0);

                    if (hasCutout || hasTransparency) {
                        // Save original material
                        originalMaterials.put(geom, geom.getMaterial());

                        // Create highlight material
                        Material hlMat = new Material(app.getAssetManager(),
                                "Common/MatDefs/Misc/Unshaded.j3md");
                        if (hasCutout) {
                            hlMat.setColor("Color", new ColorRGBA(1f, 0f, 1f, 0.6f)); // magenta
                        } else {
                            hlMat.setColor("Color", new ColorRGBA(0f, 1f, 1f, 0.6f)); // cyan
                        }
                        RenderState rs = hlMat.getAdditionalRenderState();
                        rs.setWireframe(true);
                        rs.setFaceCullMode(RenderState.FaceCullMode.Off);

                        geom.setMaterial(hlMat);
                    }
                }
            });
        } else {
            // Restore original materials
            for (Map.Entry<Geometry, Material> entry : originalMaterials.entrySet()) {
                entry.getKey().setMaterial(entry.getValue());
            }
            originalMaterials.clear();
            alphaHighlightActive = false;
        }
    }
}
