package org.pstale.hud;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.ListBox;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.Command;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.lemur.component.SpringGridLayout;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Insets3f;

import java.util.Set;

/**
 * Server UI Asset Viewer & Editor.
 *
 * Workflow:
 * 1. View all imageset assets from the server UI directory
 * 2. Select an asset to preview it
 * 3. Export asset as individual PNG
 * 4. User edits the image externally
 * 5. Import edited image — replaces the region in the in-memory atlas
 * 6. Export final atlas PNG + .imageset (with all modifications)
 */
public class HudEditorState extends BaseAppState implements ActionListener {

    private static final String ACT_PICK = "HudEditor_Pick";

    // Layout constants
    private static final float LEFT_PANEL_W = 260;
    private static final float RIGHT_PANEL_W = 280;
    private static final float TOOLBAR_H = 50;
    private static final float PANEL_MARGIN = 8;

    private SimpleApplication app;
    private Node guiNode;
    private Node canvasNode;
    private InputManager inputManager;
    private Camera cam;
    private int screenW, screenH;

    // Server UI directory
    private String uiDir;

    // ---- Data ----
    // Parsed imagesets
    private final List<ImagesetParser.Imageset> imagesets = new ArrayList<ImagesetParser.Imageset>();
    // In-memory atlas images (key = imageFile e.g. "Hud.png")
    private final Map<String, BufferedImage> atlasImages = new HashMap<String, BufferedImage>();
    // Flat list of all regions across all imagesets
    private final List<RegionEntry> allRegions = new ArrayList<RegionEntry>();
    // Currently selected region index
    private int selectedIndex = -1;

    // Modified flag per atlas (key = imageFile)
    private final Map<String, Boolean> modifiedAtlases = new HashMap<String, Boolean>();

    // ---- UI ----
    private Container toolbarPanel;
    private Container listPanel;
    private Container detailPanel;
    private Container statusBar;
    private ListBox<String> regionListBox;
    private VersionedReference<Set<Integer>> listSelectionRef;

    // Detail panel labels
    private Label labelAssetName;
    private Label labelAtlasName;
    private Label labelRegionInfo;
    private Label labelDimensions;
    private Label labelStatus;
    private Label statusLabel;

    // Preview in canvas
    private Geometry previewGeom;
    private Geometry previewBorder;
    private Geometry canvasBg;

    // Filter
    private String currentFilter = "ALL";

    // Export directory
    private File lastExportDir;

    // ---- Helper data class ----
    /** Links a region to its parent imageset. */
    private static class RegionEntry {
        final ImagesetParser.Imageset imageset;
        final ImagesetParser.ImageRegion region;
        boolean modified;

        RegionEntry(ImagesetParser.Imageset imageset, ImagesetParser.ImageRegion region) {
            this.imageset = imageset;
            this.region = region;
            this.modified = false;
        }

        String displayName() {
            String mod = modified ? " *" : "";
            return imageset.name + " / " + region.name + mod;
        }
    }

    // ================================================================
    // Lifecycle
    // ================================================================

    @Override
    protected void initialize(Application application) {
        this.app = (SimpleApplication) application;
        this.guiNode = app.getGuiNode();
        this.inputManager = app.getInputManager();
        this.cam = app.getCamera();
        screenW = cam.getWidth();
        screenH = cam.getHeight();
        uiDir = app.getContext().getSettings().getString("UiDir");

        canvasNode = new Node("PreviewCanvas");

        buildToolbar();
        buildListPanel();
        buildDetailPanel();
        buildStatusBar();
        buildCanvasBg();

        loadAllImagesets();

        inputManager.addMapping(ACT_PICK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener(this, ACT_PICK);
    }

    @Override
    protected void cleanup(Application application) {
        inputManager.deleteMapping(ACT_PICK);
        inputManager.removeListener(this);
        // Free atlas memory
        for (BufferedImage bi : atlasImages.values()) {
            bi.flush();
        }
        atlasImages.clear();
    }

    @Override
    protected void onEnable() {
        guiNode.attachChild(canvasNode);
        guiNode.attachChild(toolbarPanel);
        guiNode.attachChild(listPanel);
        guiNode.attachChild(detailPanel);
        guiNode.attachChild(statusBar);
        guiNode.attachChild(canvasBg);
    }

    @Override
    protected void onDisable() {
        canvasNode.removeFromParent();
        toolbarPanel.removeFromParent();
        listPanel.removeFromParent();
        detailPanel.removeFromParent();
        statusBar.removeFromParent();
        canvasBg.removeFromParent();
    }

    // ================================================================
    // UI Construction
    // ================================================================

    private void buildToolbar() {
        toolbarPanel = new Container();
        toolbarPanel.setInsets(new Insets3f(6, 12, 6, 12));
        toolbarPanel.setLocalTranslation(0, screenH, 20);
        toolbarPanel.setPreferredSize(new Vector3f(screenW, TOOLBAR_H, 0));

        Container row = new Container(new SpringGridLayout(Axis.X, Axis.Y, FillMode.Even, FillMode.Even));
        row.setInsets(new Insets3f(2, 4, 2, 4));

        Label title = new Label("Server UI Asset Editor");
        title.setColor(new ColorRGBA(0.95f, 0.85f, 0.4f, 1f));
        title.setFontSize(20f);
        title.setInsets(new Insets3f(4, 12, 4, 20));
        row.addChild(title);

        // Filter buttons
        addFilterButton(row, "ALL");
        addFilterButton(row, "HUD");
        addFilterButton(row, "Ingame");
        addFilterButton(row, "Main");
        addFilterButton(row, "Events");

        toolbarPanel.addChild(row);
    }

    private void addFilterButton(Container row, final String filterName) {
        Button btn = new Button(filterName);
        btn.setInsets(new Insets3f(4, 8, 4, 8));
        btn.addClickCommands(new Command<Button>() {
            public void execute(Button src) {
                currentFilter = filterName;
                rebuildListForFilter();
                clearPreview();
                updateStatusText();
            }
        });
        row.addChild(btn);
    }

    private void buildListPanel() {
        listPanel = new Container();
        listPanel.setInsets(new Insets3f(8, 8, 8, 8));

        float listH = screenH - TOOLBAR_H - 36 - PANEL_MARGIN * 2;
        listPanel.setLocalTranslation(PANEL_MARGIN, screenH - TOOLBAR_H - PANEL_MARGIN, 20);
        listPanel.setPreferredSize(new Vector3f(LEFT_PANEL_W, listH, 0));

        Label title = new Label("Assets");
        title.setColor(new ColorRGBA(1f, 0.9f, 0.3f, 1f));
        title.setFontSize(17f);
        title.setInsets(new Insets3f(0, 4, 6, 4));
        listPanel.addChild(title);

        int visItems = Math.max(10, (int) ((listH - 80) / 20));
        regionListBox = new ListBox<String>();
        regionListBox.setVisibleItems(visItems);
        regionListBox.setPreferredSize(new Vector3f(LEFT_PANEL_W - 20, listH - 60, 0));
        listPanel.addChild(regionListBox);
        listSelectionRef = regionListBox.getSelectionModel().createReference();
    }

    private void buildDetailPanel() {
        detailPanel = new Container();
        detailPanel.setInsets(new Insets3f(10, 10, 10, 10));

        float detailH = screenH - TOOLBAR_H - 36 - PANEL_MARGIN * 2;
        detailPanel.setLocalTranslation(screenW - RIGHT_PANEL_W - PANEL_MARGIN,
                screenH - TOOLBAR_H - PANEL_MARGIN, 20);
        detailPanel.setPreferredSize(new Vector3f(RIGHT_PANEL_W, detailH, 0));

        Label title = new Label("Asset Details");
        title.setColor(new ColorRGBA(1f, 0.9f, 0.3f, 1f));
        title.setFontSize(17f);
        title.setInsets(new Insets3f(0, 4, 10, 4));
        detailPanel.addChild(title);

        // Info labels
        labelAssetName = new Label("(select an asset)");
        labelAssetName.setColor(ColorRGBA.White);
        labelAssetName.setFontSize(15f);
        labelAssetName.setInsets(new Insets3f(2, 6, 4, 6));
        detailPanel.addChild(labelAssetName);

        labelAtlasName = new Label("");
        labelAtlasName.setColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));
        labelAtlasName.setFontSize(13f);
        labelAtlasName.setInsets(new Insets3f(2, 6, 2, 6));
        detailPanel.addChild(labelAtlasName);

        labelRegionInfo = new Label("");
        labelRegionInfo.setColor(new ColorRGBA(0.7f, 0.7f, 0.7f, 1f));
        labelRegionInfo.setFontSize(13f);
        labelRegionInfo.setInsets(new Insets3f(2, 6, 2, 6));
        detailPanel.addChild(labelRegionInfo);

        labelDimensions = new Label("");
        labelDimensions.setColor(new ColorRGBA(0.7f, 0.7f, 0.7f, 1f));
        labelDimensions.setFontSize(13f);
        labelDimensions.setInsets(new Insets3f(2, 6, 8, 6));
        detailPanel.addChild(labelDimensions);

        labelStatus = new Label("");
        labelStatus.setColor(new ColorRGBA(0.5f, 1f, 0.5f, 1f));
        labelStatus.setFontSize(13f);
        labelStatus.setInsets(new Insets3f(2, 6, 12, 6));
        detailPanel.addChild(labelStatus);

        // ---- Workflow Buttons ----

        // Step 1: Export selected asset as PNG
        addSpacer(detailPanel, 8);
        Label stepLabel1 = new Label("--- Step 1: Export ---");
        stepLabel1.setColor(new ColorRGBA(0.9f, 0.75f, 0.3f, 1f));
        stepLabel1.setFontSize(14f);
        stepLabel1.setInsets(new Insets3f(2, 6, 4, 6));
        detailPanel.addChild(stepLabel1);

        Button btnExport = new Button("Export Asset as PNG");
        btnExport.setInsets(new Insets3f(6, 12, 6, 12));
        btnExport.addClickCommands(new Command<Button>() {
            public void execute(Button src) { exportSelectedAsset(); }
        });
        detailPanel.addChild(btnExport);

        // Step 2: Import edited PNG
        addSpacer(detailPanel, 8);
        Label stepLabel2 = new Label("--- Step 2: Import ---");
        stepLabel2.setColor(new ColorRGBA(0.9f, 0.75f, 0.3f, 1f));
        stepLabel2.setFontSize(14f);
        stepLabel2.setInsets(new Insets3f(2, 6, 4, 6));
        detailPanel.addChild(stepLabel2);

        Button btnImport = new Button("Import Edited PNG");
        btnImport.setInsets(new Insets3f(6, 12, 6, 12));
        btnImport.addClickCommands(new Command<Button>() {
            public void execute(Button src) { importEditedAsset(); }
        });
        detailPanel.addChild(btnImport);

        // Step 3: Export final atlas
        addSpacer(detailPanel, 8);
        Label stepLabel3 = new Label("--- Step 3: Export Final ---");
        stepLabel3.setColor(new ColorRGBA(0.9f, 0.75f, 0.3f, 1f));
        stepLabel3.setFontSize(14f);
        stepLabel3.setInsets(new Insets3f(2, 6, 4, 6));
        detailPanel.addChild(stepLabel3);

        Button btnExportAtlas = new Button("Export Modified Atlas");
        btnExportAtlas.setInsets(new Insets3f(6, 12, 6, 12));
        btnExportAtlas.addClickCommands(new Command<Button>() {
            public void execute(Button src) { exportModifiedAtlas(); }
        });
        detailPanel.addChild(btnExportAtlas);

        addSpacer(detailPanel, 12);

        Button btnReload = new Button("Reload Original");
        btnReload.setInsets(new Insets3f(4, 12, 4, 12));
        btnReload.setColor(new ColorRGBA(1f, 0.6f, 0.4f, 1f));
        btnReload.addClickCommands(new Command<Button>() {
            public void execute(Button src) { reloadFromDisk(); }
        });
        detailPanel.addChild(btnReload);
    }

    private void addSpacer(Container c, int height) {
        Label sp = new Label("");
        sp.setPreferredSize(new Vector3f(1, height, 0));
        c.addChild(sp);
    }

    private void buildStatusBar() {
        statusBar = new Container();
        statusBar.setLocalTranslation(0, 28, 20);
        statusBar.setPreferredSize(new Vector3f(screenW, 28, 0));
        statusBar.setInsets(new Insets3f(4, 14, 4, 14));

        statusLabel = new Label("Select an asset from the list to preview and edit.");
        statusLabel.setColor(new ColorRGBA(0.55f, 0.7f, 0.55f, 1f));
        statusLabel.setFontSize(13f);
        statusBar.addChild(statusLabel);
    }

    private void buildCanvasBg() {
        float cx = LEFT_PANEL_W + PANEL_MARGIN * 2;
        float cy = 30;
        float cw = screenW - LEFT_PANEL_W - RIGHT_PANEL_W - PANEL_MARGIN * 4;
        float ch = screenH - TOOLBAR_H - 32 - PANEL_MARGIN;

        Quad quad = new Quad(cw, ch);
        canvasBg = new Geometry("CanvasBg", quad);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.12f, 0.12f, 0.16f, 0.6f));
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        canvasBg.setMaterial(mat);
        canvasBg.setLocalTranslation(cx, cy, -1);
    }

    private void updateStatusText() {
        int total = allRegions.size();
        int modCount = 0;
        for (RegionEntry re : allRegions) {
            if (re.modified) modCount++;
        }
        String filterTxt = "ALL".equals(currentFilter) ? "all" : currentFilter;
        String sel = selectedIndex >= 0 ? "  |  Selected: " + allRegions.get(selectedIndex).region.name : "";
        String mod = modCount > 0 ? "  |  " + modCount + " modified" : "";
        statusLabel.setText("Filter: " + filterTxt + "  |  " + total + " assets" + sel + mod);
    }

    // ================================================================
    // Data Loading
    // ================================================================

    private void loadAllImagesets() {
        imagesets.clear();
        allRegions.clear();
        atlasImages.clear();
        modifiedAtlases.clear();
        selectedIndex = -1;

        if (uiDir == null || uiDir.isEmpty()) {
            System.out.println("[HudEditor] No UI directory configured.");
            rebuildListForFilter();
            return;
        }

        File uiDirFile = new File(uiDir);
        if (!uiDirFile.isDirectory()) {
            System.err.println("[HudEditor] UI directory not found: " + uiDir);
            rebuildListForFilter();
            return;
        }

        List<ImagesetParser.Imageset> parsed = ImagesetParser.parseAll(uiDirFile);
        imagesets.addAll(parsed);

        // Load atlas PNGs into memory
        for (ImagesetParser.Imageset is : imagesets) {
            if (!atlasImages.containsKey(is.imageFile)) {
                File imgFile = new File(uiDirFile, "imagesets/" + is.imageFile);
                if (imgFile.exists()) {
                    try {
                        // Read as ARGB for maximum compatibility
                        BufferedImage raw = ImageIO.read(imgFile);
                        BufferedImage argb = new BufferedImage(raw.getWidth(), raw.getHeight(),
                                BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = argb.createGraphics();
                        g.drawImage(raw, 0, 0, null);
                        g.dispose();
                        raw.flush();
                        atlasImages.put(is.imageFile, argb);
                        modifiedAtlases.put(is.imageFile, false);
                        System.out.println("[HudEditor] Loaded atlas: " + is.imageFile
                                + " (" + argb.getWidth() + "x" + argb.getHeight() + ")");
                    } catch (IOException e) {
                        System.err.println("[HudEditor] Failed to load: " + imgFile);
                    }
                }
            }
        }

        // Build flat region list
        for (ImagesetParser.Imageset is : imagesets) {
            for (ImagesetParser.ImageRegion region : is.regions) {
                allRegions.add(new RegionEntry(is, region));
            }
        }

        System.out.println("[HudEditor] Total assets loaded: " + allRegions.size()
                + " from " + imagesets.size() + " imagesets.");

        rebuildListForFilter();
        updateStatusText();
    }

    // ================================================================
    // Filtering
    // ================================================================

    // visibleIndices maps listBox row -> allRegions index
    private final List<Integer> visibleIndices = new ArrayList<Integer>();

    private void rebuildListForFilter() {
        regionListBox.getModel().clear();
        visibleIndices.clear();

        for (int i = 0; i < allRegions.size(); i++) {
            RegionEntry re = allRegions.get(i);
            if ("ALL".equals(currentFilter) || re.imageset.name.equals(currentFilter)) {
                regionListBox.getModel().add(re.displayName());
                visibleIndices.add(i);
            }
        }
    }

    // ================================================================
    // Selection & Preview
    // ================================================================

    private void selectRegion(int listIndex) {
        if (listIndex < 0 || listIndex >= visibleIndices.size()) return;
        int realIndex = visibleIndices.get(listIndex);
        selectedIndex = realIndex;

        RegionEntry re = allRegions.get(realIndex);
        ImagesetParser.ImageRegion region = re.region;
        ImagesetParser.Imageset is = re.imageset;

        // Update detail labels
        labelAssetName.setText(region.name);
        labelAtlasName.setText("Atlas: " + is.name + " (" + is.imageFile + ")");
        labelRegionInfo.setText("Position: " + region.x + ", " + region.y);
        labelDimensions.setText("Size: " + region.width + " x " + region.height + " px");
        labelStatus.setText(re.modified ? "Status: MODIFIED" : "Status: Original");
        if (re.modified) {
            labelStatus.setColor(new ColorRGBA(1f, 0.8f, 0.2f, 1f));
        } else {
            labelStatus.setColor(new ColorRGBA(0.5f, 1f, 0.5f, 1f));
        }

        // Show preview in canvas
        showPreview(re);
        updateStatusText();
    }

    private void showPreview(RegionEntry re) {
        canvasNode.detachAllChildren();

        BufferedImage atlas = atlasImages.get(re.imageset.imageFile);
        if (atlas == null) return;

        ImagesetParser.ImageRegion r = re.region;
        if (r.x + r.width > atlas.getWidth() || r.y + r.height > atlas.getHeight()) return;

        Texture2D tex = extractSubTexture(atlas, r.x, r.y, r.width, r.height);

        // Calculate preview size — fit within the canvas area, preserving aspect ratio
        float canvasX = LEFT_PANEL_W + PANEL_MARGIN * 2;
        float canvasW = screenW - LEFT_PANEL_W - RIGHT_PANEL_W - PANEL_MARGIN * 4;
        float canvasY = 30;
        float canvasH = screenH - TOOLBAR_H - 34 - PANEL_MARGIN;

        float imgW = r.width;
        float imgH = r.height;

        // Scale up small images for better visibility, scale down large ones to fit
        float maxW = canvasW - 40;
        float maxH = canvasH - 40;
        float scale = 1f;

        if (imgW > maxW || imgH > maxH) {
            scale = Math.min(maxW / imgW, maxH / imgH);
        } else if (imgW < 100 && imgH < 100) {
            // Scale up small assets to at least 100px on the smaller side
            float minTarget = 200f;
            float upscale = Math.min(minTarget / imgW, minTarget / imgH);
            upscale = Math.min(upscale, 8f); // Max 8x zoom
            scale = upscale;
        }

        float dispW = imgW * scale;
        float dispH = imgH * scale;
        float px = canvasX + (canvasW - dispW) / 2f;
        float py = canvasY + (canvasH - dispH) / 2f;

        // Checkerboard background for transparency
        Geometry checker = createCheckerboard(dispW, dispH);
        checker.setLocalTranslation(px, py, 0);
        canvasNode.attachChild(checker);

        // The preview image
        Quad quad = new Quad(dispW, dispH);
        previewGeom = new Geometry("Preview", quad);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", tex);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        previewGeom.setMaterial(mat);
        previewGeom.setLocalTranslation(px, py, 1);
        canvasNode.attachChild(previewGeom);

        // Border around the preview
        Quad borderQuad = new Quad(dispW + 4, dispH + 4);
        previewBorder = new Geometry("PreviewBorder", borderQuad);
        Material borderMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        borderMat.setColor("Color", new ColorRGBA(1f, 0.9f, 0.3f, 1f));
        borderMat.getAdditionalRenderState().setWireframe(true);
        borderMat.getAdditionalRenderState().setLineWidth(2f);
        previewBorder.setMaterial(borderMat);
        previewBorder.setLocalTranslation(px - 2, py - 2, 2);
        canvasNode.attachChild(previewBorder);

        // Scale info label
        String scaleText = (int)(scale * 100) + "% (" + r.width + "x" + r.height + ")";
        // We'll put scale in status
        statusLabel.setText(statusLabel.getText().split("\\|")[0]
                + "|  Preview: " + scaleText);
    }

    /**
     * Create a simple checkerboard geometry to show behind transparent assets.
     */
    private Geometry createCheckerboard(float w, float h) {
        int checkSize = 16;
        int cw = Math.max(2, (int)(w / checkSize) + 1);
        int ch = Math.max(2, (int)(h / checkSize) + 1);
        int imgW = cw * checkSize;
        int imgH = ch * checkSize;

        BufferedImage checker = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        for (int cy = 0; cy < imgH; cy++) {
            for (int cx = 0; cx < imgW; cx++) {
                boolean light = ((cx / checkSize) + (cy / checkSize)) % 2 == 0;
                checker.setRGB(cx, cy, light ? 0xFF404040 : 0xFF303030);
            }
        }

        Texture2D tex = bufferedImageToTexture(checker);
        checker.flush();

        Quad quad = new Quad(w, h);
        Geometry geom = new Geometry("Checker", quad);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", tex);
        geom.setMaterial(mat);
        return geom;
    }

    private void clearPreview() {
        canvasNode.detachAllChildren();
        selectedIndex = -1;
        labelAssetName.setText("(select an asset)");
        labelAtlasName.setText("");
        labelRegionInfo.setText("");
        labelDimensions.setText("");
        labelStatus.setText("");
    }

    // ================================================================
    // Export Asset as Individual PNG
    // ================================================================

    private void exportSelectedAsset() {
        if (selectedIndex < 0 || selectedIndex >= allRegions.size()) {
            System.out.println("[HudEditor] No asset selected for export.");
            return;
        }

        final RegionEntry re = allRegions.get(selectedIndex);
        final BufferedImage atlas = atlasImages.get(re.imageset.imageFile);
        if (atlas == null) return;

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Export Asset: " + re.region.name);
                fc.setFileFilter(new FileNameExtensionFilter("PNG Image (*.png)", "png"));
                fc.setSelectedFile(new File(re.region.name + ".png"));
                if (lastExportDir != null) fc.setCurrentDirectory(lastExportDir);

                if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    if (!file.getName().toLowerCase().endsWith(".png")) {
                        file = new File(file.getAbsolutePath() + ".png");
                    }
                    lastExportDir = file.getParentFile();

                    try {
                        ImagesetParser.ImageRegion r = re.region;
                        BufferedImage sub = atlas.getSubimage(r.x, r.y, r.width, r.height);
                        // Copy to detach from parent atlas
                        BufferedImage copy = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = copy.createGraphics();
                        g.drawImage(sub, 0, 0, null);
                        g.dispose();

                        ImageIO.write(copy, "PNG", file);
                        copy.flush();

                        System.out.println("[HudEditor] Exported: " + file.getAbsolutePath());
                        final String msg = "Exported: " + file.getName();
                        app.enqueue(new Runnable() {
                            public void run() {
                                labelStatus.setText(msg);
                                labelStatus.setColor(new ColorRGBA(0.3f, 1f, 0.3f, 1f));
                            }
                        });
                    } catch (IOException e) {
                        System.err.println("[HudEditor] Export error: " + e.getMessage());
                    }
                }
            }
        });
    }

    // ================================================================
    // Import Edited PNG (replace region in atlas)
    // ================================================================

    private void importEditedAsset() {
        if (selectedIndex < 0 || selectedIndex >= allRegions.size()) {
            System.out.println("[HudEditor] No asset selected for import.");
            return;
        }

        final RegionEntry re = allRegions.get(selectedIndex);
        final BufferedImage atlas = atlasImages.get(re.imageset.imageFile);
        if (atlas == null) return;

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Import Edited Asset: " + re.region.name);
                fc.setFileFilter(new FileNameExtensionFilter("PNG Image (*.png)", "png"));
                if (lastExportDir != null) fc.setCurrentDirectory(lastExportDir);

                if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    lastExportDir = file.getParentFile();

                    try {
                        BufferedImage imported = ImageIO.read(file);
                        if (imported == null) {
                            System.err.println("[HudEditor] Failed to read image: " + file);
                            return;
                        }

                        final ImagesetParser.ImageRegion r = re.region;

                        // Check if dimensions match
                        if (imported.getWidth() != r.width || imported.getHeight() != r.height) {
                            int choice = JOptionPane.showConfirmDialog(null,
                                    "Imported image is " + imported.getWidth() + "x" + imported.getHeight()
                                            + " but region is " + r.width + "x" + r.height + ".\n"
                                            + "The image will be scaled to fit. Continue?",
                                    "Size Mismatch", JOptionPane.YES_NO_OPTION);
                            if (choice != JOptionPane.YES_OPTION) {
                                imported.flush();
                                return;
                            }
                        }

                        // Draw imported image onto the atlas at the region's position
                        Graphics2D g = atlas.createGraphics();
                        g.drawImage(imported, r.x, r.y, r.width, r.height, null);
                        g.dispose();
                        imported.flush();

                        // Mark as modified
                        re.modified = true;
                        modifiedAtlases.put(re.imageset.imageFile, true);

                        System.out.println("[HudEditor] Imported " + file.getName()
                                + " into " + re.imageset.name + "/" + r.name
                                + " at (" + r.x + "," + r.y + ")");

                        // Refresh preview and list on jME thread
                        final int idx = selectedIndex;
                        app.enqueue(new Runnable() {
                            public void run() {
                                showPreview(re);
                                rebuildListForFilter();
                                // Find and re-select the same item
                                for (int i = 0; i < visibleIndices.size(); i++) {
                                    if (visibleIndices.get(i) == idx) {
                                        regionListBox.getSelectionModel().setSelection(i);
                                        break;
                                    }
                                }
                                labelStatus.setText("Status: MODIFIED - imported");
                                labelStatus.setColor(new ColorRGBA(1f, 0.8f, 0.2f, 1f));
                                updateStatusText();
                            }
                        });

                    } catch (IOException e) {
                        System.err.println("[HudEditor] Import error: " + e.getMessage());
                    }
                }
            }
        });
    }

    // ================================================================
    // Export Modified Atlas (PNG + copy .imageset)
    // ================================================================

    private void exportModifiedAtlas() {
        // Check if any atlas was modified
        boolean anyModified = false;
        for (Boolean b : modifiedAtlases.values()) {
            if (b) { anyModified = true; break; }
        }

        if (!anyModified) {
            System.out.println("[HudEditor] No modifications to export.");
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    JOptionPane.showMessageDialog(null,
                            "No assets have been modified.\nImport an edited asset first.",
                            "Nothing to Export", JOptionPane.INFORMATION_MESSAGE);
                }
            });
            return;
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Choose Export Directory for Modified Atlases");
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (lastExportDir != null) fc.setCurrentDirectory(lastExportDir);

                if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    final File outDir = fc.getSelectedFile();
                    lastExportDir = outDir;

                    // Export on a background thread to not block Swing
                    new Thread(new Runnable() {
                        public void run() {
                            doExportAtlases(outDir);
                        }
                    }).start();
                }
            }
        });
    }

    private void doExportAtlases(File outDir) {
        int exported = 0;

        for (Map.Entry<String, Boolean> entry : modifiedAtlases.entrySet()) {
            if (!entry.getValue()) continue;

            String imageFile = entry.getKey();
            BufferedImage atlas = atlasImages.get(imageFile);
            if (atlas == null) continue;

            // Save the modified PNG
            File outPng = new File(outDir, imageFile);
            try {
                ImageIO.write(atlas, "PNG", outPng);
                System.out.println("[HudEditor] Exported atlas PNG: " + outPng.getAbsolutePath());
                exported++;
            } catch (IOException e) {
                System.err.println("[HudEditor] Failed to save: " + outPng + " - " + e.getMessage());
                continue;
            }

            // Copy the corresponding .imageset XML file too
            // Find which imageset uses this file
            for (ImagesetParser.Imageset is : imagesets) {
                if (is.imageFile.equals(imageFile)) {
                    // Find the source .imageset file
                    String isFileName = is.name + ".imageset";
                    // Try original case variations
                    File srcIs = new File(uiDir, "imagesets/" + isFileName);
                    if (!srcIs.exists()) {
                        // Try filename derived from imageFile
                        String baseName = imageFile.substring(0, imageFile.lastIndexOf('.'));
                        srcIs = new File(uiDir, "imagesets/" + baseName + ".imageset");
                    }
                    if (srcIs.exists()) {
                        File outIs = new File(outDir, srcIs.getName());
                        try {
                            Files.copy(srcIs.toPath(), outIs.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("[HudEditor] Copied imageset: " + outIs.getAbsolutePath());
                        } catch (IOException e) {
                            System.err.println("[HudEditor] Failed to copy imageset: " + e.getMessage());
                        }
                    }
                    break;
                }
            }
        }

        final int count = exported;
        final File dir = outDir;
        app.enqueue(new Runnable() {
            public void run() {
                labelStatus.setText("Exported " + count + " atlas(es) to " + dir.getName());
                labelStatus.setColor(new ColorRGBA(0.3f, 1f, 0.3f, 1f));
                updateStatusText();
            }
        });

        System.out.println("[HudEditor] Export complete: " + exported + " atlas(es) to " + outDir);
    }

    // ================================================================
    // Reload from Disk
    // ================================================================

    private void reloadFromDisk() {
        clearPreview();
        for (BufferedImage bi : atlasImages.values()) {
            bi.flush();
        }
        loadAllImagesets();
        updateStatusText();
        System.out.println("[HudEditor] Reloaded all imagesets from disk.");
    }

    // ================================================================
    // Texture Conversion
    // ================================================================

    /**
     * Extract a sub-region from a BufferedImage and convert to jME3 Texture2D.
     */
    private Texture2D extractSubTexture(BufferedImage atlas, int rx, int ry, int rw, int rh) {
        BufferedImage sub = atlas.getSubimage(rx, ry, rw, rh);
        return bufferedImageToTexture(sub);
    }

    /**
     * Convert a BufferedImage to a jME3 Texture2D, flipping vertically for OpenGL.
     */
    private Texture2D bufferedImageToTexture(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);

        // Flip vertically: OpenGL expects bottom row first
        for (int row = h - 1; row >= 0; row--) {
            for (int col = 0; col < w; col++) {
                int argb = img.getRGB(col, row);
                buf.put((byte) ((argb >> 16) & 0xFF)); // R
                buf.put((byte) ((argb >> 8) & 0xFF));  // G
                buf.put((byte) (argb & 0xFF));          // B
                buf.put((byte) ((argb >> 24) & 0xFF)); // A
            }
        }
        buf.flip();

        Image jmeImg = new Image(Image.Format.RGBA8, w, h, buf, null, ColorSpace.sRGB);
        Texture2D tex = new Texture2D(jmeImg);
        tex.setMagFilter(Texture.MagFilter.Bilinear);
        tex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        return tex;
    }

    // ================================================================
    // Input & Update
    // ================================================================

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        // Not needed for this viewer mode, but kept for potential future use
    }

    @Override
    public void update(float tpf) {
        // Watch for list selection changes
        if (listSelectionRef != null && listSelectionRef.update()) {
            Set<Integer> sel = listSelectionRef.get();
            if (sel != null && !sel.isEmpty()) {
                int idx = sel.iterator().next();
                selectRegion(idx);
            }
        }
    }
}
