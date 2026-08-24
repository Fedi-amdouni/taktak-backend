package com.taktak.game.controller;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameWebSocketSessionListenerTest {

    @Test
    void keepsPlayerDuringTransientDisconnectGracePeriod() {
        GameWebSocketController controller = mock(GameWebSocketController.class);
        GameWebSocketSessionListener listener = new GameWebSocketSessionListener(controller);

        listener.handleConnect(connectEvent("session-1", "player-1"));

        SessionDisconnectEvent disconnectEvent = mock(SessionDisconnectEvent.class);
        when(disconnectEvent.getSessionId()).thenReturn("session-1");
        listener.handleDisconnect(disconnectEvent);

        verify(controller, never()).removePlayer("player-1");
    }

    @Test
    void removesPlayerWhenGracePeriodReallyExpires() {
        GameWebSocketController controller = mock(GameWebSocketController.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.schedule(any(Runnable.class), eq(45L), eq(TimeUnit.SECONDS)))
                .thenReturn(mock(ScheduledFuture.class));
        GameWebSocketSessionListener listener = new GameWebSocketSessionListener(controller, 45, scheduler);

        listener.handleConnect(connectEvent("session-1", "player-1"));
        SessionDisconnectEvent disconnectEvent = disconnectEvent("session-1");
        listener.handleDisconnect(disconnectEvent);

        var cleanupTask = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(cleanupTask.capture(), eq(45L), eq(TimeUnit.SECONDS));
        cleanupTask.getValue().run();

        verify(controller).removePlayer("player-1");
    }

    @Test
    void reconnectCancelsPendingRemoval() {
        GameWebSocketController controller = mock(GameWebSocketController.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.schedule(any(Runnable.class), eq(45L), eq(TimeUnit.SECONDS)))
                .thenReturn(mock(ScheduledFuture.class));
        GameWebSocketSessionListener listener = new GameWebSocketSessionListener(controller, 45, scheduler);

        listener.handleConnect(connectEvent("session-1", "player-1"));
        listener.handleDisconnect(disconnectEvent("session-1"));
        var cleanupTask = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(cleanupTask.capture(), eq(45L), eq(TimeUnit.SECONDS));

        listener.handleConnect(connectEvent("session-2", "player-1"));
        cleanupTask.getValue().run();

        verify(controller, never()).removePlayer("player-1");
    }

    private SessionConnectEvent connectEvent(String sessionId, String playerId) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setSessionId(sessionId);
        headers.setNativeHeader(GameWebSocketSessionListener.PLAYER_ID_HEADER, playerId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        return new SessionConnectEvent(this, message);
    }

    private SessionDisconnectEvent disconnectEvent(String sessionId) {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getSessionId()).thenReturn(sessionId);
        return event;
    }
}
