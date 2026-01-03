package com.aegisguard.claimblocks;

import com.aegisguard.AegisGuard;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ClaimBlockExchangeStateStore {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private final AegisGuard plugin;
    private final File file;
    private FileConfiguration data;

    private final Object ioLock = new Object();

    public ClaimBlockExchangeStateStore(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claim-block-exchange.yml");
        load();
    }

    public void load() {
        synchronized (ioLock) {
            if (!file.exists()) {
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            data = YamlConfiguration.loadConfiguration(file);
        }
    }

    public void save() {
        synchronized (ioLock) {
            if (data == null) data = YamlConfiguration.loadConfiguration(file);
            try {
                data.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    // -------------------------
    // Per-player state
    // -------------------------

    private String p(UUID uuid) {
        return "players." + uuid;
    }

    public long getLastTradeEpoch(UUID uuid) {
        synchronized (ioLock) {
            return data.getLong(p(uuid) + ".last_trade_epoch", 0L);
        }
    }

    public void setLastTradeEpoch(UUID uuid, long epochSeconds) {
        synchronized (ioLock) {
            data.set(p(uuid) + ".last_trade_epoch", epochSeconds);
        }
    }

    public String getDayKey() {
        return LocalDate.now().format(DAY_FMT);
    }

    public String getStoredDayKey(UUID uuid) {
        synchronized (ioLock) {
            return data.getString(p(uuid) + ".day_key", "");
        }
    }

    public void setStoredDayKey(UUID uuid, String dayKey) {
        synchronized (ioLock) {
            data.set(p(uuid) + ".day_key", dayKey);
        }
    }

    public long getBoughtToday(UUID uuid) {
        synchronized (ioLock) {
            return data.getLong(p(uuid) + ".bought_today", 0L);
        }
    }

    public long getSoldToday(UUID uuid) {
        synchronized (ioLock) {
            return data.getLong(p(uuid) + ".sold_today", 0L);
        }
    }

    public void setBoughtToday(UUID uuid, long v) {
        synchronized (ioLock) {
            data.set(p(uuid) + ".bought_today", Math.max(0L, v));
        }
    }

    public void setSoldToday(UUID uuid, long v) {
        synchronized (ioLock) {
            data.set(p(uuid) + ".sold_today", Math.max(0L, v));
        }
    }

    public void resetDailyIfNeeded(UUID uuid) {
        String nowKey = getDayKey();
        synchronized (ioLock) {
            String stored = data.getString(p(uuid) + ".day_key", "");
            if (!nowKey.equals(stored)) {
                data.set(p(uuid) + ".day_key", nowKey);
                data.set(p(uuid) + ".bought_today", 0L);
                data.set(p(uuid) + ".sold_today", 0L);
            }
        }
    }

    /**
     * Purchase lots for sell-lock.
     * Stored as list of strings: "epoch:amount"
     */
    public List<Lot> getPurchaseLots(UUID uuid) {
        synchronized (ioLock) {
            List<String> raw = data.getStringList(p(uuid) + ".purchase_lots");
            if (raw == null || raw.isEmpty()) return new ArrayList<>();

            List<Lot> out = new ArrayList<>();
            for (String s : raw) {
                if (s == null || s.isBlank()) continue;
                String[] parts = s.split(":", 2);
                if (parts.length != 2) continue;
                try {
                    long t = Long.parseLong(parts[0]);
                    long a = Long.parseLong(parts[1]);
                    if (t > 0 && a > 0) out.add(new Lot(t, a));
                } catch (Throwable ignored) {}
            }
            out.sort(Comparator.comparingLong(l -> l.epochSeconds));
            return out;
        }
    }

    public void setPurchaseLots(UUID uuid, List<Lot> lots) {
        synchronized (ioLock) {
            List<String> raw = new ArrayList<>();
            if (lots != null) {
                for (Lot l : lots) {
                    if (l != null && l.epochSeconds > 0 && l.amount > 0) {
                        raw.add(l.epochSeconds + ":" + l.amount);
                    }
                }
            }
            data.set(p(uuid) + ".purchase_lots", raw);
        }
    }

    public static final class Lot {
        public long epochSeconds;
        public long amount;

        public Lot(long epochSeconds, long amount) {
            this.epochSeconds = epochSeconds;
            this.amount = amount;
        }
    }
}
