package org.pstale.hud;

/**
 * Represents a single HUD element (texture quad) with position, size,
 * visibility and layer ordering.
 * This is the data model — rendering is handled by {@link HudEditorState}.
 */
public class HudElement {

    /** Display name shown in the element list. */
    private String name;

    /** Asset path relative to client root (e.g. "Textures/InGameHUD/Bar_Life.png"). */
    private String texturePath;

    /** Position in screen pixels (bottom-left origin). */
    private float x;
    private float y;

    /** Display size in pixels. */
    private float width;
    private float height;

    /** Original (intrinsic) texture size — used as default dimensions. */
    private float originalWidth;
    private float originalHeight;

    /** Whether this element is visible in the HUD layout. */
    private boolean visible = true;

    /** Layer order — higher values render on top. */
    private int layer = 0;

    /** Scale factor (1.0 = original size). */
    private float scale = 1.0f;

    // --- Atlas / Imageset source info ---
    /** Imageset name this element belongs to (e.g. "HUD", "Ingame"). Null for custom textures. */
    private String atlasName;
    /** Region name within the imageset (e.g. "Unitframe", "LifebarFill"). Null for custom textures. */
    private String regionName;
    /** Region coordinates in the atlas image (for UV calculation). */
    private int regionX, regionY, regionW, regionH;

    public HudElement(String name, String texturePath, float x, float y, float w, float h) {
        this.name = name;
        this.texturePath = texturePath;
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
        this.originalWidth = w;
        this.originalHeight = h;
    }

    // --- Getters / Setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTexturePath() { return texturePath; }
    public void setTexturePath(String texturePath) { this.texturePath = texturePath; }

    public float getX() { return x; }
    public void setX(float x) { this.x = x; }

    public float getY() { return y; }
    public void setY(float y) { this.y = y; }

    public float getWidth() { return width; }
    public void setWidth(float width) { this.width = width; }

    public float getHeight() { return height; }
    public void setHeight(float height) { this.height = height; }

    public float getOriginalWidth() { return originalWidth; }
    public float getOriginalHeight() { return originalHeight; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public int getLayer() { return layer; }
    public void setLayer(int layer) { this.layer = layer; }

    public float getScale() { return scale; }
    public void setScale(float scale) {
        this.scale = scale;
        this.width = originalWidth * scale;
        this.height = originalHeight * scale;
    }

    public String getAtlasName() { return atlasName; }
    public void setAtlasName(String atlasName) { this.atlasName = atlasName; }

    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }

    public int getRegionX() { return regionX; }
    public int getRegionY() { return regionY; }
    public int getRegionW() { return regionW; }
    public int getRegionH() { return regionH; }
    public void setRegion(int rx, int ry, int rw, int rh) {
        this.regionX = rx;
        this.regionY = ry;
        this.regionW = rw;
        this.regionH = rh;
    }

    /** Returns true if this element comes from an imageset atlas rather than a standalone file. */
    public boolean isAtlasBased() { return atlasName != null; }

    @Override
    public String toString() {
        return name + " (" + (int) x + ", " + (int) y + ")";
    }
}
