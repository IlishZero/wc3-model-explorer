package com.hiveworkshop.edit.ui;

import static com.hiveworkshop.i18n.Messages.fmt;

import com.hiveworkshop.edit.BoneNodeService;
import com.hiveworkshop.edit.ModelFileService;
import com.hiveworkshop.i18n.Messages;
import com.hiveworkshop.model.ModelAsset;
import com.hiveworkshop.rms.parsers.mdlx.MdlxAttachment;
import com.hiveworkshop.rms.parsers.mdlx.MdlxGenericObject;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bone/node manager (P3b): tree of bones/helpers/attachments with per-node
 * name, parent (cycle-checked re-parenting), pivot coordinates, billboard
 * flags and attachment add/remove. Bone deletion is deliberately not offered.
 * Writes back through the safety net; after saving the model can be opened in
 * the viewer.
 */
public final class BoneNodeManagerDialog extends JDialog {

    private final ModelAsset asset;
    private final MdlxModel model;
    private final Runnable onSuccess;
    private final Consumer<ModelAsset> previewOpener;

    private final JTree nodeTree = new JTree(new DefaultMutableTreeNode());
    private final JTextField nameField = new JTextField(18);
    private final JTextField pathField = new JTextField(18);
    private final JTextField pivotXField = new JTextField(6);
    private final JTextField pivotYField = new JTextField(6);
    private final JTextField pivotZField = new JTextField(6);
    private final JComboBox<ParentOption> parentCombo = new JComboBox<>();
    private final JCheckBox billboardBox = new JCheckBox(Messages.get("bone.flag.billboarded"));
    private final JCheckBox lockXBox = new JCheckBox("LockX");
    private final JCheckBox lockYBox = new JCheckBox("LockY");
    private final JCheckBox lockZBox = new JCheckBox("LockZ");
    private final JTextArea infoArea = new JTextArea(2, 20);
    private final JButton deleteAttachmentButton = new JButton(Messages.get("bone.delAttachment"));

    private BoneNodeService.NodeRef selectedNode;
    private int selectedAttachmentIndex = -1;

    /** Parent combo entry: -1 = root, otherwise another node (never the node itself). */
    private record ParentOption(int objectId, String label) {
        @Override public String toString() { return label; }
    }

    public static void open(JFrame owner, ModelAsset asset, Runnable onSuccess, Consumer<ModelAsset> previewOpener) {
        MdlxModel model;
        try {
            model = ModelFileService.loadModel(asset.path());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, fmt("edit.loadFailed", ex.getMessage()),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        new BoneNodeManagerDialog(owner, asset, model, onSuccess, previewOpener).setVisible(true);
    }

    private BoneNodeManagerDialog(JFrame owner, ModelAsset asset, MdlxModel model,
                                  Runnable onSuccess, Consumer<ModelAsset> previewOpener) {
        super(owner, fmt("bone.title", asset.fileName()), true);
        this.asset = asset;
        this.model = model;
        this.onSuccess = onSuccess;
        this.previewOpener = previewOpener;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(new Dimension(760, 500));
        setMinimumSize(new Dimension(640, 400));
        setLocationRelativeTo(owner);

        // Left: node tree
        rebuildTree();
        nodeTree.addTreeSelectionListener(e -> onTreeSelection());
        JPanel treePanel = new JPanel(new BorderLayout(0, 4));
        JLabel treeHeader = new JLabel(Messages.get("bone.nodes"));
        treeHeader.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 0));
        treePanel.add(treeHeader, BorderLayout.NORTH);
        treePanel.add(new JScrollPane(nodeTree), BorderLayout.CENTER);
        treePanel.setPreferredSize(new Dimension(280, 0));

        // Right: editor for the selected node
        JPanel editor = new JPanel(new GridLayout(0, 2, 6, 6));
        editor.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        editor.add(new JLabel(Messages.get("bone.objectId")));
        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        editor.add(new JScrollPane(infoArea));
        editor.add(new JLabel(Messages.get("bone.name")));
        editor.add(nameField);
        editor.add(new JLabel(Messages.get("bone.parent")));
        editor.add(parentCombo);
        editor.add(new JLabel(Messages.get("bone.pivot")));
        JPanel pivotRow = new JPanel(new GridLayout(1, 3, 4, 0));
        pivotRow.add(pivotXField);
        pivotRow.add(pivotYField);
        pivotRow.add(pivotZField);
        editor.add(pivotRow);
        editor.add(new JLabel(Messages.get("bone.path")));
        editor.add(pathField);
        editor.add(new JLabel(Messages.get("bone.flags")));
        JPanel flagRow = new JPanel(new GridLayout(1, 4, 4, 0));
        for (JCheckBox box : new JCheckBox[] { billboardBox, lockXBox, lockYBox, lockZBox }) {
            flagRow.add(box);
        }
        editor.add(flagRow);

        JPanel buttons = new JPanel(new GridLayout(0, 2, 6, 6));
        JButton applyButton = new JButton(Messages.get("bone.apply"));
        applyButton.addActionListener(e -> applyEdits());
        JButton addAttachmentButton = new JButton(Messages.get("bone.addAttachment"));
        addAttachmentButton.addActionListener(e -> addAttachment());
        deleteAttachmentButton.addActionListener(e -> deleteSelectedAttachment());
        JButton saveButton = new JButton(Messages.get("seq.save"));
        saveButton.addActionListener(e -> save());
        JButton cancelButton = new JButton(Messages.get("seq.cancel"));
        cancelButton.addActionListener(e -> dispose());
        buttons.add(applyButton);
        buttons.add(addAttachmentButton);
        buttons.add(deleteAttachmentButton);
        buttons.add(saveButton);
        buttons.add(cancelButton);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(editor, BorderLayout.NORTH);
        rightPanel.add(buttons, BorderLayout.SOUTH);

        add(treePanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);
        setEditorEnabled(false);
    }

    private void rebuildTree() {
        List<BoneNodeService.NodeRef> nodes = BoneNodeService.collectNodes(model);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        var bones = new DefaultMutableTreeNode(fmt("bone.group.count", Messages.get("bone.group.bones"), countKind(nodes, "Bone")));
        var helpers = new DefaultMutableTreeNode(fmt("bone.group.count", Messages.get("bone.group.helpers"), countKind(nodes, "Helper")));
        var attachments = new DefaultMutableTreeNode(fmt("bone.group.count", Messages.get("bone.group.attachments"), countKind(nodes, "Attachment")));
        for (BoneNodeService.NodeRef node : nodes) {
            var child = new DefaultMutableTreeNode(node);
            switch (node.kind()) {
                case "Bone" -> bones.add(child);
                case "Helper" -> helpers.add(child);
                default -> attachments.add(child);
            }
        }
        root.add(bones);
        root.add(helpers);
        root.add(attachments);
        nodeTree.setModel(new DefaultTreeModel(root));
        for (int i = 0; i < nodeTree.getRowCount(); i++) {
            nodeTree.expandRow(i);
        }
    }

    private static int countKind(List<BoneNodeService.NodeRef> nodes, String kind) {
        return (int) nodes.stream().filter(n -> n.kind().equals(kind)).count();
    }

    private void onTreeSelection() {
        TreePath path = nodeTree.getSelectionPath();
        if (path == null || !(nodeTree.getLastSelectedPathComponent() instanceof
                DefaultMutableTreeNode selected && selected.getUserObject() instanceof
                BoneNodeService.NodeRef node)) {
            selectedNode = null;
            selectedAttachmentIndex = -1;
            setEditorEnabled(false);
            return;
        }
        selectedNode = node;
        selectedAttachmentIndex = node.kind().equals("Attachment") ? node.index() : -1;
        nameField.setText(node.name() == null ? "" : node.name());
        parentCombo.removeAllItems();
        parentCombo.addItem(new ParentOption(-1, Messages.get("bone.parentNone")));
        for (BoneNodeService.NodeRef other : BoneNodeService.collectNodes(model)) {
            if (other.objectId() != node.objectId()) {
                parentCombo.addItem(new ParentOption(other.objectId(),
                        other.objectId() + "  " + (other.name() == null ? "" : other.name())));
            }
        }
        for (int i = 0; i < parentCombo.getItemCount(); i++) {
            if (parentCombo.getItemAt(i).objectId() == node.parentId()) {
                parentCombo.setSelectedIndex(i);
                break;
            }
        }
        float[] pivot = BoneNodeService.pivotOf(model, node.objectId());
        pivotXField.setText(String.valueOf(pivot[0]));
        pivotYField.setText(String.valueOf(pivot[1]));
        pivotZField.setText(String.valueOf(pivot[2]));
        pathField.setText(node.kind().equals("Attachment")
                && model.attachments.get(node.index()) instanceof MdlxAttachment att ? att.path : "");
        pathField.setEnabled(node.kind().equals("Attachment"));
        int flags = node.object().flags;
        billboardBox.setSelected((flags & BoneNodeService.FLAG_BILLBOARDED) != 0);
        lockXBox.setSelected((flags & BoneNodeService.FLAG_BILLBOARDED_LOCK_X) != 0);
        lockYBox.setSelected((flags & BoneNodeService.FLAG_BILLBOARDED_LOCK_Y) != 0);
        lockZBox.setSelected((flags & BoneNodeService.FLAG_BILLBOARDED_LOCK_Z) != 0);
        infoArea.setText(fmt("bone.info", node.kind(), node.objectId(), node.parentId()));
        setEditorEnabled(true);
        deleteAttachmentButton.setEnabled(selectedAttachmentIndex >= 0);
    }

    private void setEditorEnabled(boolean enabled) {
        nameField.setEnabled(enabled);
        parentCombo.setEnabled(enabled);
        pivotXField.setEnabled(enabled);
        pivotYField.setEnabled(enabled);
        pivotZField.setEnabled(enabled);
        pathField.setEnabled(enabled && selectedNode != null && selectedNode.kind().equals("Attachment"));
        for (JCheckBox box : new JCheckBox[] { billboardBox, lockXBox, lockYBox, lockZBox }) {
            box.setEnabled(enabled);
        }
        deleteAttachmentButton.setEnabled(enabled && selectedAttachmentIndex >= 0);
    }

    private void applyEdits() {
        if (selectedNode == null) return;
        try {
            // re-resolve: the tree may be stale after add/remove
            BoneNodeService.NodeRef node = BoneNodeService.findByObjectId(model, selectedNode.objectId());
            if (node == null) return;
            String newName = nameField.getText().trim();
            if (!newName.isEmpty()) node.object().name = newName;

            ParentOption parent = (ParentOption) parentCombo.getSelectedItem();
            if (parent != null) BoneNodeService.setParent(model, node, parent.objectId());

            float x = Float.parseFloat(pivotXField.getText().trim());
            float y = Float.parseFloat(pivotYField.getText().trim());
            float z = Float.parseFloat(pivotZField.getText().trim());
            BoneNodeService.setPivot(model, node.objectId(), x, y, z);

            int flags = node.object().flags;
            flags = billboardBox.isSelected() ? flags | BoneNodeService.FLAG_BILLBOARDED
                    : flags & ~BoneNodeService.FLAG_BILLBOARDED;
            flags = lockXBox.isSelected() ? flags | BoneNodeService.FLAG_BILLBOARDED_LOCK_X
                    : flags & ~BoneNodeService.FLAG_BILLBOARDED_LOCK_X;
            flags = lockYBox.isSelected() ? flags | BoneNodeService.FLAG_BILLBOARDED_LOCK_Y
                    : flags & ~BoneNodeService.FLAG_BILLBOARDED_LOCK_Y;
            flags = lockZBox.isSelected() ? flags | BoneNodeService.FLAG_BILLBOARDED_LOCK_Z
                    : flags & ~BoneNodeService.FLAG_BILLBOARDED_LOCK_Z;
            if (node.kind().equals("Bone")) {
                flags |= BoneNodeService.BONE_BASE_BITS; // never strip RMS's bone type bit
            }
            node.object().flags = flags;

            if (node.kind().equals("Attachment")
                    && node.object() instanceof MdlxAttachment attachment) {
                attachment.path = pathField.getText().trim();
            }
            rebuildTree();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, fmt("mat.invalidEdit", pivotXField.getText()),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addAttachment() {
        String name = JOptionPane.showInputDialog(this, Messages.get("bone.addNamePrompt"),
                Messages.get("bone.addAttachment"), JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        BoneNodeService.addAttachment(model, name.trim().replaceAll("[\"\\\\]", ""), -1);
        rebuildTree();
    }

    private void deleteSelectedAttachment() {
        if (selectedAttachmentIndex < 0) return;
        int choice = JOptionPane.showConfirmDialog(this,
                fmt("bone.delConfirm", selectedNode == null ? "" : selectedNode.name()),
                Messages.get("bone.delAttachment"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        BoneNodeService.removeAttachment(model, selectedAttachmentIndex);
        selectedNode = null;
        selectedAttachmentIndex = -1;
        setEditorEnabled(false);
        rebuildTree();
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
                Messages.get("bone.title"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        onSuccess.run();
        dispose();
        if (preview == JOptionPane.YES_OPTION) previewOpener.accept(asset);
    }
}
