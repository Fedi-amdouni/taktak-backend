package com.taktak.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Server-authoritative Tunisian Rami engine for one table room. */
final class RamiState {
    static final int INITIAL_HAND_SIZE = 10;
    static final String BOT_ID = "rami-bot";
    final LinkedHashMap<String, GameWebSocketController.Player> players = new LinkedHashMap<>();
    final Map<String, List<ChkobbaState.Card>> hands = new LinkedHashMap<>();
    final List<GameWebSocketController.RamiMeld> melds = new ArrayList<>();
    final List<ChkobbaState.Card> drawPile = new ArrayList<>();
    final List<ChkobbaState.Card> discardPile = new ArrayList<>();
    String turnId; String winner; boolean started; boolean hasDrawn; boolean botEnabled; int round;
    private final java.util.Random random = new java.util.Random();

    void join(String id, String name) { if (id == null || id.isBlank()) return; if (players.containsKey(id)) players.put(id, new GameWebSocketController.Player(id, name)); else if (!started && players.size() < 4) players.put(id, new GameWebSocketController.Player(id, name)); }
    void start(boolean requestedBotEnabled) { if (started && winner == null) return; players.remove(BOT_ID); botEnabled = requestedBotEnabled; if (botEnabled && players.size() == 1) players.put(BOT_ID, new GameWebSocketController.Player(BOT_ID, "Bot Rami")); if (players.size() < 2) return; winner = null; round = 1; beginRound(players.keySet().iterator().next()); }
    boolean draw(String playerId, String source) { if (!started || winner != null || !Objects.equals(turnId, playerId) || hasDrawn) return false; ChkobbaState.Card card; if ("discard".equals(source)) { if (discardPile.isEmpty()) return false; card = discardPile.remove(discardPile.size() - 1); } else { if (drawPile.isEmpty()) recycleDiscard(); if (drawPile.isEmpty()) return false; card = drawPile.remove(drawPile.size() - 1); } hands.get(playerId).add(card); hasDrawn = true; return true; }
    boolean lay(String playerId, List<String> cardIds) { if (!started || winner != null || !hasDrawn || !Objects.equals(turnId, playerId) || cardIds == null) return false; List<ChkobbaState.Card> hand = hands.get(playerId); if (hand == null || cardIds.size() < 3 || cardIds.size() > 4 || cardIds.size() != cardIds.stream().distinct().count()) return false; List<ChkobbaState.Card> selected = hand.stream().filter(card -> cardIds.contains(card.getId())).toList(); if (selected.size() != cardIds.size() || !isValidMeld(selected)) return false; hand.removeIf(card -> cardIds.contains(card.getId())); melds.add(new GameWebSocketController.RamiMeld(meldType(selected), selected)); if (hand.isEmpty()) winner = playerId; return true; }
    boolean discard(String playerId, String cardId) { if (!started || winner != null || !hasDrawn || !Objects.equals(turnId, playerId) || cardId == null) return false; List<ChkobbaState.Card> hand = hands.get(playerId); if (hand == null) return false; ChkobbaState.Card card = hand.stream().filter(item -> cardId.equals(item.getId())).findFirst().orElse(null); if (card == null) return false; hand.remove(card); discardPile.add(card); if (hand.isEmpty()) { winner = playerId; hasDrawn = false; } else { turnId = nextPlayer(playerId); hasDrawn = false; } return true; }
    boolean isBotTurn() { return botEnabled && winner == null && Objects.equals(turnId, BOT_ID); }
    boolean playBotTurn() { if (!isBotTurn() || !draw(BOT_ID, "deck")) return false; List<ChkobbaState.Card> hand = hands.get(BOT_ID); List<ChkobbaState.Card> meld = findMeld(hand); if (meld != null) { lay(BOT_ID, meld.stream().map(ChkobbaState.Card::getId).toList()); if (winner != null) return true; } ChkobbaState.Card discard = hand.stream().max(java.util.Comparator.comparingInt(ChkobbaState.Card::getValue)).orElse(null); return discard != null && discard(BOT_ID, discard.getId()); }
    ChkobbaState.Card discardTop() { return discardPile.isEmpty() ? null : discardPile.get(discardPile.size() - 1); }
    private void beginRound(String firstPlayerId) { drawPile.clear(); discardPile.clear(); melds.clear(); hands.clear(); drawPile.addAll(newDeck()); Collections.shuffle(drawPile, random); players.keySet().forEach(id -> hands.put(id, new ArrayList<>())); for (int i = 0; i < INITIAL_HAND_SIZE; i++) for (String id : players.keySet()) hands.get(id).add(drawPile.remove(drawPile.size() - 1)); discardPile.add(drawPile.remove(drawPile.size() - 1)); turnId = firstPlayerId; hasDrawn = false; started = true; }
    private void recycleDiscard() { if (discardPile.size() <= 1) return; ChkobbaState.Card top = discardPile.remove(discardPile.size() - 1); drawPile.addAll(discardPile); discardPile.clear(); discardPile.add(top); Collections.shuffle(drawPile, random); }
    private String nextPlayer(String id) { List<String> ids = new ArrayList<>(players.keySet()); if (ids.isEmpty()) return null; return ids.get((ids.indexOf(id) + 1) % ids.size()); }
    private boolean isValidMeld(List<ChkobbaState.Card> cards) { if (cards.size() < 3 || cards.size() > 4) return false; boolean sameValue = cards.stream().map(ChkobbaState.Card::getValue).distinct().count() == 1; boolean differentSuits = cards.stream().map(ChkobbaState.Card::getSuit).distinct().count() == cards.size(); if (sameValue && differentSuits) return true; String suit = cards.get(0).getSuit(); List<Integer> values = cards.stream().map(ChkobbaState.Card::getValue).sorted().toList(); return cards.stream().allMatch(card -> suit.equals(card.getSuit())) && new HashSet<>(values).size() == values.size() && values.get(values.size() - 1) - values.get(0) == values.size() - 1; }
    private String meldType(List<ChkobbaState.Card> cards) { return cards.stream().map(ChkobbaState.Card::getValue).distinct().count() == 1 ? "set" : "run"; }
    private List<ChkobbaState.Card> findMeld(List<ChkobbaState.Card> cards) { for (int size = 3; size <= 4; size++) { List<ChkobbaState.Card> found = findMeld(cards, 0, size, new ArrayList<>()); if (found != null) return found; } return null; }
    private List<ChkobbaState.Card> findMeld(List<ChkobbaState.Card> cards, int start, int size, List<ChkobbaState.Card> candidate) { if (candidate.size() == size) return isValidMeld(candidate) ? new ArrayList<>(candidate) : null; for (int index = start; index <= cards.size() - (size - candidate.size()); index++) { candidate.add(cards.get(index)); List<ChkobbaState.Card> found = findMeld(cards, index + 1, size, candidate); candidate.remove(candidate.size() - 1); if (found != null) return found; } return null; }
    private List<ChkobbaState.Card> newDeck() { List<ChkobbaState.Card> cards = new ArrayList<>(); List<String> ranks = List.of("A", "2", "3", "4", "5", "6", "7", "J", "Q", "K"); List<Integer> values = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10); for (int copy = 0; copy < 2; copy++) for (String suit : ChkobbaState.SUITS) for (int i = 0; i < ranks.size(); i++) cards.add(new ChkobbaState.Card("RAMI-" + copy + "-" + suit + "-" + ranks.get(i), suit, ranks.get(i), values.get(i))); return cards; }
}
