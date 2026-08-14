package com.taktak.game.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Releases game lobbies when a browser closes without sending an explicit leave message. */
@Component
@RequiredArgsConstructor
public class GameWebSocketSessionListener {
    static final String PLAYER_ID_HEADER = "x-game-player-id";

    private final GameWebSocketController gameWebSocketController;
    private final ConcurrentHashMap<String, String> playerBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> sessionsByPlayer = new ConcurrentHashMap<>();

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        String playerId = cleanPlayerId(headers.getFirstNativeHeader(PLAYER_ID_HEADER));
        if (sessionId == null || playerId == null) return;

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
            gameWebSocketController.removePlayer(playerId);
        }
    }

    private String cleanPlayerId(String playerId) {
        if (playerId == null) return null;
        String cleaned = playerId.trim();
        return cleaned.isEmpty() ? null : cleaned.substring(0, Math.min(cleaned.length(), 128));
    }
}
