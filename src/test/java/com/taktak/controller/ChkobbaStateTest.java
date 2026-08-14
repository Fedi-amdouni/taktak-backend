package com.taktak.controller;

import com.taktak.game.state.ChkobbaState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChkobbaStateTest {
    @Test
    void startsWithFortyCardsAndDealsThreeToEachPlayer() {
        ChkobbaState state = new ChkobbaState();
        state.join("p1", "Ali");
        state.join("p2", "Sarra");

        state.start();

        assertTrue(state.started);
        assertEquals(2, state.players.size());
        assertEquals(4, state.table.size());
        assertEquals(3, state.hands.get("p1").size());
        assertEquals(3, state.hands.get("p2").size());
        assertEquals(40, state.table.size() + state.deckRemaining + state.hands.values().stream().mapToInt(List::size).sum());
        assertEquals(1, state.round);
        assertEquals(11, ChkobbaState.TARGET_SCORE);
    }

    @Test
    void exactValueCaptureIsMandatoryOverACombination() {
        ChkobbaState state = preparedState();
        ChkobbaState.Card played = new ChkobbaState.Card("BASTONI-5", "BASTONI", "5", 5);
        ChkobbaState.Card exact = new ChkobbaState.Card("KOPPA-5", "KOPPA", "5", 5);
        ChkobbaState.Card two = new ChkobbaState.Card("DINARI-2", "DINARI", "2", 2);
        ChkobbaState.Card three = new ChkobbaState.Card("SABRES-3", "SABRES", "3", 3);
        state.hands.put("p1", new ArrayList<>(List.of(played, new ChkobbaState.Card("BASTONI-A", "BASTONI", "A", 1))));
        state.table.addAll(List.of(exact, two, three));

        assertFalse(state.play("p1", played.getId(), List.of(two.getId(), three.getId())));
        assertTrue(state.play("p1", played.getId(), List.of(exact.getId())));
        assertEquals(1, state.lastCaptureCount);
        assertEquals(2, state.table.size());
        assertEquals(2, state.captured.get("p1").size());
    }

    @Test
    void capturesCardsWhoseValuesSumToThePlayedCard() {
        ChkobbaState state = preparedState();
        ChkobbaState.Card played = new ChkobbaState.Card("BASTONI-5", "BASTONI", "5", 5);
        ChkobbaState.Card two = new ChkobbaState.Card("DINARI-2", "DINARI", "2", 2);
        ChkobbaState.Card three = new ChkobbaState.Card("SABRES-3", "SABRES", "3", 3);
        state.hands.put("p1", new ArrayList<>(List.of(played, new ChkobbaState.Card("BASTONI-A", "BASTONI", "A", 1))));
        state.table.addAll(List.of(two, three));

        assertTrue(state.play("p1", played.getId(), List.of(two.getId(), three.getId())));
        assertEquals(2, state.lastCaptureCount);
        assertTrue(state.table.isEmpty());
        assertEquals(3, state.captured.get("p1").size());
    }

    @Test
    void supportsA21PointSoloMatchAgainstTheBot() {
        ChkobbaState state = new ChkobbaState();
        state.join("p1", "Ali");

        state.start(21, true);

        assertTrue(state.started);
        assertTrue(state.botEnabled);
        assertEquals(21, state.targetScore);
        assertTrue(state.players.containsKey(ChkobbaState.BOT_ID));
        assertEquals(2, state.players.size());
    }

    @Test
    void startsAMatchWithTargetScoreAndBot() {
        ChkobbaState state = new ChkobbaState();
        state.join("p1", "Ali");

        state.start(11, true);

        assertTrue(state.started);
        assertEquals(11, state.targetScore);
        assertTrue(state.players.containsKey(ChkobbaState.BOT_ID));
        assertEquals(2, state.players.size());
    }

    @Test
    void botCanPlayItsTurnAndPassControlBackToTheHuman() {
        ChkobbaState state = new ChkobbaState();
        state.join("p1", "Ali");
        state.start(11, true);
        state.turnId = ChkobbaState.BOT_ID;

        assertTrue(state.playBotTurn());
        assertEquals("p1", state.turnId);
    }

    private ChkobbaState preparedState() {
        ChkobbaState state = new ChkobbaState();
        state.join("p1", "Ali");
        state.join("p2", "Sarra");
        state.started = true;
        state.turnId = "p1";
        state.hands.put("p1", new ArrayList<>());
        state.hands.put("p2", new ArrayList<>(List.of(new ChkobbaState.Card("KOPPA-A", "KOPPA", "A", 1))));
        state.captured.put("p1", new ArrayList<>());
        state.captured.put("p2", new ArrayList<>());
        state.scopaCounts.put("p1", 0);
        state.scopaCounts.put("p2", 0);
        return state;
    }
}
