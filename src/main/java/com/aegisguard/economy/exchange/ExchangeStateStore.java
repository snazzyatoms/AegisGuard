package com.aegisguard.economy.exchange;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ExchangeStateStore {

    public static final class State {
        long lastTradeMillis;
        long dayKey;          // epochDay
        long boughtToday;
        long soldToday;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final ZoneId zoneId;

    public ExchangeStateStore() {
        this(ZoneId.systemDefault());
    }

    public ExchangeStateStore(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    public State get(UUID uuid) {
        State state = states.computeIfAbsent(uuid, u -> new State());
        long today = LocalDate.now(zoneId).toEpochDay();
        if (state.dayKey != today) {
            state.dayKey = today;
            state.boughtToday = 0;
            state.soldToday = 0;
        }
        return state;
    }
}
