package com.hiveworkshop.edit.ui;

import static com.hiveworkshop.i18n.Messages.fmt;

import com.hiveworkshop.edit.ModelFileService;
import com.hiveworkshop.edit.TextureCheckService;
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
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only report of every texture reference in a model and whether the
 * renderer would find it (disk / MPQ / CASC / map archive / replaceable),
 * with the missing ones highlighted and copyable in one click.
 */
public final class TextureCheckDialog extends JDialog {

    private final List<TextureCheckService.TextureStatus> statuses;

    public static void open(JFrame owner, Path file) {
        MdlxModel model;
        try {
            model = ModelFileService.loadModel(file);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, fmt("edit.loadFailed", ex.getMessage()),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        Path modelDir = file.toAbsolutePath().getParent();
        List<TextureCheckService.TextureStatus> statuses =
                TextureCheckService.check(model, modelDir, null);
        new TextureCheckDialog(owner, file, statuses).setVisible(true);
    }

    private TextureCheckDialog(JFrame owner, Path file,
                               List<TextureCheckService.TextureStatus> statuses) {
        super(owner, fmt("chk.title", file.getFileName().toString(),
                statuses.stream().filter(s -> !s.found()).count(), statuses.size()), true);
        this.statuses = statuses;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(new Dimension(680, 420));
        setMinimumSize(new Dimension(500, 300));
        setLocationRelativeTo(owner);

        AbstractTableModel tableModel = new AbstractTableModel() {
            private final String[] columns = {
                    Messages.get("chk.column.path"),
                    Messages.get("chk.column.status"),
            };

            @Override public int getRowCount() {
                return statuses.size();
            }

            @Override public int getColumnCount() {
                return columns.length;
            }

            @Override public String getColumnName(int column) {
                return columns[column];
            }

            @Override public Object getValueAt(int rowIndex, int columnIndex) {
                return columnIndex == 0
                        ? statuses.get(rowIndex).path()
                        : localizedStatus(statuses.get(rowIndex).source());
            }
        };
        JTable table = new JTable(tableModel);
        table.setRowHeight(table.getRowHeight() + 4);
        table.getColumnModel().getColumn(0).setPreferredWidth(420);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value,
                                                                     boolean selected, boolean focused,
                                                                     int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focused, row, column);
                if (!selected && !statuses.get(row).found()) {
                    c.setForeground(new Color(196, 42, 42));
                }
                return c;
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton copyMissingButton = new JButton(Messages.get("chk.copyMissing"));
        copyMissingButton.addActionListener(e -> copyMissingPaths());
        JButton closeButton = new JButton(Messages.get("restore.close"));
        closeButton.addActionListener(e -> dispose());
        buttons.add(copyMissingButton);
        buttons.add(closeButton);
        add(buttons, BorderLayout.SOUTH);
    }

    private void copyMissingPaths() {
        List<String> missing = statuses.stream()
                .filter(s -> !s.found())
                .map(TextureCheckService.TextureStatus::path)
                .collect(Collectors.toList());
        if (missing.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.get("chk.noneMissing"),
                    Messages.get("chk.copyMissing"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String joined = String.join(System.lineSeparator(), missing);
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(joined), null);
        JOptionPane.showMessageDialog(this, fmt("chk.copyDone", missing.size()),
                Messages.get("chk.copyMissing"), JOptionPane.INFORMATION_MESSAGE);
    }

    private String localizedStatus(String source) {
        return switch (source) {
            case "DISK" -> Messages.get("statusSource.disk");
            case "MPQ" -> Messages.get("statusSource.mpq");
            case "CASC" -> Messages.get("statusSource.casc");
            case "MAP" -> Messages.get("statusSource.map");
            case "REPLACEABLE" -> Messages.get("statusSource.replaceable");
            default -> Messages.get("statusSource.missing");
        };
    }
}
