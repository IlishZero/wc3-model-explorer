package com.hiveworkshop.edit.ui;

import static com.hiveworkshop.i18n.Messages.fmt;

import com.hiveworkshop.edit.ModelFileService;
import com.hiveworkshop.edit.ModelSlimmingService;
import com.hiveworkshop.i18n.Messages;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.nio.file.Path;

/**
 * Slimming wizard (P2-15): pick what to strip — unreferenced texture entries,
 * cameras, event objects, collision shapes — preview the current counts, and
 * write back through the shared safety net with a before/after report.
 */
public final class ModelSlimmingDialog extends JDialog {

    private final Path modelFile;
    private final Runnable onSuccess;
    private final JCheckBox texturesBox = new JCheckBox(Messages.get("slim.opt.textures"), true);
    private final JCheckBox camerasBox = new JCheckBox(Messages.get("slim.opt.cameras"));
    private final JCheckBox eventsBox = new JCheckBox(Messages.get("slim.opt.events"));
    private final JCheckBox collisionBox = new JCheckBox(Messages.get("slim.opt.collision"));
    private final JLabel beforeLabel = new JLabel();

    public static void open(JFrame owner, Path file, Runnable onSuccess) {
        MdlxModel model;
        try {
            model = ModelFileService.loadModel(file);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, fmt("edit.loadFailed", ex.getMessage()),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        new ModelSlimmingDialog(owner, file, model, onSuccess).setVisible(true);
    }

    private ModelSlimmingDialog(JFrame owner, Path file, MdlxModel model, Runnable onSuccess) {
        super(owner, fmt("slim.title", file.getFileName().toString()), true);
        this.modelFile = file;
        this.onSuccess = onSuccess;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(new Dimension(520, 380));
        setMinimumSize(new Dimension(440, 320));
        setLocationRelativeTo(owner);

        long sizeKb = modelFile.toFile().length() / 1024;
        beforeLabel.setText(fmt("slim.before", model.textures.size(), model.cameras.size(),
                model.eventObjects.size(), model.collisionShapes.size(), sizeKb));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        beforeLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(beforeLabel);
        content.add(javax.swing.Box.createVerticalStrut(10));

        JLabel optionsHeader = new JLabel(Messages.get("slim.options"));
        optionsHeader.setAlignmentX(LEFT_ALIGNMENT);
        content.add(optionsHeader);
        for (JCheckBox box : new JCheckBox[] { texturesBox, camerasBox, eventsBox, collisionBox }) {
            box.setAlignmentX(LEFT_ALIGNMENT);
            content.add(box);
        }
        content.add(javax.swing.Box.createVerticalStrut(12));

        JPanel buttons = new JPanel();
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        JButton runButton = new JButton(Messages.get("slim.run"));
        runButton.addActionListener(e -> run());
        JButton cancelButton = new JButton(Messages.get("seq.cancel"));
        cancelButton.addActionListener(e -> dispose());
        buttons.add(runButton);
        buttons.add(cancelButton);
        content.add(buttons);

        add(content, BorderLayout.CENTER);
    }

    private void run() {
        ModelSlimmingService.Options options = new ModelSlimmingService.Options(
                texturesBox.isSelected(), camerasBox.isSelected(),
                eventsBox.isSelected(), collisionBox.isSelected());
        if (!texturesBox.isSelected() && !camerasBox.isSelected()
                && !eventsBox.isSelected() && !collisionBox.isSelected()) {
            JOptionPane.showMessageDialog(this, Messages.get("slim.nothing"),
                    Messages.get("slim.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<ModelSlimmingService.Plan, Void>() {
            @Override protected ModelSlimmingService.Plan doInBackground() throws Exception {
                return ModelSlimmingService.plan(modelFile, options);
            }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    ModelSlimmingService.Plan plan = get();
                    if (plan == null) {
                        JOptionPane.showMessageDialog(ModelSlimmingDialog.this,
                                Messages.get("slim.nothing"), Messages.get("slim.title"),
                                JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    // Human gate: review the textual diff before anything is written.
                    if (!WritePreview.confirm(ModelSlimmingDialog.this, modelFile, plan.model())) return;
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    new SwingWorker<ModelFileService.WriteResult, Void>() {
                        @Override protected ModelFileService.WriteResult doInBackground() {
                            return ModelFileService.writeModel(modelFile, plan.model());
                        }
                        @Override protected void done() {
                            setCursor(Cursor.getDefaultCursor());
                            try {
                                showWriteResult(get(), plan);
                            } catch (Exception ex) {
                                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                JOptionPane.showMessageDialog(ModelSlimmingDialog.this,
                                        fmt("slim.failed", String.valueOf(cause)),
                                        Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }.execute();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(ModelSlimmingDialog.this,
                            fmt("slim.failed", String.valueOf(cause)),
                            Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showWriteResult(ModelFileService.WriteResult write, ModelSlimmingService.Plan plan) {
        if (!write.success()) {
            JOptionPane.showMessageDialog(this, fmt("slim.failed", write.message()),
                    Messages.get("main.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        long sizeAfterBytes = modelFile.toFile().length();
        String report = fmt("slim.report",
                modelFile.getFileName(),
                plan.texturesBefore(), plan.texturesAfter(), plan.removedTextures(),
                plan.camerasRemoved(), plan.eventObjectsRemoved(), plan.collisionShapesRemoved(),
                plan.sizeBeforeBytes() / 1024, sizeAfterBytes / 1024,
                write.backupPath().getFileName());
        JOptionPane.showMessageDialog(this, report, Messages.get("slim.title"),
                JOptionPane.INFORMATION_MESSAGE);
        onSuccess.run();
        dispose();
    }
}
