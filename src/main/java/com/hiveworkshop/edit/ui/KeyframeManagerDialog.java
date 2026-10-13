package com.hiveworkshop.edit.ui;

import static com.hiveworkshop.i18n.Messages.fmt;

import com.hiveworkshop.edit.KeyframeEditService;
import com.hiveworkshop.edit.ModelFileService;
import com.hiveworkshop.i18n.Messages;
import com.hiveworkshop.model.ModelAsset;
import com.hiveworkshop.rms.parsers.mdlx.InterpolationType;
import com.hiveworkshop.rms.parsers.mdlx.MdlxAnimatedObject;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.timeline.MdlxTimeline;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
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
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Keyframe manager (P3c): node → transform track → keyframe table. Edits frame
 * times, vector components, removes/inserts frames, switches interpolation
 * between the tangent-free types, and deduplicates LINEAR tracks (only removes
 * middle keys whose three consecutive values are identical). Writes back
 * through the shared safety net; after saving the model can be opened in the
 * viewer to see the animation change.
 */
public final class KeyframeManagerDialog extends JDialog {

    private record NodeEntry(String label, MdlxAnimatedObject object) {
        @Override public String toString() { return label; }
    }

    private final ModelAsset asset;
    private final MdlxModel model;
    private final Runnable onSuccess;
    private final Consumer<ModelAsset> previewOpener;

    private final DefaultListModel<NodeEntry> nodeListModel = new DefaultListModel<>();
    private final JList<NodeEntry> nodeList = new JList<>(nodeListModel);
    private final DefaultListModel<KeyframeEditService.Track> trackListModel = new DefaultListModel<>();
    private final JList<KeyframeEditService.Track> trackList = new JList<>(trackListModel);
    private final JComboBox<InterpolationType> interpCombo = new JComboBox<>(
            new InterpolationType[] { InterpolationType.DONT_INTERP, InterpolationType.LINEAR });
    private final KeyframeTableModel tableModel = new KeyframeTableModel();
    private final JTable keyframeTable = new JTable(tableModel);

    private NodeEntry currentNode;

    public static void open(JFrame owner, ModelAsset asset, Runnable onSuccess, Consumer<ModelAsset> previewOpener) {
        MdlxModel model;
        try {
            model = ModelFileService.loadModel(asset.path());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, fmt("edit.loadFailed", ex.getMessage()),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        new KeyframeManagerDialog(owner, asset, model, onSuccess, previewOpener).setVisible(true);
    }

    private KeyframeManagerDialog(JFrame owner, ModelAsset asset, MdlxModel model,
                                  Runnable onSuccess, Consumer<ModelAsset> previewOpener) {
        super(owner, fmt("kf.title", asset.fileName()), true);
        this.asset = asset;
        this.model = model;
        this.onSuccess = onSuccess;
        this.previewOpener = previewOpener;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(new Dimension(820, 520));
        setMinimumSize(new Dimension(680, 420));
        setLocationRelativeTo(owner);

        // Left: nodes with animation-relevant types
        for (int i = 0; i < model.bones.size(); i++) {
            var bone = model.bones.get(i);
            nodeListModel.addElement(new NodeEntry(
                    Messages.get("bone.group.bones") + "  " + (bone.name == null ? "" : bone.name), bone));
        }
        for (int i = 0; i < model.helpers.size(); i++) {
            var helper = model.helpers.get(i);
            nodeListModel.addElement(new NodeEntry(
                    Messages.get("bone.group.helpers") + "  " + (helper.name == null ? "" : helper.name), helper));
        }
        nodeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        nodeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) selectNode(nodeList.getSelectedValue());
        });

        // Center: tracks of the selected node
        trackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        trackList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) selectTrack(trackList.getSelectedValue());
        });
        JPanel trackPanel = new JPanel(new BorderLayout(0, 4));
        trackPanel.add(new JLabel(Messages.get("kf.tracks")), BorderLayout.NORTH);
        trackPanel.add(new JScrollPane(trackList), BorderLayout.CENTER);
        JPanel interpPanel = new JPanel(new BorderLayout(4, 0));
        interpPanel.add(new JLabel(Messages.get("kf.interp")), BorderLayout.WEST);
        interpPanel.add(interpCombo, BorderLayout.CENTER);
        trackPanel.add(interpPanel, BorderLayout.SOUTH);
        trackPanel.setPreferredSize(new Dimension(240, 0));

        // Right: keyframe table
        keyframeTable.setRowHeight(keyframeTable.getRowHeight() + 4);
        keyframeTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        JPanel tablePanel = new JPanel(new BorderLayout(0, 4));
        tablePanel.add(new JScrollPane(keyframeTable), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(0, 2, 6, 6));
        JButton deleteFrameButton = new JButton(Messages.get("kf.delFrame"));
        deleteFrameButton.addActionListener(e -> deleteSelectedFrame());
        JButton addFrameButton = new JButton(Messages.get("kf.addFrame"));
        addFrameButton.addActionListener(e -> insertFrameAfterSelected());
        JButton dedupeTrackButton = new JButton(Messages.get("kf.dedupeTrack"));
        dedupeTrackButton.addActionListener(e -> dedupe(false));
        JButton dedupeNodeButton = new JButton(Messages.get("kf.dedupeNode"));
        dedupeNodeButton.addActionListener(e -> dedupe(true));
        JButton saveButton = new JButton(Messages.get("seq.save"));
        saveButton.addActionListener(e -> save());
        JButton cancelButton = new JButton(Messages.get("seq.cancel"));
        cancelButton.addActionListener(e -> dispose());
        buttons.add(deleteFrameButton);
        buttons.add(addFrameButton);
        buttons.add(dedupeTrackButton);
        buttons.add(dedupeNodeButton);
        buttons.add(saveButton);
        buttons.add(cancelButton);
        tablePanel.add(buttons, BorderLayout.SOUTH);

        add(new JScrollPane(nodeList), BorderLayout.WEST);
        add(trackPanel, BorderLayout.EAST);
        add(tablePanel, BorderLayout.CENTER);

        interpCombo.addActionListener(e -> applyInterpChange());
        if (!nodeListModel.isEmpty()) nodeList.setSelectedIndex(0);
    }

    private void selectNode(NodeEntry entry) {
        currentNode = entry;
        trackListModel.clear();
        if (entry != null) {
            for (KeyframeEditService.Track track : KeyframeEditService.tracksOf(entry.object())) {
                trackListModel.addElement(track);
            }
        }
        if (!trackListModel.isEmpty()) trackList.setSelectedIndex(0);
        else selectTrack(null);
    }

    private void selectTrack(KeyframeEditService.Track track) {
        tableModel.setTrack(track);
        if (track != null && (track.timeline().interpolationType == InterpolationType.DONT_INTERP
                || track.timeline().interpolationType == InterpolationType.LINEAR)) {
            interpCombo.setSelectedItem(track.timeline().interpolationType);
        }
    }

    private KeyframeEditService.Track selectedTrack() {
        return trackList.getSelectedValue();
    }

    private void applyInterpChange() {
        KeyframeEditService.Track track = selectedTrack();
        if (track == null || interpCombo.getSelectedItem() == null) return;
        track.timeline().interpolationType = (InterpolationType) interpCombo.getSelectedItem();
        trackListModel.set(trackList.getSelectedIndex(), track);
    }

    private void deleteSelectedFrame() {
        KeyframeEditService.Track track = selectedTrack();
        int row = keyframeTable.getSelectedRow();
        if (track == null || row < 0) return;
        KeyframeEditService.removeFrame(track.timeline(), row);
        tableModel.setTrack(track);
        trackList.repaint();
    }

    private void insertFrameAfterSelected() {
        KeyframeEditService.Track track = selectedTrack();
        int row = keyframeTable.getSelectedRow();
        if (track == null || row < 0) return;
        KeyframeEditService.insertFrameAfter(track.timeline(), row);
        tableModel.setTrack(track);
        trackList.repaint();
    }

    private void dedupe(boolean wholeNode) {
        KeyframeEditService.Track track = selectedTrack();
        if (!wholeNode && track == null) return;
        if (wholeNode && currentNode == null) return;
        List<KeyframeEditService.Track> targets = new ArrayList<>();
        if (wholeNode) {
            targets.addAll(KeyframeEditService.tracksOf(currentNode.object()));
        } else {
            targets.add(track);
        }
        int removed = 0;
        for (KeyframeEditService.Track t : targets) {
            removed += KeyframeEditService.dedupeLinear(t.timeline());
        }
        tableModel.setTrack(selectedTrack());
        trackList.repaint();
        JOptionPane.showMessageDialog(this, fmt("kf.dedupeDone", removed),
                Messages.get("kf.title"), JOptionPane.INFORMATION_MESSAGE);
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
        int preview = JOptionPane.showConfirmDialog(this, Messages.get("bone.previewPrompt"),
                Messages.get("kf.title"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        onSuccess.run();
        dispose();
        if (preview == JOptionPane.YES_OPTION) previewOpener.accept(asset);
    }

    /** Columns: frame | X | Y | Z | W (W only for quaternion tracks). */
    private final class KeyframeTableModel extends AbstractTableModel {
        private KeyframeEditService.Track track;

        void setTrack(KeyframeEditService.Track track) {
            this.track = track;
            fireTableStructureChanged();
        }

        @Override public int getRowCount() {
            return track == null ? 0 : track.timeline().frames.length;
        }

        @Override public int getColumnCount() {
            return track != null && track.valueSize() == 4 ? 5 : 4;
        }

        @Override public String getColumnName(int column) {
            return switch (column) {
                case 0 -> Messages.get("kf.col.frame");
                case 1 -> "X";
                case 2 -> "Y";
                case 3 -> "Z";
                default -> "W";
            };
        }

        @Override public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Long.class : Float.class;
        }

        @Override public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) return track.timeline().frames[rowIndex];
            float[] values = (float[]) track.timeline().values[rowIndex];
            return columnIndex - 1 < values.length ? values[columnIndex - 1] : 0f;
        }

        @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            try {
                if (columnIndex == 0) {
                    KeyframeEditService.setFrameTime(track.timeline(), rowIndex, Long.parseLong(aValue.toString().trim()));
                } else {
                    float[] values = KeyframeEditService.getValues(track.timeline(), rowIndex);
                    values[columnIndex - 1] = Float.parseFloat(aValue.toString().trim());
                    KeyframeEditService.setValues(track.timeline(), rowIndex, values);
                }
                fireTableCellUpdated(rowIndex, columnIndex);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(KeyframeManagerDialog.this,
                        fmt("mat.invalidEdit", String.valueOf(aValue)),
                        Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
                fireTableCellUpdated(rowIndex, columnIndex);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(KeyframeManagerDialog.this,
                        ex.getMessage(), Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }
}
