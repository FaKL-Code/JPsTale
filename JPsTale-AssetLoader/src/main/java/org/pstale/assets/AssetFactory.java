package org.pstale.assets;

import static org.pstale.constants.SceneConstants.scale;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.pstale.assets.utils.AssetNameUtils;
import org.pstale.assets.utils.ModelBuilder;
import org.pstale.assets.utils.SceneBuilder;
import org.pstale.entity.field.RespawnList;
import org.pstale.entity.field.StartPoint;
import org.pstale.entity.item.ItemInfo;
import org.pstale.utils.FileLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.asset.plugins.ClasspathLocator;
import com.jme3.audio.plugins.WAVLoader;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.scene.plugins.ase.AseLoader;
import com.jme3.scene.plugins.smd.InxLoader;
import com.jme3.scene.plugins.smd.SMDTYPE;
import com.jme3.scene.plugins.smd.SmbLoader;
import com.jme3.scene.plugins.smd.SmdKey;
import com.jme3.scene.plugins.smd.SmdLoader;
import com.jme3.scene.plugins.smd.geom.AnimateModel;
import com.jme3.scene.plugins.smd.geom.PAT3D;
import com.jme3.scene.plugins.smd.material._Material;
import com.jme3.scene.plugins.smd.stage.Stage;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.script.plugins.character.CharInfoLoader;
import com.jme3.script.plugins.character.Monster;
import com.jme3.script.plugins.field.CharacterTransform;
import com.jme3.script.plugins.field.SpcLoader;
import com.jme3.script.plugins.field.SpmLoader;
import com.jme3.script.plugins.field.SppLoader;
import com.jme3.script.plugins.item.ItemLoader;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture.MinFilter;
import com.jme3.texture.Texture.WrapMode;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

/**
 * 模型工厂
 * 
 * @author yanmaoyuan
 *
 */
public class AssetFactory {

    static Logger logger = LoggerFactory.getLogger(AssetFactory.class);

    static AssetManager assetManager;
    static String folder = "/";

    /**
     * 设置资源目录
     * 
     * @param folder
     */
    public static void setFolder(String f) {
        folder = f;
    }

    public static void setAssetManager(final AssetManager manager) {
        assetManager = manager;
        assetManager.registerLoader(AseLoader.class, "ase");
        assetManager.registerLoader(InxLoader.class, "inx");
        assetManager.registerLoader(SmbLoader.class, "smb");
        assetManager.registerLoader(SmdLoader.class, "smd");
        assetManager.registerLoader(WAVLoader.class, "bgm");
        assetManager.registerLoader(SpcLoader.class, "spc");
        assetManager.registerLoader(SpmLoader.class, "spm");
        assetManager.registerLoader(SppLoader.class, "spp");
        assetManager.registerLoader(CharInfoLoader.class, "inf", "npc");
        assetManager.registerLoader(ItemLoader.class, "txt");

        // 注册资源加载路径
        assetManager.registerLocator("/", ClasspathLocator.class);
        assetManager.registerLocator("/", FileLocator.class);
        registerFolder("F:/1_DEVELOP/3_素材");
        registerFolder("I:/game/PTCN-RPT1.0");
        registerFolder("D:/Priston Tale/0_素材/Client");
        registerFolder("D:/Priston Tale/0_素材/Server/精灵中国全服务端3060/3060");
        registerFolder("D:/Priston Tale/PTCN3550/PTCN3550");
    }

    private static void registerFolder(String folder) {
        if (new File(folder).exists()) {
            assetManager.registerLocator(folder, FileLocator.class);
        }
    }

    /**
     * 检查客户端文件夹是否都存在
     * 
     * @param folder
     * @return
     */
    public static boolean checkClientRoot(String folder) {
        File file = new File(folder);
        if (!file.exists()) {
            return false;
        }

        String[] folders = { "effect", "field", "char", "wav", "sky", "image" };
        for (String subFolder : folders) {
            if (!new File(folder + "/" + subFolder).exists()) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查服务端文件夹是否都存在
     * 
     * @param folder
     * @return
     */
    public static boolean checkServerRoot(String folder) {
        File file = new File(folder);
        if (!file.exists()) {
            return false;
        }

        String[] folders = { "GameServer/Field", "GameServer/Monster", "GameServer/NPC", "GameServer/OpenItem" };
        for (String subFolder : folders) {
            if (!new File(folder + "/" + subFolder).exists()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获得一个刷怪点标记
     * 
     * @return
     */
    public static Spatial loadFlag() {
        Spatial flag;
        try {
            Node wow = AssetFactory.loadStageObj("char/flag/wow.smd", false);
            wow.depthFirstTraversal(new SceneGraphVisitor() {
                @Override
                public void visit(Spatial spatial) {
                    if (spatial instanceof Geometry) {
                        Geometry geom = (Geometry) spatial;
                        geom.getMaterial().getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
                    }
                }
            });

            flag = wow;
        } catch (Exception e) {
            logger.debug("无法加载旗帜", e);
            flag = new Geometry("flag", new Box(1 / scale, 1 / scale, 1 / scale));
            Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            mat.setColor("Color", ColorRGBA.Red);
            flag.setMaterial(mat);
        }

        return flag;
    }

    /**
     * 创建一个Loading标记
     * 
     * @return
     */
    public static Spatial getLoadingFlag() {
        Quad quad = new Quad(80, 20);
        Geometry geom = new Geometry("loading", quad);
        Material mat = new Material(assetManager, "Shader/Misc/Scroll.j3md");
        Texture tex = assetManager.loadTexture("Interface/loading.png");
        tex.setWrap(WrapMode.Repeat);
        mat.setTexture("ColorMap", tex);
        mat.setFloat("Speed", 2);
        mat.setColor("Color", ColorRGBA.Magenta);
        geom.setMaterial(mat);

        return geom;
    }

    /**
     * 加载模型的索引文件
     * 
     * @param name
     * @return
     */
    public static AnimateModel loadInx(final String name) {
        String inx = AssetNameUtils.changeExt(name, "inx");
        return (AnimateModel) assetManager.loadAsset(inx);
    }

    /**
     * 读取地形数据
     * 
     * @param name
     * @return
     */
    public static Stage loadSmdStage(final String name) {
        String smd = AssetNameUtils.changeExt(name, "smd");
        Stage stage = (Stage) assetManager.loadAsset(new SmdKey(smd, SMDTYPE.STAGE3D));
        return stage;
    }

    public static PAT3D loadSmb(final String name) {
        String smb = AssetNameUtils.changeExt(name, "smb");
        PAT3D skeleton = (PAT3D) assetManager.loadAsset(new SmdKey(smb, SMDTYPE.PAT3D));
        return skeleton;
    }

    public static PAT3D loadSmd(final String name) {
        String smb = AssetNameUtils.changeExt(name, "smd");
        PAT3D model = (PAT3D) assetManager.loadAsset(new SmdKey(smb, SMDTYPE.PAT3D));
        return model;
    }

    /**
     * 加载地图
     * 
     * @param name
     * @return
     * @throws Exception
     */
    public static Node loadStage3D(final String name) {
        return loadStage3D(name, null);
    }

    /**
     * Loads the map with an optional texture folder override.
     * When {@code folderOverride} is non-null it is used instead of the
     * folder derived from the SMD path, allowing texture presets to be
     * swapped without changing the map file itself.
     */
    public static Node loadStage3D(final String name, final String folderOverride) {
        String smd = AssetNameUtils.changeExt(name, "smd");
        SmdKey key = new SmdKey(smd, SMDTYPE.STAGE3D);
        Stage stage = (Stage) assetManager.loadAsset(key);
        // Apply folder override AFTER loadAsset: SmdLoader resets folder to
        // key.getFolder() internally.
        setFolder(folderOverride != null ? folderOverride : key.getFolder());
        return SceneBuilder.buildScene3D(stage, smd);
    }

    public static Mesh loadStage3DMesh(final String name) {
        return loadStage3DMesh(name, null);
    }

    public static Mesh loadStage3DMesh(final String name, final String folderOverride) {
        String smd = AssetNameUtils.changeExt(name, "smd");
        SmdKey key = new SmdKey(smd, SMDTYPE.STAGE3D);
        Stage stage = (Stage) assetManager.loadAsset(key);
        // Apply folder override AFTER loadAsset: SmdLoader resets folder to
        // key.getFolder() internally.
        setFolder(folderOverride != null ? folderOverride : key.getFolder());
        return SceneBuilder.buildCollisionMesh(stage);
    }

    public static Node loadStageObj(final String name, final boolean bip) {
        PAT3D model = loadSmd(name);
        if (bip) {
            // load smb first
            PAT3D skeleton = loadSmb(name);
            model.setSkeleton(skeleton);
        }

        AssetFactory.setFolder(AssetNameUtils.getFolder(name));
        return ModelBuilder.buildModel(model, AssetNameUtils.getSimpleName(name));
    }

    public static Node loadCharacter(String path) {
        AnimateModel modelInfo = loadInx(path);

        String folder = AssetNameUtils.getFolder(path);

        // 有共享数据?
        String linkFile = modelInfo.motionLinkFile;
        if (linkFile.length() > 0) {
            AnimateModel mi = AssetFactory.loadInx(linkFile);
            modelInfo.motionFile = mi.motionFile;
        }

        PAT3D skeleton = null;
        // 读取动画
        if (modelInfo.motionFile.length() > 0) {
            // 后缀名改为smb
            String smbFile = AssetNameUtils.changeExt(modelInfo.motionFile, "smb");
            String name = AssetNameUtils.getName(smbFile);
            skeleton = AssetFactory.loadSmb(folder + name);
        }

        // 读取网格
        String smdFile = AssetNameUtils.changeExt(modelInfo.modelFile, "smd");
        smdFile = AssetNameUtils.getName(smdFile);
        PAT3D model = AssetFactory.loadSmd(folder + smdFile);
        model.setSkeleton(skeleton);

        return ModelBuilder.buildModel(model, path);
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<StartPoint> loadSpp(final String name) {
        String path = String.format("GameServer/Field/%s.ase.spp", AssetNameUtils.getSimpleName(name));
        try {
            ArrayList<StartPoint> spp = (ArrayList<StartPoint>) assetManager.loadAsset(path);
            return spp;
        } catch (Exception e) {
            return null;
        }
    }

    public static RespawnList loadSpm(final String name) {
        String path = String.format("GameServer/Field/%s.ase.spm", AssetNameUtils.getSimpleName(name));
        try {
            RespawnList creatures = (RespawnList) assetManager.loadAsset(path);
            return creatures;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<CharacterTransform> loadSpc(final String name) {
        String path = String.format("GameServer/Field/%s.ase.spc", AssetNameUtils.getSimpleName(name));
        try {
            ArrayList<CharacterTransform> npcs = (ArrayList<CharacterTransform>) assetManager.loadAsset(path);
            return npcs;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 导入NPC的脚本文件
     * 
     * @param name
     * @return
     */
    public static Monster loadNpcScript(final String name) {
        String path = String.format("GameServer/NPC/%s.npc", AssetNameUtils.getSimpleName(name));

        try {
            Monster info = (Monster) assetManager.loadAsset(path);
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 导入怪物的脚本文件
     * 
     * @param name
     * @return
     */
    public static Monster loadMonsterScript(final String name) {
        String path = String.format("GameServer/Monster/%s.inf", AssetNameUtils.getSimpleName(name));

        try {
            Monster info = (Monster) assetManager.loadAsset(path);
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 导入装备的脚本
     * 
     * @param name
     * @return
     */
    public static ItemInfo loadItemScript(String name) {
        String path = String.format("GameServer/OpenItem/%s.txt", AssetNameUtils.getSimpleName(name));

        try {
            ItemInfo info = (ItemInfo) assetManager.loadAsset(path);
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 创建纹理
     * 
     * @param name
     */
    /** Maximum texture dimension. Textures larger than this are downscaled. */
    private static final int MAX_TEXTURE_SIZE = 2048;

    public static Texture createTexture(String name) {
        name = AssetNameUtils.getName(name);
        Texture texture = null;
        try {
            TextureKey texKey = new TextureKey(folder + name);
            texKey.setGenerateMips(true);
            texture = assetManager.loadTexture(texKey);
            texture = clampTextureSize(texture);
            texture.setWrap(WrapMode.Repeat);
            texture.setAnisotropicFilter(4);
        } catch (Exception ex) {
            texture = assetManager.loadTexture("Common/Textures/MissingTexture.png");
            texture.setWrap(WrapMode.EdgeClamp);
        }
        return texture;
    }

    /**
     * If a texture exceeds MAX_TEXTURE_SIZE in any dimension, downscale it
     * using java.awt so it fits within the limit. This prevents
     * OutOfMemoryError caused by oversized custom PNG textures.
     *
     * Uses TYPE_INT_ARGB as the intermediate BufferedImage type to avoid
     * any byte-order or pre-multiplied-alpha artifacts that occur with
     * TYPE_4BYTE_ABGR / TYPE_3BYTE_BGR during Graphics2D compositing.
     * The output image uses the same pixel format as the source.
     */
    private static Texture clampTextureSize(Texture texture) {
        com.jme3.texture.Image img = texture.getImage();
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= MAX_TEXTURE_SIZE && h <= MAX_TEXTURE_SIZE) {
            return texture;
        }

        logger.info("Downscaling oversized texture {}x{} -> max {}", w, h, MAX_TEXTURE_SIZE);

        float scale = Math.min((float) MAX_TEXTURE_SIZE / w, (float) MAX_TEXTURE_SIZE / h);
        int newW = Math.max(1, (int) (w * scale));
        int newH = Math.max(1, (int) (h * scale));

        try {
            ByteBuffer buf = img.getData(0);
            if (buf == null)
                return texture;
            buf.rewind();
            Format fmt = img.getFormat();

            int bpp = fmt.getBitsPerPixel() / 8;
            if (bpp < 3 || bpp > 4) {
                logger.warn("Unsupported format {} for downscale, skipping", fmt);
                return texture;
            }

            // --- Decode source pixels into a plain INT_ARGB image ---
            // Using TYPE_INT_ARGB avoids any sub-format byte-order or
            // pre-multiplied-alpha issues during the scaling step.
            BufferedImage srcImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int r, g, b, a;
                    if (fmt == Format.ABGR8) {
                        a = buf.get() & 0xFF;
                        b = buf.get() & 0xFF;
                        g = buf.get() & 0xFF;
                        r = buf.get() & 0xFF;
                    } else if (fmt == Format.BGRA8) {
                        b = buf.get() & 0xFF;
                        g = buf.get() & 0xFF;
                        r = buf.get() & 0xFF;
                        a = buf.get() & 0xFF;
                    } else if (fmt == Format.RGBA8) {
                        r = buf.get() & 0xFF;
                        g = buf.get() & 0xFF;
                        b = buf.get() & 0xFF;
                        a = buf.get() & 0xFF;
                    } else if (fmt == Format.BGR8) {
                        b = buf.get() & 0xFF;
                        g = buf.get() & 0xFF;
                        r = buf.get() & 0xFF;
                        a = 255;
                    } else {
                        // RGB8 or other 3-byte format
                        r = buf.get() & 0xFF;
                        g = buf.get() & 0xFF;
                        b = buf.get() & 0xFF;
                        a = 255;
                    }
                    srcImg.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }

            // --- Scale into another TYPE_INT_ARGB ---
            BufferedImage dstImg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = dstImg.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(srcImg, 0, 0, newW, newH, null);
            g2d.dispose();

            // --- Encode back to the original jME pixel format ---
            ByteBuffer newBuf = BufferUtils.createByteBuffer(newW * newH * bpp);
            for (int y = 0; y < newH; y++) {
                for (int x = 0; x < newW; x++) {
                    int argb = dstImg.getRGB(x, y); // always 0xAARRGGBB
                    int r2 = (argb >> 16) & 0xFF;
                    int g2 = (argb >> 8) & 0xFF;
                    int b2 = argb & 0xFF;
                    int a2 = (argb >> 24) & 0xFF;
                    if (fmt == Format.ABGR8) {
                        newBuf.put((byte) a2);
                        newBuf.put((byte) b2);
                        newBuf.put((byte) g2);
                        newBuf.put((byte) r2);
                    } else if (fmt == Format.BGRA8) {
                        newBuf.put((byte) b2);
                        newBuf.put((byte) g2);
                        newBuf.put((byte) r2);
                        newBuf.put((byte) a2);
                    } else if (fmt == Format.RGBA8) {
                        newBuf.put((byte) r2);
                        newBuf.put((byte) g2);
                        newBuf.put((byte) b2);
                        newBuf.put((byte) a2);
                    } else if (fmt == Format.BGR8) {
                        newBuf.put((byte) b2);
                        newBuf.put((byte) g2);
                        newBuf.put((byte) r2);
                    } else {
                        // RGB8 or other
                        newBuf.put((byte) r2);
                        newBuf.put((byte) g2);
                        newBuf.put((byte) b2);
                    }
                }
            }
            newBuf.flip();

            com.jme3.texture.Image newImg = new com.jme3.texture.Image(fmt, newW, newH, newBuf);
            return new Texture2D(newImg);
        } catch (Exception e) {
            logger.warn("Failed to downscale texture, using original", e);
            return texture;
        }
    }

    /**
     * 创建感光材质
     * 
     * @param m
     * @return
     */
    public static Material createLightMaterial(_Material m) {
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setColor("Diffuse", new ColorRGBA(m.Diffuse.r, m.Diffuse.g, m.Diffuse.b, 1));
        mat.setColor("Ambient", new ColorRGBA(1f, 1f, 1f, 1f));
        mat.setColor("Specular", new ColorRGBA(0, 0, 0, 1));
        // mat.setBoolean("UseMaterialColors", true);

        // 设置贴图
        if (m.TextureCounter > 0) {
            mat.setTexture("DiffuseMap", createTexture(m.smTexture[0].Name));
        }
        if (m.TextureCounter > 1) {
            mat.setBoolean("SeparateTexCoord", true);
            mat.setTexture("LightMap", createTexture(m.smTexture[1].Name));
        }

        return mat;
    }

    /**
     * 创建一个忽略光源的材质。
     * 
     * @param m
     * @return
     */
    public static Material createMiscMaterial(_Material m) {
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        // mat.setColor("Color", new ColorRGBA(m.Diffuse.r, m.Diffuse.g,
        // m.Diffuse.b, 1));
        mat.setColor("Color", ColorRGBA.White);

        // 设置贴图
        if (m.TextureCounter > 0) {
            mat.setTexture("ColorMap", createTexture(m.smTexture[0].Name));
        }
        if (m.TextureCounter > 1) {
            mat.setBoolean("SeparateTexCoord", true);
            mat.setTexture("LightMap", createTexture(m.smTexture[1].Name));
        }

        return mat;
    }

    /**
     * 创建一个匀速切换帧的材质。
     * 
     * @param m
     * @return
     */
    public static Material createShiftMaterial(_Material m) {
        Material mat = new Material(assetManager, "Shader/Misc/Shift.j3md");

        // 画面的切换时间间隔
        float ShiftSpeed = (1 << m.Shift_FrameSpeed) / 1000f;
        mat.setFloat("ShiftSpeed", ShiftSpeed);

        // 设置贴图
        Texture tex;
        for (int i = 0; i < m.AnimTexCounter; i++) {
            tex = createTexture(m.smAnimTexture[i].Name);
            mat.setTexture("Tex" + (i + 1), tex);
        }

        return mat;
    }

    /**
     * 创建一个卷轴动画材质。
     * 
     * @param m
     * @return
     */
    public static Material createScrollMaterial(_Material m) {
        Material mat = new Material(assetManager, "Shader/Misc/Scroll.j3md");

        // 画面的卷动速度
        float speed = 1f;

        int n = m.TextureFormState[0];
        if (n >= 6 && n <= 14) {
            speed = 15 - n;
        }

        if (n >= 15 && n <= 18) {
            int factor = 18 - n + 4;
            speed = (128 >> factor) / 256f;
        }

        mat.setFloat("Speed", speed);

        // 设置贴图
        Texture tex = createTexture(m.smTexture[0].Name);
        mat.setTexture("ColorMap", tex);

        if (m.TextureCounter > 1) {
            mat.setBoolean("SeparateTexCoord", true);
            mat.setTexture("LightMap", createTexture(m.smTexture[1].Name));
        }

        return mat;
    }

    /**
     * 创建一个原地转圈的动画材质。 Water动画专用
     * 
     * @param m
     * @return
     */
    public static Material createRoundMaterial(_Material m) {
        Material mat = new Material(assetManager, "Shader/Misc/Round.j3md");

        // 设置贴图
        Texture tex = createTexture(m.smTexture[0].Name);
        mat.setTexture("ColorMap", tex);

        if (m.TextureCounter > 1) {
            mat.setBoolean("SeparateTexCoord", true);
            mat.setTexture("LightMap", createTexture(m.smTexture[1].Name));
        }

        return mat;
    }

    /**
     * 设置材质的RenderState
     * 
     * @param m
     * @param mat
     */
    public static void setRenderState(_Material m, Material mat) {
        RenderState rs = mat.getAdditionalRenderState();

        switch (m.BlendType) {
            case 0:// SMMAT_BLEND_NONE
                rs.setBlendMode(BlendMode.Off);
                break;
            case 1:// SMMAT_BLEND_ALPHA
                rs.setBlendMode(BlendMode.Alpha);
                break;
            case 2:// SMMAT_BLEND_COLOR
                rs.setBlendMode(BlendMode.Color);
                break;
            case 3:// SMMAT_BLEND_SHADOW
                break;
            case 4:// SMMAT_BLEND_LAMP
                rs.setBlendMode(BlendMode.Additive);
                break;
            case 5:// SMMAT_BLEND_ADDCOLOR
                rs.setBlendMode(BlendMode.Additive);
                break;
            case 6:
                break;
            default:
                logger.info("Unknown BlendType=" + m.BlendType);
        }

        if (m.TwoSide == 1) {
            rs.setFaceCullMode(FaceCullMode.Off);
        }

        if (m.TextureType == 0x0001) {
            // 动画默认显示2面
            rs.setFaceCullMode(FaceCullMode.Off);
        }

        // --- Cutout materials (MapOpacity flag) ---
        // Fences, railings, foliage, etc. with per-pixel alpha in the texture.
        // Use alpha-test discard in the Opaque bucket so depth writes are
        // correct and no back-to-front sorting is needed.
        // Threshold 0.5 = industry standard: >50% opaque passes, rest discarded.
        // Also disable mipmap filtering on the diffuse texture so the alpha
        // channel stays crisp at any distance (mipmaps average alpha and cause
        // far-away transparent gaps to pass the threshold and block objects behind).
        if (m.MapOpacity != 0) {
            mat.setFloat("AlphaDiscardThreshold", 0.5f);
            rs.setBlendMode(BlendMode.Off);
            rs.setDepthWrite(true);
            rs.setDepthTest(true);
            rs.setColorWrite(true);
            rs.setFaceCullMode(FaceCullMode.Off);

            // Disable mipmap filtering so alpha stays sharp at distance
            disableMipmapsForCutout(mat);
        }
        // --- Semi-transparent materials (global Transparency value) ---
        // Glass, faded surfaces, etc. Need real alpha blending and must
        // NOT write to the depth buffer so geometry behind stays visible.
        else if (m.Transparency != 0) {
            rs.setBlendMode(BlendMode.Alpha);
            rs.setDepthWrite(false);
            rs.setDepthTest(true);
            rs.setFaceCullMode(FaceCullMode.Off);
        }
    }

    /**
     * Disable mipmap filtering on the diffuse/color texture of a material.
     * For cutout materials, mipmaps average the alpha channel at distance,
     * causing transparent areas to appear semi-opaque and block objects behind.
     * Using BilinearNoMipMaps keeps the alpha channel crisp at any distance.
     */
    private static void disableMipmapsForCutout(Material mat) {
        // Try DiffuseMap (Lighting.j3md) then ColorMap (Unshaded.j3md)
        String[] texParams = { "DiffuseMap", "ColorMap" };
        for (String paramName : texParams) {
            MatParam param = mat.getParam(paramName);
            if (param != null && param.getValue() instanceof Texture) {
                Texture tex = (Texture) param.getValue();
                tex.setMinFilter(MinFilter.BilinearNoMipMaps);
                break;
            }
        }
    }
}
