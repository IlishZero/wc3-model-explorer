package com.hiveworkshop.edit.ui;

import static com.hiveworkshop.i18n.Messages.fmt;

import com.hiveworkshop.edit.MaterialEditService;
import com.hiveworkshop.edit.ModelFileService;
import com.hiveworkshop.i18n.Messages;
import com.hiveworkshop.model.ModelAsset;
import com.hiveworkshop.rms.editor.model.util.FilterMode;
import com.hiveworkshop.rms.parsers.mdlx.MdlxLayer;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Material/layer editor (P3a): per-material layer table (filter mode, texture
 * slot, alpha), the six standard layer flags as checkboxes, and add/remove
 * layer. The texture slot editor validates against the model's texture list
 * (pairs with the texture path manager). Writes back through the safety net;
 * after saving the model can be opened in the viewer for a rendered check.
 */
public final class MaterialManagerDialog extends JDialog {

    private final ModelAsset asset;
    private final MdlxModel model;
    private final Runnable onSuccess;
    private final Consumer<ModelAsset> previewOpener;

    private final DefaultListModel<String> materialListModel = new DefaultListModel<>();
    private final JList<String> materialList = new JList<>(materialListModel);
    private final LayerTableModel layerTableModel;
    private final JTable layerTable;
    private final List<JCheckBox> flagBoxes = new ArrayList<>();

    private int currentMaterial = -1;

    public static void open(JFrame owner, ModelAsset asset, Runnable onSuccess, Consumer<ModelAsset> previewOpener) {
        MdlxModel model;
        try {
            model = ModelFileService.loadModel(asset.path());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, fmt("edit.loadFailed", ex.getMessage()),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (model.materials.isEmpty()) {
            JOptionPane.showMessageDialog(owner, Messages.get("mat.none"),
                    Messages.get("mat.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new MaterialManagerDialog(owner, asset, model, onSuccess, previewOpener).setVisible(true);
    }

    private MaterialManagerDialog(JFrame owner, ModelAsset asset, MdlxModel model,
                                  Runnable onSuccess, Consumer<ModelAsset> previewOpener) {
        super(owner, fmt("mat.title", asset.fileName()), true);
        this.asset = asset;
        this.model = model;
        this.onSuccess = onSuccess;
        this.previewOpener = previewOpener;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(new Dimension(760, 480));
        setMinimumSize(new Dimension(620, 380));
        setLocationRelativeTo(owner);

        // Left: material list with usage counts
        refreshMaterialList();
        materialList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        materialList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) selectMaterial(materialList.getSelectedIndex());
        });
        JPanel materialPanel = new JPanel(new BorderLayout(0, 4));
        JLabel materialHeader = new JLabel(Messages.get("mat.materials"));
        materialHeader.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 0));
        materialPanel.add(materialHeader, BorderLayout.NORTH);
        materialPanel.add(new JScrollPane(materialList), BorderLayout.CENTER);
        materialPanel.setPreferredSize(new Dimension(240, 0));

        // Right: layer table of the selected material
        layerTableModel = new LayerTableModel();
        layerTable = new JTable(layerTableModel);
        layerTable.setRowHeight(layerTable.getRowHeight() + 4);
        layerTable.getColumnModel().getColumn(1).setCellEditor(filterModeEditor());
        layerTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        layerTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        layerTable.getColumnModel().getColumn(2).setPreferredWidth(220);
        layerTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value,
                                                                     boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, foc, row, col);
                if (col == 2 && !sel && textureMissing(row)) c.setForeground(new Color(196, 42, 42));
                return c;
            }
        });
        layerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) syncFlagBoxes();
        });

        JPanel layerPanel = new JPanel(new BorderLayout(0, 4));
        JLabel layerHeader = new JLabel(Messages.get("mat.layers"));
        layerHeader.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 0));
        layerPanel.add(layerHeader, BorderLayout.NORTH);
        layerPanel.add(new JScrollPane(layerTable), BorderLayout.CENTER);

        // Flags for the selected layer. Upstream jar bug: for MDL text files the
        // Unlit/NoDepthSet bits do not write back faithfully — disable them there.
        boolean mdlLimited = asset.fileName().toLowerCase(java.util.Locale.ROOT).endsWith(".mdl");
        JPanel flagPanel = new JPanel(new java.awt.GridLayout(2, 3, 4, 2));
        flagPanel.setBorder(BorderFactory.createTitledBorder(Messages.get("mat.flags")));
        int[] flagBits = {
                MaterialEditService.FLAG_UNSHADED, MaterialEditService.FLAG_SPHERE_ENV_MAP,
                MaterialEditService.FLAG_TWO_SIDED, MaterialEditService.FLAG_UNLIT,
                MaterialEditService.FLAG_NO_DEPTH_TEST, MaterialEditService.FLAG_NO_DEPTH_SET
        };
        String[] flagKeys = {
                "mat.flag.unshaded", "mat.flag.sphereEnv", "mat.flag.twoSided",
                "mat.flag.unlit", "mat.flag.noDepthTest", "mat.flag.noDepthSet"
        };
        for (int i = 0; i < flagBits.length; i++) {
            final int bit = flagBits[i];
            JCheckBox box = new JCheckBox(Messages.get(flagKeys[i]));
            boolean limited = mdlLimited && (bit == MaterialEditService.FLAG_UNLIT
                    || bit == MaterialEditService.FLAG_NO_DEPTH_SET);
            box.setEnabled(!limited);
            if (limited) box.setToolTipText(Messages.get("mat.flag.mdlLimited"));
            box.addActionListener(e -> toggleFlag(bit, box.isSelected()));
            flagBoxes.add(box);
            flagPanel.add(box);
        }
        layerPanel.add(flagPanel, BorderLayout.SOUTH);

        JPanel buttons = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton addLayerButton = new JButton(Messages.get("mat.addLayer"));
        addLayerButton.addActionListener(e -> addLayer());
        JButton delLayerButton = new JButton(Messages.get("mat.delLayer"));
        delLayerButton.addActionListener(e -> deleteSelectedLayer());
        JButton saveButton = new JButton(Messages.get("seq.save"));
        saveButton.addActionListener(e -> save());
        JButton cancelButton = new JButton(Messages.get("seq.cancel"));
        cancelButton.addActionListener(e -> dispose());
        buttons.add(addLayerButton);
        buttons.add(delLayerButton);
        buttons.add(saveButton);
        buttons.add(cancelButton);

        add(materialPanel, BorderLayout.WEST);
        add(layerPanel, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        materialList.setSelectedIndex(0);
    }

    private void refreshMaterialList() {
        int[] usage = MaterialEditService.geosetUsage(model);
        materialListModel.clear();
        for (int i = 0; i < model.materials.size(); i++) {
            materialListModel.addElement(fmt("mat.usage", i, model.materials.get(i).layers.size(), usage[i]));
        }
    }

    private void selectMaterial(int index) {
        currentMaterial = (index >= 0 && index < model.materials.size()) ? index : -1;
        layerTableModel.fireTableDataChanged();
        syncFlagBoxes();
    }

    private boolean textureMissing(int row) {
        if (currentMaterial < 0) return false;
        var layers = model.materials.get(currentMaterial).layers;
        if (row < 0 || row >= layers.size()) return false;
        MdlxLayer layer = layers.get(row);
        return layer.textureId < 0 || layer.textureId >= model.textures.size();
    }

    private String textureDisplay(MdlxLayer layer) {
        int texId = layer.textureId;
        if (texId < 0 || texId >= model.textures.size()) return "";
        var texture = model.textures.get(texId);
        if (texture.replaceableId > 0) return fmt("mat.replaceable", texture.replaceableId);
        return texture.path == null ? "" : texture.path;
    }

    private javax.swing.DefaultCellEditor filterModeEditor() {
        JComboBox<FilterMode> combo = new JComboBox<>(FilterMode.values());
        return new DefaultCellEditor(combo);
    }

    private void syncFlagBoxes() {
        int row = layerTable.getSelectedRow();
        int flags = 0;
        if (currentMaterial >= 0 && row >= 0 && row < model.materials.get(currentMaterial).layers.size()) {
            flags = model.materials.get(currentMaterial).layers.get(row).flags;
        }
        int[] bits = {
                MaterialEditService.FLAG_UNSHADED, MaterialEditService.FLAG_SPHERE_ENV_MAP,
                MaterialEditService.FLAG_TWO_SIDED, MaterialEditService.FLAG_UNLIT,
                MaterialEditService.FLAG_NO_DEPTH_TEST, MaterialEditService.FLAG_NO_DEPTH_SET
        };
        for (int i = 0; i < flagBoxes.size(); i++) {
            flagBoxes.get(i).setSelected((flags & bits[i]) != 0);
        }
    }

    private void toggleFlag(int bit, boolean on) {
        int row = layerTable.getSelectedRow();
        if (currentMaterial < 0 || row < 0) return;
        var layers = model.materials.get(currentMaterial).layers;
        if (row >= layers.size()) return;
        MdlxLayer layer = layers.get(row);
        layer.flags = on ? (layer.flags | bit) : (layer.flags & ~bit);
        layerTableModel.fireTableRowsUpdated(row, row);
    }

    private void addLayer() {
        if (currentMaterial < 0) return;
        try {
            MaterialEditService.addLayer(model, currentMaterial);
            layerTableModel.fireTableDataChanged();
            refreshMaterialList();
        } catch (IllegalArgumentException ex) {
            showError(ex);
        }
    }

    private void deleteSelectedLayer() {
        int row = layerTable.getSelectedRow();
        if (currentMaterial < 0 || row < 0) return;
        try {
            MaterialEditService.removeLayer(model, currentMaterial, row);
            layerTableModel.fireTableDataChanged();
            refreshMaterialList();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void save() {
        if (!WritePreview.confirm(this, asset.path(), model)) return;
        ModelFileService.WriteResult result = ModelFileService.writeModel(asset.path(), model);
        if (!result.success()) {
            JOptionPane.showMessageDialog(this, fmt("edit.saveFailed", result.message()),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(this, fmt("edit.saved",
                asset.path().getFileName(), result.backupPath().getFileName()),
                Messages.get("seq.save"), JOptionPane.INFORMATION_MESSAGE);
        int preview = JOptionPane.showConfirmDialog(this, Messages.get("mat.previewPrompt"),
                Messages.get("mat.title"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        onSuccess.run();
        dispose();
        if (preview == JOptionPane.YES_OPTION) previewOpener.accept(asset);
    }

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(),
                Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
    }

    private final class LayerTableModel extends AbstractTableModel {
        private final String[] columns = {
                Messages.get("mat.col.index"),
                Messages.get("mat.col.filter"),
                Messages.get("mat.col.texture"),
                Messages.get("mat.col.texId"),
                Messages.get("mat.col.alpha"),
        };

        @Override public int getRowCount() {
            return currentMaterial < 0 ? 0 : model.materials.get(currentMaterial).layers.size();
        }

        @Override public int getColumnCount() {
            return columns.length;
        }

        @Override public String getColumnName(int column) {
            return columns[column];
        }

        @Override public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0, 3 -> Integer.class;
                case 4 -> Float.class;
                default -> String.class;
            };
        }

        @Override public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 1 || columnIndex == 3 || columnIndex == 4;
        }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            MdlxLayer layer = model.materials.get(currentMaterial).layers.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> rowIndex;
                case 1 -> layer.filterMode == null ? FilterMode.NONE : layer.filterMode;
                case 2 -> textureDisplay(layer);
                case 3 -> layer.textureId;
                case 4 -> layer.alpha;
                default -> null;
            };
        }

        @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            String raw = String.valueOf(aValue).trim();
            try {
                switch (columnIndex) {
                    case 1 -> MaterialEditService.setLayerFilterMode(model, currentMaterial, rowIndex,
                            FilterMode.valueOf(raw));
                    case 3 -> MaterialEditService.setLayerTextureId(model, currentMaterial, rowIndex,
                            Integer.parseInt(raw));
                    case 4 -> MaterialEditService.setLayerAlpha(model, currentMaterial, rowIndex,
                            Float.parseFloat(raw));
                    default -> { }
                }
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(MaterialManagerDialog.this,
                        fmt("mat.invalidEdit", ex.getMessage()),
                        Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
                fireTableCellUpdated(rowIndex, columnIndex);
                return;
            }
            fireTableRowsUpdated(rowIndex, rowIndex);
        }
    }
}
