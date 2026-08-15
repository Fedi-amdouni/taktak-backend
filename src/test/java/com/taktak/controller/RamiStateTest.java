package com.taktak.controller;

import com.taktak.game.controller.GameWebSocketController;
import com.taktak.game.state.ChkobbaState;
import com.taktak.game.state.RamiState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RamiStateTest {

    @Test
    void firstLayCanUseAJokerAndSeveralMeldsToReachTheMinimumScore() {
        RamiState state = preparedState();
        state.minMeldScore = 74;
        state.hands.put("p1", new ArrayList<>(List.of(
                card("DINARI", "A"), card("KOPPA", "A"), card("SABRES", "A"), card("BASTONI", "A"),
                card("KOPPA", "Q"), card("KOPPA", "K"), joker("j1")
        )));

        assertTrue(state.lay("p1", List.of("DINARI-A", "KOPPA-A", "SABRES-A", "BASTONI-A", "KOPPA-Q", "KOPPA-K", "j1")));
        assertTrue(state.playerHasLaid.get("p1"));
        assertEquals(2, state.melds.size());
    }

    @Test
    void firstLayAcceptsASetContainingAJokerAlongsideAnotherMeld() {
        RamiState state = preparedState();
        state.minMeldScore = 74;
        state.hands.put("p1", new ArrayList<>(List.of(
                card("DINARI", "A"), card("KOPPA", "A"), card("SABRES", "A"), card("BASTONI", "A"),
                card("DINARI", "10"), card("KOPPA", "10"), joker("j1")
        )));

        assertTrue(state.lay("p1", List.of("DINARI-A", "KOPPA-A", "SABRES-A", "BASTONI-A", "DINARI-10", "KOPPA-10", "j1")));
        assertTrue(state.playerHasLaid.get("p1"));
        assertEquals(2, state.melds.size());
        assertTrue(state.melds.stream().anyMatch(meld -> "set".equals(meld.getType()) && meld.getCards().stream().anyMatch(card -> "j1".equals(card.getId()))));
    }

    @Test
    void firstLayAcceptsTheExactTwoSetsSelectedByThePlayer() {
        RamiState state = preparedState();
        state.minMeldScore = 30;
        state.hands.put("p1", new ArrayList<>(List.of(
                card("DINARI", "6"), card("KOPPA", "6"), card("SABRES", "6"),
                card("DINARI", "9"), card("KOPPA", "9"), card("BASTONI", "9")
        )));

        assertTrue(state.lay("p1", List.of("DINARI-6", "KOPPA-6", "SABRES-6", "DINARI-9", "KOPPA-9", "BASTONI-9")));
        assertEquals(2, state.melds.size());
        assertTrue(state.melds.stream().allMatch(meld -> "set".equals(meld.getType())));
    }

    @Test
    void jokerCannotBeDiscardedAndCanBeRecoveredWithItsMatchingCard() {
        RamiState state = preparedState();
        state.playerHasLaid.put("p1", true);
        state.hands.put("p1", new ArrayList<>(List.of(card("KOPPA", "7"), card("KOPPA", "8"), joker("j1"), card("KOPPA", "9"))));

        assertTrue(state.lay("p1", List.of("KOPPA-7", "KOPPA-9", "j1")));
        assertEquals(List.of("KOPPA-7", "j1", "KOPPA-9"),
                state.melds.get(0).getCards().stream().map(ChkobbaState.Card::getId).toList());
        assertFalse(state.discard("p1", "j1"));
        assertTrue(state.replaceJoker("p1", 0, "j1", "KOPPA-8"));
        assertTrue(state.hands.get("p1").stream().anyMatch(card -> "j1".equals(card.getId())));
        assertTrue(state.melds.get(0).getCards().stream().anyMatch(card -> "KOPPA-8".equals(card.getId())));
    }

    @Test
    void jokerRemainingInHandCostsTwentyPointsAtTheEndOfTheRound() {
        RamiState state = preparedState();
        state.hands.put("p1", new ArrayList<>(List.of(card("KOPPA", "7"))));
        state.hands.put("p2", new ArrayList<>(List.of(joker("j1"))));

        assertTrue(state.discard("p1", "KOPPA-7"));
        assertEquals("p1", state.winner);
        assertEquals(20, state.scores.get("p2"));
    }

    @Test
    void firstLayEscalatesMinimumMeldScoreForOtherPlayers() {
        RamiState state = preparedState();
        state.minMeldScore = 74;
        state.hands.put("p1", new ArrayList<>(List.of(
                card("DINARI", "A"), card("KOPPA", "A"), card("SABRES", "A"), card("BASTONI", "A"),
                card("KOPPA", "Q"), card("KOPPA", "K"), joker("j1"),
                card("DINARI", "2")
        )));

        // 4 As (44 pts) + Q-K-Joker KOPPA (30 pts) = 74 pts
        assertTrue(state.lay("p1", List.of("DINARI-A", "KOPPA-A", "SABRES-A", "BASTONI-A", "KOPPA-Q", "KOPPA-K", "j1")));
        assertTrue(state.playerHasLaid.get("p1"));
        // Seuil initial était 74, joueur a posé 74 -> nouveau seuil devient 75 (74 + 1) !
        assertEquals(75, state.minMeldScore);
    }

    private RamiState preparedState() {
        RamiState state = new RamiState();
        state.players.put("p1", new GameWebSocketController.Player("p1", "Amina"));
        state.players.put("p2", new GameWebSocketController.Player("p2", "Sami"));
        state.hands.put("p1", new ArrayList<>());
        state.hands.put("p2", new ArrayList<>());
        state.playerHasLaid.put("p1", false);
        state.playerHasLaid.put("p2", false);
        state.started = true;
        state.turnId = "p1";
        state.hasDrawn = true;
        return state;
    }

    private ChkobbaState.Card card(String suit, String rank) {
        return new ChkobbaState.Card(suit + "-" + rank, suit, rank, "A".equals(rank) ? 1 : 10);
    }

    private ChkobbaState.Card joker(String id) {
        return new ChkobbaState.Card(id, "JOKER", "JKR", 20);
    }
}
