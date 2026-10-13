package com.hiveworkshop.edit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void backupCopiesFileIntoBackupFolderWithBakSuffix() throws Exception {
        Path file = tempDir.resolve("model.mdx");
        Files.write(file, new byte[] { 1, 2, 3, 4 });

        Path backup = BackupManager.backup(file);

        assertTrue(backup.toString().contains("backup"), backup.toString());
        assertTrue(backup.getFileName().toString().endsWith(".bak"));
        assertArrayEquals(new byte[] { 1, 2, 3, 4 }, Files.readAllBytes(backup));
        assertEquals(List.of(backup), BackupManager.backupsFor(file));
    }

    @Test
    void restorePutsBackupContentBackOverTheOriginal() throws Exception {
        Path file = tempDir.resolve("model.mdx");
        Files.write(file, new byte[] { 1 });
        Path backup = BackupManager.backup(file);
        Files.write(file, new byte[] { 9, 9, 9 });

        BackupManager.restore(backup, file);

        assertArrayEquals(new byte[] { 1 }, Files.readAllBytes(file));
    }

    @Test
    void backupsForReturnsEmptyWhenNothingWasBackedUp() throws Exception {
        assertTrue(BackupManager.backupsFor(tempDir.resolve("missing.mdx")).isEmpty());
    }
}
