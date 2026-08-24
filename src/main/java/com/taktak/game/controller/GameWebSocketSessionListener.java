package com.taktak.game.controller;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Releases game lobbies when a browser closes without sending an explicit leave message. */
@Component
public class GameWebSocketSessionListener {
    static final String PLAYER_ID_HEADER = "x-game-player-id";
    private static final long DEFAULT_DISCONNECT_GRACE_SECONDS = 45;

    private final GameWebSocketController gameWebSocketController;
    private final long disconnectGraceSeconds;
    private final ScheduledExecutorService cleanupScheduler;
    private final ConcurrentHashMap<String, String> playerBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> sessionsByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> pendingRemovalGeneration = new ConcurrentHashMap<>();
    private final AtomicLong removalSequence = new AtomicLong();

    @Autowired
    public GameWebSocketSessionListener(
            GameWebSocketController gameWebSocketController,
            @Value("${taktak.games.disconnect-grace-seconds:45}") long disconnectGraceSeconds) {
        this(gameWebSocketController, disconnectGraceSeconds, newCleanupScheduler());
    }

    GameWebSocketSessionListener(GameWebSocketController gameWebSocketController) {
        this(gameWebSocketController, DEFAULT_DISCONNECT_GRACE_SECONDS, newCleanupScheduler());
    }

    GameWebSocketSessionListener(
            GameWebSocketController gameWebSocketController,
            long disconnectGraceSeconds,
            ScheduledExecutorService cleanupScheduler) {
        this.gameWebSocketController = gameWebSocketController;
        this.disconnectGraceSeconds = Math.max(0, disconnectGraceSeconds);
        this.cleanupScheduler = cleanupScheduler;
    }

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        String playerId = cleanPlayerId(headers.getFirstNativeHeader(PLAYER_ID_HEADER));
        if (sessionId == null || playerId == null) return;

        pendingRemovalGeneration.remove(playerId);
        playerBySession.put(sessionId, playerId);
        sessionsByPlayer.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String playerId = playerBySession.remove(event.getSessionId());
        if (playerId == null) return;

        Set<String> sessions = sessionsByPlayer.get(playerId);
        if (sessions == null) return;
        sessions.remove(event.getSessionId());
        if (sessions.isEmpty() && sessionsByPlayer.remove(playerId, sessions)) {
            scheduleRemoval(playerId);
        }
    }

    private void scheduleRemoval(String playerId) {
        long generation = removalSequence.incrementAndGet();
        pendingRemovalGeneration.put(playerId, generation);
        cleanupScheduler.schedule(() -> {
            boolean stillPending = pendingRemovalGeneration.remove(playerId, generation);
            if (stillPending && !sessionsByPlayer.containsKey(playerId)) {
                gameWebSocketController.removePlayer(playerId);
            }
        }, disconnectGraceSeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdownScheduler() {
        cleanupScheduler.shutdownNow();
    }

    private static ScheduledExecutorService newCleanupScheduler() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "taktak-game-disconnect-cleanup");
            thread.setDaemon(true);
            return thread;
        });
    }

    private String cleanPlayerId(String playerId) {
        if (playerId == null) return null;
        String cleaned = playerId.trim();
        return cleaned.isEmpty() ? null : cleaned.substring(0, Math.min(cleaned.length(), 128));
    }
}
