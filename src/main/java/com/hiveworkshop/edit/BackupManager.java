package com.hiveworkshop.edit;

import com.hiveworkshop.parser.ReterasModelParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Safety net for every operation that rewrites a model file: before writing,
 * a timestamped copy of the original lands in a {@code backup/} folder next to
 * it. Backups use a {@code .bak} suffix so the model scanner never picks them
 * up as library entries.
 */
public final class BackupManager {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    /** Name of the per-directory folder holding backups. */
    public static final String BACKUP_DIR = "backup";

    private BackupManager() {}

    /** Copies {@code file} to {@code <parent>/backup/<name>.<timestamp>.bak} and returns the copy's path. */
    public static Path backup(Path file) throws IOException {
        Path dir = backupDirFor(file);
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(STAMP);
        Path target = dir.resolve(file.getFileName().toString() + "." + stamp + ".bak");
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /** All backups of {@code file}, newest first. */
    public static List<Path> backupsFor(Path file) throws IOException {
        Path dir = backupDirFor(file);
        if (!Files.isDirectory(dir)) return List.of();
        String prefix = file.getFileName().toString() + ".";
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(".bak");
                    })
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString().toLowerCase(Locale.ROOT)).reversed())
                    .toList();
        }
    }

    /** Copies {@code backup} over {@code original} and invalidates the parser cache for it. */
    public static void restore(Path backup, Path original) throws IOException {
        Files.copy(backup, original, StandardCopyOption.REPLACE_EXISTING);
        ReterasModelParser.invalidate(original);
    }

    public static Path backupDirFor(Path file) {
        return file.toAbsolutePath().getParent().resolve(BACKUP_DIR);
    }
}
