import java.io.BufferedInputStream;
import java.io.FileInputStream;

import com.jme3.scene.plugins.smd.geom.PAT3D;
import com.jme3.util.LittleEndien;

public class TestSmdLoad {
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0]
                : "C:\\Users\\starl\\Downloads\\MonsterPKv4410\\image\\Sinimage\\Items\\DropItem\\itSDS268.smd";
        System.out.println("Loading: " + path);

        FileInputStream fis = new FileInputStream(path);
        LittleEndien in = new LittleEndien(new BufferedInputStream(fis));

        PAT3D pat = new PAT3D();
        pat.loadFile(in);

        System.out.println("objCount=" + pat.objCount);
        if (pat.materialGroup != null) {
            System.out.println("materialCount=" + pat.materialGroup.materialCount);
            if (pat.materialGroup.materials != null) {
                for (int i = 0; i < pat.materialGroup.materialCount; i++) {
                    System.out.println("  mat[" + i + "] InUse=" + pat.materialGroup.materials[i].InUse
                            + " TexCounter=" + pat.materialGroup.materials[i].TextureCounter);
                    if (pat.materialGroup.materials[i].smTexture != null) {
                        for (int j = 0; j < pat.materialGroup.materials[i].TextureCounter; j++) {
                            if (pat.materialGroup.materials[i].smTexture[j] != null) {
                                System.out.println(
                                        "    tex[" + j + "]=" + pat.materialGroup.materials[i].smTexture[j].Name);
                            }
                        }
                    }
                }
            }
        }
        for (int i = 0; i < pat.objCount; i++) {
            System.out.println("obj[" + i + "] nVertex=" + pat.objArray[i].nVertex
                    + " nFace=" + pat.objArray[i].nFace
                    + " nTexLink=" + pat.objArray[i].nTexLink
                    + " MaxVertex=" + pat.objArray[i].MaxVertex
                    + " MaxFace=" + pat.objArray[i].MaxFace
                    + " NodeName='" + pat.objArray[i].NodeName + "'"
                    + " NodeParent='" + pat.objArray[i].NodeParent + "'");
            // Print some face data if present
            if (pat.objArray[i].Face != null) {
                System.out.println("  Face array length=" + pat.objArray[i].Face.length);
            }
            if (pat.objArray[i].Vertex != null && pat.objArray[i].Vertex.length > 0) {
                System.out.println("  Vertex[0] v=(" + pat.objArray[i].Vertex[0].v.x
                        + "," + pat.objArray[i].Vertex[0].v.y + "," + pat.objArray[i].Vertex[0].v.z + ")");
            }
        }

        fis.close();
        System.out.println("OK");
    }
}
