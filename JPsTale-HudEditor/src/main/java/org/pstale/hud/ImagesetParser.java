package org.pstale.hud;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parses CEGUI-style .imageset XML files.
 * Each imageset references a single PNG atlas and defines named sub-regions.
 */
public class ImagesetParser {

    /** A single image region within an imageset atlas. */
    public static class ImageRegion {
        public final String name;
        public final int x, y, width, height;

        public ImageRegion(String name, int x, int y, int width, int height) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /** Result of parsing one imageset file. */
    public static class Imageset {
        public final String name;
        public final String imageFile;
        public final int nativeHorzRes;
        public final int nativeVertRes;
        public final List<ImageRegion> regions;

        public Imageset(String name, String imageFile, int nativeHorzRes, int nativeVertRes,
                        List<ImageRegion> regions) {
            this.name = name;
            this.imageFile = imageFile;
            this.nativeHorzRes = nativeHorzRes;
            this.nativeVertRes = nativeVertRes;
            this.regions = regions;
        }
    }

    /**
     * Parse an .imageset XML file and return its contents.
     * @param file the .imageset file to parse
     * @return parsed Imageset, or null on error
     */
    public static Imageset parse(File file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new FileInputStream(file));
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            String name = root.getAttribute("name");
            String imageFile = root.getAttribute("imagefile");

            int hRes = 800, vRes = 600;
            if (root.hasAttribute("nativeHorzRes")) {
                try { hRes = Integer.parseInt(root.getAttribute("nativeHorzRes")); } catch (NumberFormatException e) {}
            }
            if (root.hasAttribute("nativeVertRes")) {
                try { vRes = Integer.parseInt(root.getAttribute("nativeVertRes")); } catch (NumberFormatException e) {}
            }

            List<ImageRegion> regions = new ArrayList<ImageRegion>();
            NodeList imageNodes = root.getElementsByTagName("Image");
            for (int i = 0; i < imageNodes.getLength(); i++) {
                Element img = (Element) imageNodes.item(i);
                String imgName = img.getAttribute("name");
                int x = Integer.parseInt(img.getAttribute("xPos"));
                int y = Integer.parseInt(img.getAttribute("yPos"));
                int w = Integer.parseInt(img.getAttribute("width"));
                int h = Integer.parseInt(img.getAttribute("height"));
                regions.add(new ImageRegion(imgName, x, y, w, h));
            }

            System.out.println("[ImagesetParser] Parsed " + file.getName()
                    + ": " + regions.size() + " regions from atlas '" + imageFile + "'");

            return new Imageset(name, imageFile, hRes, vRes, regions);

        } catch (Exception e) {
            System.err.println("[ImagesetParser] Failed to parse " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Find and parse all .imageset files in the given ui directory.
     * Looks in {@code uiDir/imagesets/} for .imageset files.
     * @param uiDir the root ui/ directory
     * @return list of parsed imagesets
     */
    public static List<Imageset> parseAll(File uiDir) {
        List<Imageset> result = new ArrayList<Imageset>();
        File imagesetsDir = new File(uiDir, "imagesets");
        if (!imagesetsDir.isDirectory()) {
            System.err.println("[ImagesetParser] imagesets/ directory not found in: " + uiDir);
            return result;
        }

        File[] files = imagesetsDir.listFiles();
        if (files == null) return result;

        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".imageset")) {
                Imageset is = parse(f);
                if (is != null) {
                    result.add(is);
                }
            }
        }

        return result;
    }
}
