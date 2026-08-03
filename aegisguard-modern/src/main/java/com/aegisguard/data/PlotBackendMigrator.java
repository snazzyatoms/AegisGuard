package com.aegisguard.data;

import com.aegisguard.AegisGuard;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;

/** One-shot copy utility for the plots.yml and SQLite backends. */
public class PlotBackendMigrator {
    private final AegisGuard plugin;
    public PlotBackendMigrator(AegisGuard plugin) { this.plugin = plugin; }

    public String migrate(String direction) {
        if ("yml-to-sql".equalsIgnoreCase(direction)) return exportYmlToSql();
        if ("sql-to-yml".equalsIgnoreCase(direction)) return exportSqlToYml();
        return "Unknown migration direction. Use yml-to-sql or sql-to-yml.";
    }
    public String exportYmlToSql() { return copy(new YMLDataStore(plugin), new SQLDataStore(plugin), "plots.yml"); }
    public String exportSqlToYml() { return copy(new SQLDataStore(plugin), new YMLDataStore(plugin), "aegisguard.db"); }

    private String copy(IDataStore source, IDataStore target, String backupFile) {
        try {
            backup(backupFile);
            source.load();
            Collection<Plot> sourcePlots = new ArrayList<>(source.getAllPlots());
            for (Plot plot : sourcePlots) if (plot != null) target.addPlot(plot);
            target.saveSync();
            return "Migrated " + sourcePlots.size() + " plot(s). Backup created before migration.";
        } catch (Throwable error) {
            return "Migration failed: " + safe(error.getMessage());
        } finally {
            try { source.shutdown(); } catch (Throwable ignored) {}
            try { target.shutdown(); } catch (Throwable ignored) {}
        }
    }
    private void backup(String filename) throws IOException {
        File source = new File(plugin.getDataFolder(), filename);
        if (!source.exists()) return;
        File directory = new File(plugin.getDataFolder(), "backups");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Could not create backups directory.");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        Files.copy(source.toPath(), new File(directory, filename + "." + stamp + ".bak").toPath(), StandardCopyOption.COPY_ATTRIBUTES);
    }
    private String safe(String message) { return message == null || message.isBlank() ? "unknown error" : message; }
}
