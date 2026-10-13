package com.hiveworkshop.edit.ui;

import static com.hiveworkshop.i18n.Messages.fmt;

import com.hiveworkshop.edit.ModelFileService;
import com.hiveworkshop.i18n.Messages;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.MdlxSequence;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Edit-in-place manager for a model's animation sequences: rename, retime,
 * change rarity/move speed, delete — written back through
 * {@link SequenceEditService} (round-trip check + automatic backup).
 */
public final class SequenceManagerDialog extends JDialog {

    private final Path modelFile;
    private final MdlxModel model;
    private final Runnable onSuccess;
    private final SequenceTableModel tableModel;
    private final JTable table;

    /** Loads the model first so parse failures surface as a message instead of an empty dialog. */
    public static void open(JFrame owner, Path file, Runnable onSuccess) {
        MdlxModel model;
        try {
            model = ModelFileService.loadModel(file);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, fmt("edit.loadFailed", ex.getMessage()),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        new SequenceManagerDialog(owner, file, model, onSuccess).setVisible(true);
    }

    private SequenceManagerDialog(JFrame owner, Path file, MdlxModel model, Runnable onSuccess) {
        super(owner, fmt("seq.title", file.getFileName().toString()), true);
        this.modelFile = file;
        this.model = model;
        this.onSuccess = onSuccess;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(new Dimension(620, 440));
        setMinimumSize(new Dimension(480, 300));
        setLocationRelativeTo(owner);

        String[] columns = {
                Messages.get("seq.column.name"),
                Messages.get("seq.column.start"),
                Messages.get("seq.column.end"),
                Messages.get("seq.column.rarity"),
                Messages.get("seq.column.moveSpeed"),
        };
        Object[][] rows = new Object[model.sequences.size()][];
        for (int i = 0; i < model.sequences.size(); i++) {
            MdlxSequence s = model.sequences.get(i);
            rows[i] = new Object[] { s.name, s.interval[0], s.interval[1], s.rarity, s.moveSpeed };
        }
        tableModel = new SequenceTableModel(rows, columns);
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.setRowHeight(table.getRowHeight() + 4);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton deleteButton = new JButton(Messages.get("seq.delete"));
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
        int choice = JOptionPane.showConfirmDialog(this, fmt("seq.deleteConfirm", rows.length),
                Messages.get("seq.delete"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        int[] sorted = rows.clone();
        Arrays.sort(sorted);
        for (int i = sorted.length - 1; i >= 0; i--) {
            model.sequences.remove(sorted[i]);
            tableModel.removeRow(sorted[i]);
        }
    }

    private void save() {
        for (int i = 0; i < model.sequences.size(); i++) {
            MdlxSequence s = model.sequences.get(i);
            if (s.interval[0] > s.interval[1]) {
                JOptionPane.showMessageDialog(this, fmt("seq.badInterval", s.name),
                        Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
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

    /** Edits go straight into the underlying {@link MdlxSequence} objects; bad input reverts the cell. */
    private final class SequenceTableModel extends DefaultTableModel {
        SequenceTableModel(Object[][] rows, String[] columns) {
            super(rows, columns);
        }

        @Override public boolean isCellEditable(int row, int column) {
            return true;
        }

        @Override public void setValueAt(Object aValue, int row, int column) {
            MdlxSequence seq = model.sequences.get(row);
            String value = String.valueOf(aValue).trim();
            try {
                switch (column) {
                    case 0 -> {
                        if (value.isEmpty()) throw new NumberFormatException(value);
                        seq.name = value;
                    }
                    case 1 -> seq.interval[0] = Long.parseLong(value);
                    case 2 -> seq.interval[1] = Long.parseLong(value);
                    case 3 -> seq.rarity = Float.parseFloat(value);
                    case 4 -> seq.moveSpeed = Float.parseFloat(value);
                    default -> { }
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(SequenceManagerDialog.this,
                        fmt("seq.invalidNumber", value), Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
                fireTableCellUpdated(row, column);
                return;
            }
            super.setValueAt(value, row, column);
        }
    }
}
