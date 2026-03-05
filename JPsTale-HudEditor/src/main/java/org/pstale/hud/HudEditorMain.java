package org.pstale.hud;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.prefs.BackingStoreException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.pstale.assets.AssetFactory;

import com.jme3.system.AppSettings;

/**
 * Launcher dialog for the HUD Editor application.
 * Asks the user for the game client root directory and launches the editor.
 */
public final class HudEditorMain extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final String TITLE = "Editor de HUD";

    private final AppSettings source;
    private JTextField clientRootTxt;
    private JTextField uiDirTxt;
    private JFileChooser chooser;

    static String CLIENT_ROOT;
    static String UI_DIR;

    private static final int APP_WIDTH = 1920;
    private static final int APP_HEIGHT = 1080;

    public HudEditorMain() {
        source = new AppSettings(true);
        source.setTitle(TITLE);

        // Load saved settings (client root, etc.)
        AppSettings saved = new AppSettings(true);
        try {
            saved.load(TITLE);
            source.copyFrom(saved);
        } catch (BackingStoreException ex) {
            // First run — use defaults
        }

        // Always force resolution after loading saved settings
        source.setWidth(APP_WIDTH);
        source.setHeight(APP_HEIGHT);
        source.setMinWidth(APP_WIDTH);
        source.setMinHeight(APP_HEIGHT);
        source.setResizable(false);

        // Also try to load from the zone manager settings for the client root
        try {
            AppSettings fieldSettings = new AppSettings(true);
            fieldSettings.load("Gerenciador de Zonas");
            String fieldClientRoot = fieldSettings.getString("ClientRoot");
            if (fieldClientRoot != null && source.getString("ClientRoot") == null) {
                source.put("ClientRoot", fieldClientRoot);
            }
        } catch (BackingStoreException ex) {
            // Ignore
        }

        // Default UI directory if not set
        if (source.getString("UiDir") == null) {
            source.put("UiDir", "");
        }

        setResizable(false);
        setTitle(TITLE);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        createUI();
    }

    private void createUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Ignore
        }

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc;

        // --- Client root path ---
        JPanel pathPanel = new JPanel(new GridBagLayout());
        pathPanel.setBorder(BorderFactory.createTitledBorder("Caminho do Cliente"));

        JLabel clientLabel = new JLabel("Pasta raiz do cliente:");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        pathPanel.add(clientLabel, gbc);

        CLIENT_ROOT = source.getString("ClientRoot");
        if (CLIENT_ROOT == null) CLIENT_ROOT = "";

        clientRootTxt = new JTextField(CLIENT_ROOT, 30);
        clientRootTxt.setEditable(false);
        if (AssetFactory.checkClientRoot(CLIENT_ROOT)) {
            clientRootTxt.setBackground(new Color(0.8f, 1f, 0.8f));
        }
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(4, 4, 4, 4);
        pathPanel.add(clientRootTxt, gbc);

        JButton browseBtn = new JButton("Procurar...");
        browseBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setupClient();
            }
        });
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 4, 4, 4);
        pathPanel.add(browseBtn, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(pathPanel, gbc);

        // --- Server UI directory ---
        JPanel uiPanel = new JPanel(new GridBagLayout());
        uiPanel.setBorder(BorderFactory.createTitledBorder("Server UI (imagesets)"));

        JLabel uiLabel = new JLabel("Pasta ui/ do servidor:");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        uiPanel.add(uiLabel, gbc);

        UI_DIR = source.getString("UiDir");
        if (UI_DIR == null) UI_DIR = "";

        uiDirTxt = new JTextField(UI_DIR, 30);
        uiDirTxt.setEditable(false);
        if (UI_DIR.length() > 0 && new File(UI_DIR, "imagesets").isDirectory()) {
            uiDirTxt.setBackground(new Color(0.8f, 1f, 0.8f));
        }
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(4, 4, 4, 4);
        uiPanel.add(uiDirTxt, gbc);

        JButton browsUiBtn = new JButton("Procurar...");
        browsUiBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setupUiDir();
            }
        });
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 4, 4, 4);
        uiPanel.add(browsUiBtn, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(6, 0, 0, 0);
        mainPanel.add(uiPanel, gbc);

        // --- Info ---
        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("Informações"));

        JLabel infoLabel = new JLabel("<html>"
                + "Editor visual de HUD para Priston Tale.<br>"
                + "Arraste elementos para reposicioná-los,<br>"
                + "use o painel de propriedades para ajustar<br>"
                + "tamanho, posição e visibilidade."
                + "</html>");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        infoPanel.add(infoLabel, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(6, 0, 0, 0);
        mainPanel.add(infoPanel, gbc);

        // --- Buttons ---
        JPanel buttonPanel = new JPanel();

        JButton okButton = new JButton("Iniciar Editor");
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (verifyAndSave()) {
                    dispose();
                    startApp();
                }
            }
        });
        buttonPanel.add(okButton);

        JButton cancelButton = new JButton("Cancelar");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        buttonPanel.add(cancelButton);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.insets = new Insets(10, 0, 0, 0);
        mainPanel.add(buttonPanel, gbc);

        getContentPane().add(mainPanel, BorderLayout.CENTER);
        pack();
        getRootPane().setDefaultButton(okButton);
    }

    private JFileChooser getChooser() {
        if (chooser == null) {
            chooser = new JFileChooser();
            chooser.setDialogType(JFileChooser.OPEN_DIALOG);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Selecione a pasta do cliente");
        }
        return chooser;
    }

    private void setupClient() {
        if (getChooser().showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (file != null) {
            CLIENT_ROOT = file.getAbsolutePath().replaceAll("\\\\", "/");
            clientRootTxt.setText(CLIENT_ROOT);
            if (AssetFactory.checkClientRoot(CLIENT_ROOT)) {
                clientRootTxt.setBackground(new Color(0.8f, 1f, 0.8f));
                source.put("ClientRoot", CLIENT_ROOT);
            } else {
                clientRootTxt.setBackground(Color.YELLOW);
            }
        }
    }

    private void setupUiDir() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogType(JFileChooser.OPEN_DIALOG);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Selecione a pasta 'ui' do servidor (contendo imagesets/)");
        if (UI_DIR.length() > 0) fc.setCurrentDirectory(new File(UI_DIR));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        if (file != null) {
            UI_DIR = file.getAbsolutePath().replaceAll("\\\\", "/");
            uiDirTxt.setText(UI_DIR);
            if (new File(UI_DIR, "imagesets").isDirectory()) {
                uiDirTxt.setBackground(new Color(0.8f, 1f, 0.8f));
                source.put("UiDir", UI_DIR);
            } else {
                uiDirTxt.setBackground(Color.YELLOW);
            }
        }
    }

    private boolean verifyAndSave() {
        if (!AssetFactory.checkClientRoot(CLIENT_ROOT)) {
            JOptionPane.showMessageDialog(this,
                    "Caminho do cliente inválido. Selecione a pasta raiz do Priston Tale.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        source.put("ClientRoot", CLIENT_ROOT);
        source.put("UiDir", UI_DIR);
        try {
            source.save(TITLE);
        } catch (BackingStoreException ex) {
            // Ignore
        }
        return true;
    }

    private void startApp() {
        new Thread() {
            public void run() {
                final HudEditorApp app = new HudEditorApp();
                app.setSettings(source);
                app.setPauseOnLostFocus(false);
                app.setShowSettings(false);
                app.start();
            }
        }.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                HudEditorMain dialog = new HudEditorMain();
                dialog.setLocationRelativeTo(null);
                dialog.setVisible(true);
                dialog.toFront();
            }
        });
    }
}
