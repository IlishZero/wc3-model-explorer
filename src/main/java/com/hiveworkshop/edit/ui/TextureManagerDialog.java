package com.hiveworkshop.edit.ui;

import static com.hiveworkshop.i18n.Messages.fmt;

import com.hiveworkshop.edit.ModelFileService;
import com.hiveworkshop.edit.TexturePathService;
import com.hiveworkshop.i18n.Messages;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.nio.file.Path;

/**
 * View and rewrite the model's texture paths: single-path cell editing plus a
 * batch find/replace (case-insensitive, literal). Writes back through
 * {@link ModelFileService} with the usual safety net. Replaceable textures
 * (replaceableId &gt; 0) have no path and show an empty cell.
 */
public final class TextureManagerDialog extends JDialog {

    private final Path modelFile;
    private final MdlxModel model;
    private final Runnable onSuccess;
    private final TextureTableModel tableModel;

    public static void open(JFrame owner, Path file, Runnable onSuccess) {
        MdlxModel model;
        try {
            model = ModelFileService.loadModel(file);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, fmt("edit.loadFailed", ex.getMessage()),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        new TextureManagerDialog(owner, file, model, onSuccess).setVisible(true);
    }

    private TextureManagerDialog(JFrame owner, Path file, MdlxModel model, Runnable onSuccess) {
        super(owner, fmt("tex.title", file.getFileName().toString()), true);
        this.modelFile = file;
        this.model = model;
        this.onSuccess = onSuccess;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(new Dimension(640, 400));
        setMinimumSize(new Dimension(480, 280));
        setLocationRelativeTo(owner);

        tableModel = new TextureTableModel();
        JTable table = new JTable(tableModel);
        table.setRowHeight(table.getRowHeight() + 4);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(360);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton batchButton = new JButton(Messages.get("tex.batchReplace"));
        batchButton.addActionListener(e -> batchReplace());
        JButton saveButton = new JButton(Messages.get("seq.save"));
        saveButton.addActionListener(e -> save());
        JButton cancelButton = new JButton(Messages.get("seq.cancel"));
        cancelButton.addActionListener(e -> dispose());
        buttons.add(batchButton);
        buttons.add(saveButton);
        buttons.add(cancelButton);
        add(buttons, BorderLayout.SOUTH);
    }

    private void batchReplace() {
        JTextField findField = new JTextField();
        JTextField replaceField = new JTextField();
        JPanel panel = new JPanel(new GridLayout(2, 2, 6, 6));
        panel.add(new JLabel(Messages.get("tex.find")));
        panel.add(findField);
        panel.add(new JLabel(Messages.get("tex.replaceWith")));
        panel.add(replaceField);

        int choice = JOptionPane.showConfirmDialog(this, panel, Messages.get("tex.batchReplaceTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;

        String find = findField.getText().trim();
        if (find.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.get("tex.findEmpty"),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        int changed = TexturePathService.replaceAll(model, find, replaceField.getText());
        tableModel.fireTableDataChanged();
        JOptionPane.showMessageDialog(this, fmt("tex.replacedCount", changed),
                Messages.get("tex.batchReplaceTitle"), JOptionPane.INFORMATION_MESSAGE);
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

    /** Path cells edit {@code MdlxTexture.path} live; empty input clears the path. */
    private final class TextureTableModel extends AbstractTableModel {
        private final String[] columns = {
                Messages.get("tex.column.index"),
                Messages.get("tex.column.path"),
                Messages.get("tex.column.replaceable"),
        };

        @Override public int getRowCount() {
            return model.textures.size();
        }

        @Override public int getColumnCount() {
            return columns.length;
        }

        @Override public String getColumnName(int column) {
            return columns[column];
        }

        @Override public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 2 ? Integer.class : String.class;
        }

        @Override public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 1;
        }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            var texture = model.textures.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> rowIndex;
                case 1 -> texture.path == null ? "" : texture.path;
                case 2 -> texture.replaceableId;
                default -> null;
            };
        }

        @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex != 1) return;
            model.textures.get(rowIndex).path = String.valueOf(aValue).trim();
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}
