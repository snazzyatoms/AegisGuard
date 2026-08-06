package com.aegisguard.arena;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single-owner persistence queue: copy-on-write, tmp → validate → atomic replace, rolling backups.
 */
public final class ArenaPersistenceQueue implements AutoCloseable {

    private final Logger logger;
    private final File backupDir;
    private final int backupCount;
    private final ExecutorService executor;
    private final Object lock = new Object();

    public ArenaPersistenceQueue(Logger logger, File dataFolder, int backupCount) {
        this.logger = logger == null ? Logger.getLogger("ArenaPersistence") : logger;
        this.backupDir = new File(dataFolder, "arena-backups");
        this.backupCount = Math.max(1, backupCount);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "aegisguard-arena-io");
            t.setDaemon(true);
            return t;
        });
        //noinspection ResultOfMethodCallIgnored
        this.backupDir.mkdirs();
    }

    public void enqueue(Runnable task) {
        if (task == null) return;
        executor.execute(() -> {
            synchronized (lock) {
                try {
                    task.run();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Arena persistence task failed", t);
                }
            }
        });
    }

    public void saveYamlAtomic(File target, YamlConfiguration config, Consumer<YamlConfiguration> validator) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(config, "config");
        File parent = target.getParentFile();
        if (parent != null) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();

        File tmp = new File(target.getAbsolutePath() + ".tmp");
        config.save(tmp);
        YamlConfiguration check = YamlConfiguration.loadConfiguration(tmp);
        if (validator != null) validator.accept(check);

        rotateBackup(target);
        Path tmpPath = tmp.toPath();
        Path targetPath = target.toPath();
        try {
            Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void rotateBackup(File target) {
        if (target == null || !target.exists()) return;
        try {
            String base = target.getName();
            File dest = new File(backupDir, base + "." + System.currentTimeMillis() + ".bak");
            Files.copy(target.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            File[] bak = backupDir.listFiles((dir, name) -> name.startsWith(base + "."));
            if (bak == null || bak.length <= backupCount) return;
            Deque<File> oldest = new ArrayDeque<>();
            java.util.Arrays.sort(bak, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (int i = 0; i < bak.length - backupCount; i++) {
                //noinspection ResultOfMethodCallIgnored
                bak[i].delete();
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Arena backup rotate failed for " + target, e);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
