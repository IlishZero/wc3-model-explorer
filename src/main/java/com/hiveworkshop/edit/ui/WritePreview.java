package com.hiveworkshop.edit.ui;

import static com.hiveworkshop.i18n.Messages.fmt;

import com.hiveworkshop.edit.LineDiff;
import com.hiveworkshop.edit.ModelFileService;
import com.hiveworkshop.i18n.Messages;
import com.hiveworkshop.rms.parsers.mdlx.MdlLoadSave;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * The human gate of the write-back safety net (P2-16): before a file is
 * touched, both the on-disk model and the edited model are serialized to MDL
 * text by the same RMS writer and diffed. The user reviews the colored diff
 * and confirms; cancelling leaves everything untouched.
 *
 * Both sides go through the same serializer, so the diff shows content
 * changes only — never formatting noise.
 */
public final class WritePreview {

    private static final int MAX_DISPLAY_LINES = 4000;
    private static final int CONTEXT_LINES = 3;

    private WritePreview() {}

    /** Shows the diff and returns true when the user approves the write-back. */
    public static boolean confirm(java.awt.Component parent, Path file, MdlxModel edited) {
        String oldText;
        String newText;
        try {
            oldText = mdlText(ModelFileService.loadModel(file));
            newText = mdlText(edited);
        } catch (Exception ex) {
            int choice = JOptionPane.showConfirmDialog(parent,
                    fmt("diff.unpreviewable", String.valueOf(ex.getMessage())),
                    Messages.get("diff.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            return choice == JOptionPane.YES_OPTION;
        }

        List<LineDiff.Line> lines = LineDiff.diff(
                oldText.lines().toList(), newText.lines().toList());

        JDialog dialog = new JDialog(
                (java.awt.Frame) null, fmt("diff.title", file.getFileName().toString()), true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(new Dimension(760, 560));
        dialog.setLocationRelativeTo(parent);

        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        populate(pane.getStyledDocument(), lines);

        JPanel buttons = new JPanel();
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        boolean[] approved = { false };
        JButton confirmButton = new JButton(Messages.get("diff.confirm"));
        confirmButton.addActionListener(e -> {
            approved[0] = true;
            dialog.dispose();
        });
        JButton cancelButton = new JButton(Messages.get("seq.cancel"));
        cancelButton.addActionListener(e -> dialog.dispose());
        JButton copyButton = new JButton(Messages.get("diff.copy"));
        copyButton.addActionListener(e -> {
            StringBuilder sb = new StringBuilder();
            for (LineDiff.Line line : lines) {
                sb.append(prefix(line.type())).append(line.text()).append('\n');
            }
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(sb.toString()), null);
        });
        buttons.add(copyButton);
        buttons.add(confirmButton);
        buttons.add(cancelButton);

        dialog.add(new JScrollPane(pane), BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
        return approved[0];
    }

    private static String mdlText(MdlxModel model) {
        ByteBuffer buffer = MdlLoadSave.saveMdl(model);
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        // Both sides decode identically, so the diff compares content only.
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String prefix(LineDiff.Type type) {
        return switch (type) {
            case ADDED -> "+ ";
            case REMOVED -> "- ";
            case SAME -> "  ";
        };
    }

    /** Renders the diff with per-line colors; long unchanged spans collapse to a marker. */
    private static void populate(StyledDocument doc, List<LineDiff.Line> lines) {
        SimpleAttributeSet added = new SimpleAttributeSet();
        StyleConstants.setBackground(added, new Color(212, 237, 218));
        StyleConstants.setForeground(added, new Color(21, 87, 36));
        SimpleAttributeSet removed = new SimpleAttributeSet();
        StyleConstants.setBackground(removed, new Color(248, 215, 218));
        StyleConstants.setForeground(removed, new Color(114, 28, 36));
        SimpleAttributeSet plain = new SimpleAttributeSet();

        try {
            doc.remove(0, doc.getLength());
        } catch (Exception ignored) {}

        int changedCount = 0;
        for (LineDiff.Line line : lines) {
            if (line.type() != LineDiff.Type.SAME) changedCount++;
        }
        if (changedCount == 0) {
            append(doc, Messages.get("diff.noChange"), plain);
            return;
        }

        int displayed = 0;
        int i = 0;
        int n = lines.size();
        while (i < n && displayed < MAX_DISPLAY_LINES) {
            LineDiff.Line line = lines.get(i);
            if (line.type() == LineDiff.Type.SAME) {
                // find the run of SAME lines
                int runStart = i;
                while (i < n && lines.get(i).type() == LineDiff.Type.SAME) i++;
                int runEnd = i;
                boolean nearChange = (runStart > 0 && lines.get(runStart - 1).type() != LineDiff.Type.SAME)
                        || (runEnd < n && lines.get(runEnd).type() != LineDiff.Type.SAME);
                int keep = nearChange ? CONTEXT_LINES : 1;
                if (runEnd - runStart <= keep * 2 + 2) {
                    for (int j = runStart; j < runEnd && displayed < MAX_DISPLAY_LINES; j++) {
                        append(doc, "  " + lines.get(j).text() + "\n", plain);
                        displayed++;
                    }
                } else {
                    for (int j = runStart; j < runStart + keep && displayed < MAX_DISPLAY_LINES; j++) {
                        append(doc, "  " + lines.get(j).text() + "\n", plain);
                        displayed++;
                    }
                    String marker = fmt("diff.collapsed", runEnd - runStart - keep * 2) + "\n";
                    SimpleAttributeSet markerAttr = new SimpleAttributeSet();
                    StyleConstants.setForeground(markerAttr, new Color(120, 130, 140));
                    StyleConstants.setItalic(markerAttr, true);
                    append(doc, "   " + marker, markerAttr);
                    displayed++;
                    for (int j = runEnd - keep; j < runEnd && displayed < MAX_DISPLAY_LINES; j++) {
                        append(doc, "  " + lines.get(j).text() + "\n", plain);
                        displayed++;
                    }
                }
            } else {
                SimpleAttributeSet attrs = line.type() == LineDiff.Type.ADDED ? added : removed;
                append(doc, prefix(line.type()) + line.text() + "\n", attrs);
                displayed++;
                i++;
            }
        }
        if (displayed >= MAX_DISPLAY_LINES && i < n) {
            SimpleAttributeSet markerAttr = new SimpleAttributeSet();
            StyleConstants.setForeground(markerAttr, new Color(120, 130, 140));
            append(doc, fmt("diff.tooMany", n - displayed) + "\n", markerAttr);
        }
    }

    private static void append(StyledDocument doc, String text, SimpleAttributeSet attrs) {
        try {
            doc.insertString(doc.getLength(), text, attrs);
        } catch (Exception ignored) {}
    }
}
