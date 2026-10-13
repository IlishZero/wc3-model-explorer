package com.hiveworkshop.edit.ui;

import static com.hiveworkshop.i18n.Messages.get;
import static com.hiveworkshop.i18n.Messages.fmt;

import com.hiveworkshop.edit.BackupManager;
import com.hiveworkshop.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Lets the user pick one of a model's {@code backup/*.bak} copies and restore
 * it over the current file. The current file is backed up first, so a restore
 * is itself reversible.
 */
public final class BackupRestoreDialog extends JDialog {

    private final Path original;
    private final List<Path> backups;
    private final Runnable onSuccess;
    private final JList<Path> backupList;

    /** Shows an info message instead of the dialog when the file has no backups. */
    public static void open(JFrame owner, Path original, Runnable onSuccess) {
        List<Path> backups;
        try {
            backups = BackupManager.backupsFor(original);
        } catch (IOException ex) {
            backups = List.of();
        }
        if (backups.isEmpty()) {
            JOptionPane.showMessageDialog(owner, get("main.noBackups"), get("restore.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new BackupRestoreDialog(owner, original, backups, onSuccess).setVisible(true);
    }

    private BackupRestoreDialog(JFrame owner, Path original, List<Path> backups, Runnable onSuccess) {
        super(owner, get("restore.title"), true);
        this.original = original;
        this.backups = backups;
        this.onSuccess = onSuccess;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(new Dimension(520, 340));
        setLocationRelativeTo(owner);

        backupList = new JList<>(backups.toArray(Path[]::new));
        backupList.setSelectedIndex(0);
        backupList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean selected, boolean focused) {
                Component c = super.getListCellRendererComponent(list, value, index, selected, focused);
                if (c instanceof JLabel label && value instanceof Path path) {
                    long kb = 0;
                    try {
                        kb = Files.size(path) / 1024;
                    } catch (IOException ignored) {
                    }
                    label.setText(path.getFileName() + "  (" + kb + " KB)");
                }
                return c;
            }
        });
        add(new JScrollPane(backupList), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton restoreButton = new JButton(get("restore.confirmButton"));
        restoreButton.addActionListener(e -> restoreSelected());
        JButton openFolderButton = new JButton(get("restore.openFolder"));
        openFolderButton.addActionListener(e -> openBackupFolder());
        JButton closeButton = new JButton(get("restore.close"));
        closeButton.addActionListener(e -> dispose());
        buttons.add(restoreButton);
        buttons.add(openFolderButton);
        buttons.add(closeButton);
        add(buttons, BorderLayout.SOUTH);
    }

    private void restoreSelected() {
        Path selected = backupList.getSelectedValue();
        if (selected == null) return;
        int choice = JOptionPane.showConfirmDialog(this, fmt("restore.confirm", original),
                get("restore.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        try {
            BackupManager.restore(selected, original);
            JOptionPane.showMessageDialog(this, fmt("restore.done", original.getFileName()),
                    get("restore.title"), JOptionPane.INFORMATION_MESSAGE);
            onSuccess.run();
            dispose();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, fmt("restore.failed", ex.getMessage()),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openBackupFolder() {
        Path dir = BackupManager.backupDirFor(original);
        try {
            java.awt.Desktop.getDesktop().open(dir.toFile());
        } catch (Exception ex) {
            try {
                new ProcessBuilder("explorer", dir.toAbsolutePath().toString()).start();
            } catch (Exception ignored) {
            }
        }
    }
}
