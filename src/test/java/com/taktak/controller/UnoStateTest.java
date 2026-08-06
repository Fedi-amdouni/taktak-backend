package com.taktak.controller;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnoStateTest {

    @Test
    void wildCardPublishesTheChosenColorForTheNextPlayer() {
        UnoState state = new UnoState();
        state.players.put("player-1", new GameWebSocketController.Player("player-1", "Amina"));
        state.players.put("player-2", new GameWebSocketController.Player("player-2", "Sami"));
        state.hands.put("player-1", new ArrayList<>(List.of("WILD:DRAW4", "RED:4")));
        state.hands.put("player-2", new ArrayList<>(List.of("BLUE:7")));
        state.deck.addAll(List.of("YELLOW:1", "YELLOW:2", "YELLOW:3", "YELLOW:4"));
        state.topCard = "RED:8";
        state.turnId = "player-1";
        state.started = true;

        assertTrue(state.play("player-1", "WILD:DRAW4", "BLUE"));

        assertEquals("BLUE", state.activeColor());
    }

    @Test
    void wildCardRequiresAChosenColor() {
        UnoState state = new UnoState();
        state.players.put("player-1", new GameWebSocketController.Player("player-1", "Amina"));
        state.players.put("player-2", new GameWebSocketController.Player("player-2", "Sami"));
        state.hands.put("player-1", new ArrayList<>(List.of("WILD:WILD")));
        state.hands.put("player-2", new ArrayList<>(List.of("BLUE:7")));
        state.topCard = "RED:8";
        state.turnId = "player-1";
        state.started = true;

        assertTrue(!state.play("player-1", "WILD:WILD", null));
        assertEquals("RED:8", state.topCard);
        assertEquals("RED", state.activeColor());
    }
}
