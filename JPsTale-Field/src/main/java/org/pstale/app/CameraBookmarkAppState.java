package org.pstale.app;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

/**
 * Allows designers to save and restore named camera positions (bookmarks).
 * <p>
 * Bookmarks are persisted to a JSON file so they can be reloaded next
 * session and shared across the team.
 * <p>
 * File format (camera_bookmarks.json):
 * 
 * <pre>
 * [
 *   {
 *     "name": "Vista do Castelo",
 *     "px": 100.0, "py": 200.0, "pz": 300.0,
 *     "rx": 0.0, "ry": 0.0, "rz": 0.0, "rw": 1.0
 *   },
 *   ...
 * ]
 * </pre>
 */
public class CameraBookmarkAppState extends SubAppState {

    private static final Logger logger = LoggerFactory.getLogger(CameraBookmarkAppState.class);

    private static final String BOOKMARKS_FILE = "camera_bookmarks.json";

    public static class Bookmark {
        public String name;
        public Vector3f position;
        public Quaternion rotation;

        public Bookmark(String name, Vector3f position, Quaternion rotation) {
            this.name = name;
            this.position = position;
            this.rotation = rotation;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final List<Bookmark> bookmarks = new ArrayList<>();

    /** Directory where bookmark file is stored. */
    private String saveDirectory;

    @Override
    protected void initialize(Application app) {
    }

    @Override
    protected void cleanup(Application app) {
    }

    /**
     * Set the directory where bookmarks should be saved/loaded.
     */
    public void setSaveDirectory(String dir) {
        this.saveDirectory = dir;
    }

    /**
     * Save the current camera position as a named bookmark.
     */
    public void addBookmark(String name) {
        Camera cam = getApplication().getCamera();
        if (name == null || name.trim().isEmpty()) {
            name = "Bookmark " + (bookmarks.size() + 1);
        }
        Bookmark bm = new Bookmark(
                name,
                cam.getLocation().clone(),
                cam.getRotation().clone());
        bookmarks.add(bm);
        logger.info("Bookmark adicionado: '{}' em {}", name, bm.position);
    }

    /**
     * Teleport the camera to the given bookmark.
     */
    public void goToBookmark(int index) {
        if (index < 0 || index >= bookmarks.size())
            return;
        Bookmark bm = bookmarks.get(index);
        Camera cam = getApplication().getCamera();
        cam.setLocation(bm.position.clone());
        cam.setRotation(bm.rotation.clone());
        logger.info("Camera movida para bookmark: '{}'", bm.name);
    }

    /**
     * Teleport the camera to a bookmark by name.
     */
    public void goToBookmark(String name) {
        for (int i = 0; i < bookmarks.size(); i++) {
            if (bookmarks.get(i).name.equals(name)) {
                goToBookmark(i);
                return;
            }
        }
        logger.warn("Bookmark nao encontrado: '{}'", name);
    }

    /**
     * Remove a bookmark by index.
     */
    public void removeBookmark(int index) {
        if (index >= 0 && index < bookmarks.size()) {
            Bookmark removed = bookmarks.remove(index);
            logger.info("Bookmark removido: '{}'", removed.name);
        }
    }

    /**
     * Get all bookmark names for UI display.
     */
    public List<String> getBookmarkNames() {
        List<String> names = new ArrayList<>(bookmarks.size());
        for (Bookmark bm : bookmarks) {
            names.add(bm.name);
        }
        return names;
    }

    /**
     * Get bookmark count.
     */
    public int getCount() {
        return bookmarks.size();
    }

    /**
     * Clear all bookmarks.
     */
    public void clearBookmarks() {
        bookmarks.clear();
    }

    // ========================================================================
    // Persistence
    // ========================================================================

    /**
     * Save bookmarks to JSON.
     */
    public void saveBookmarks() {
        if (saveDirectory == null || saveDirectory.isEmpty()) {
            logger.warn("Diretorio de salvamento nao definido, bookmarks nao salvos.");
            return;
        }

        JSONArray arr = new JSONArray();
        for (Bookmark bm : bookmarks) {
            JSONObject obj = new JSONObject();
            obj.put("name", bm.name);
            obj.put("px", bm.position.x);
            obj.put("py", bm.position.y);
            obj.put("pz", bm.position.z);
            obj.put("rx", bm.rotation.getX());
            obj.put("ry", bm.rotation.getY());
            obj.put("rz", bm.rotation.getZ());
            obj.put("rw", bm.rotation.getW());
            arr.put(obj);
        }

        File file = new File(saveDirectory, BOOKMARKS_FILE);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(arr.toString(2));
            logger.info("Bookmarks salvos em: {}", file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Erro ao salvar bookmarks", e);
        }
    }

    /**
     * Load bookmarks from JSON.
     */
    public void loadBookmarks() {
        if (saveDirectory == null || saveDirectory.isEmpty())
            return;

        File file = new File(saveDirectory, BOOKMARKS_FILE);
        if (!file.exists())
            return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONArray arr = new JSONArray(sb.toString());
            bookmarks.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.getString("name");
                Vector3f pos = new Vector3f(
                        (float) obj.getDouble("px"),
                        (float) obj.getDouble("py"),
                        (float) obj.getDouble("pz"));
                Quaternion rot = new Quaternion(
                        (float) obj.getDouble("rx"),
                        (float) obj.getDouble("ry"),
                        (float) obj.getDouble("rz"),
                        (float) obj.getDouble("rw"));
                bookmarks.add(new Bookmark(name, pos, rot));
            }
            logger.info("Carregados {} bookmarks de {}", bookmarks.size(), file.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Erro ao carregar bookmarks", e);
        }
    }
}
