package com.hiveworkshop.edit.ui;

import static com.hiveworkshop.i18n.Messages.fmt;

import com.hiveworkshop.edit.GeosetEditService;
import com.hiveworkshop.edit.ModelFileService;
import com.hiveworkshop.i18n.Messages;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;

/**
 * Inspect and delete geosets of a model. Deletion cleans up geoset animations
 * and bone references (see {@link GeosetEditService}) and goes through the
 * shared write-back safety net of {@link ModelFileService}. Rows are
 * read-only — this dialog is about pruning, not editing geometry.
 */
public final class GeosetManagerDialog extends JDialog {

    private final Path modelFile;
    private final MdlxModel model;
    private final Runnable onSuccess;
    private final GeosetTableModel tableModel;
    private final JTable table;

    public static void open(JFrame owner, Path file, Runnable onSuccess) {
        MdlxModel model;
        try {
            model = ModelFileService.loadModel(file);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, fmt("edit.loadFailed", ex.getMessage()),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        new GeosetManagerDialog(owner, file, model, onSuccess).setVisible(true);
    }

    private GeosetManagerDialog(JFrame owner, Path file, MdlxModel model, Runnable onSuccess) {
        super(owner, fmt("geo.title", file.getFileName().toString()), true);
        this.modelFile = file;
        this.model = model;
        this.onSuccess = onSuccess;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(new Dimension(560, 400));
        setMinimumSize(new Dimension(440, 280));
        setLocationRelativeTo(owner);

        tableModel = new GeosetTableModel();
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setRowHeight(table.getRowHeight() + 4);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton deleteButton = new JButton(Messages.get("geo.delete"));
        deleteButton.addActionListener(e -> deleteSelected());
        JButton saveButton = new JButton(Messages.get("seq.save"));
        saveButton.addActionListener(e -> save());
        JButton cancelButton = new JButton(Messages.get("seq.cancel"));
        cancelButton.addActionListener(e -> dispose());
        buttons.add(deleteButton);
        buttons.add(saveButton);
        buttons.add(cancelButton);
        add(buttons, BorderLayout.SOUTH);
    }

    private void deleteSelected() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) return;
        int choice = JOptionPane.showConfirmDialog(this, fmt("geo.deleteConfirm", rows.length),
                Messages.get("geo.delete"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        int[] sorted = rows.clone();
        java.util.Arrays.sort(sorted);
        for (int i = sorted.length - 1; i >= 0; i--) {
            GeosetEditService.removeGeosetAt(model, sorted[i]);
        }
        tableModel.reload();
    }

    private void save() {
        // Human gate before anything touches the disk (P2-16)
        if (!WritePreview.confirm(this, modelFile, model)) return;
        ModelFileService.WriteResult result = ModelFileService.writeModel(modelFile, model);
        if (result.success()) {
            JOptionPane.showMessageDialog(this, fmt("edit.saved",
                    modelFile.getFileName(), result.backupPath().getFileName()),
                    Messages.get("seq.save"), JOptionPane.INFORMATION_MESSAGE);
            onSuccess.run();
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, fmt("edit.saveFailed", result.message()),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private final class GeosetTableModel extends AbstractTableModel {
        private final String[] columns = {
                Messages.get("geo.column.index"),
                Messages.get("geo.column.vertices"),
                Messages.get("geo.column.faces"),
                Messages.get("geo.column.material"),
                Messages.get("geo.column.lod"),
        };

        private void reload() {
            fireTableDataChanged();
        }

        @Override public int getRowCount() {
            return model.geosets.size();
        }

        @Override public int getColumnCount() {
            return columns.length;
        }

        @Override public String getColumnName(int column) {
            return columns[column];
        }

        @Override public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 4 ? String.class : Integer.class;
        }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            var geoset = model.geosets.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> rowIndex;
                case 1 -> geoset.vertices == null ? 0 : geoset.vertices.length / 3;
                case 2 -> geoset.faces == null ? 0 : geoset.faces.length / 3;
                case 3 -> (int) geoset.materialId;
                case 4 -> geoset.lodName == null || geoset.lodName.isEmpty() ? String.valueOf(geoset.lod) : geoset.lodName;
                default -> null;
            };
        }
    }
}
