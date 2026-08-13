package com.taktak.game.state;

import com.taktak.game.controller.GameWebSocketController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Server-authoritative Tunisian Rami engine for one table room. */
public final class RamiState {
    public static final int INITIAL_HAND_SIZE = 14;
    public static final String BOT_ID = "rami-bot";

    public final LinkedHashMap<String, GameWebSocketController.Player> players = new LinkedHashMap<>();
    public final Map<String, List<ChkobbaState.Card>> hands = new LinkedHashMap<>();
    public final List<GameWebSocketController.RamiMeld> melds = new ArrayList<>();
    public final List<ChkobbaState.Card> drawPile = new ArrayList<>();
    public final List<ChkobbaState.Card> discardPile = new ArrayList<>();
    public final Map<String, Boolean> playerHasLaid = new LinkedHashMap<>();
    public final Map<String, JokerReplacement> jokerReplacements = new LinkedHashMap<>();
    public final Map<String, Integer> scores = new LinkedHashMap<>();

    public String turnId;
    public String winner;
    public boolean started;
    public boolean hasDrawn;
    public boolean botEnabled;
    public int round;
    public int minMeldScore = 74;

    private final java.util.Random random = new java.util.Random();

    public record JokerReplacement(String suit, String rank, int value) {}
    public record MeldValidation(String type, int score, Map<String, JokerReplacement> replacements) {}
    private record ResolvedMeld(List<ChkobbaState.Card> cards, MeldValidation validation) {}
    private record BotMelds(List<ChkobbaState.Card> cards, int score) {}

    public void join(String id, String name) {
        if (id == null || id.isBlank()) return;
        if (players.containsKey(id)) {
            players.put(id, new GameWebSocketController.Player(id, name));
        } else if (!started && players.size() < 4) {
            players.put(id, new GameWebSocketController.Player(id, name));
        }
    }

    public void leave(String id) {
        if (id != null) {
            players.remove(id);
            hands.remove(id);
            playerHasLaid.remove(id);
            scores.remove(id);
        }
        if (players.isEmpty()) {
            started = false;
            turnId = null;
            winner = null;
        } else if (Objects.equals(turnId, id)) {
            turnId = players.keySet().iterator().next();
        }
    }

    public void start(boolean requestedBotEnabled, Integer requestedMinMeldScore) {
        if (started && winner == null) return;
        players.remove(BOT_ID);
        botEnabled = requestedBotEnabled;
        if (requestedMinMeldScore != null && requestedMinMeldScore > 0) {
            this.minMeldScore = requestedMinMeldScore;
        } else {
            this.minMeldScore = 74;
        }
        if (botEnabled && players.size() == 1) players.put(BOT_ID, new GameWebSocketController.Player(BOT_ID, "Bot Rami"));
        if (players.size() < 2) return;
        winner = null;
        scores.clear();
        players.keySet().forEach(id -> scores.put(id, 0));
        round = 1;
        beginRound(players.keySet().iterator().next());
    }

    public boolean draw(String playerId, String source) {
        if (!started || winner != null || !Objects.equals(turnId, playerId) || hasDrawn) return false;
        ChkobbaState.Card card;
        if ("discard".equals(source)) {
            if (discardPile.isEmpty()) return false;
            card = discardPile.remove(discardPile.size() - 1);
        } else {
            if (drawPile.isEmpty()) recycleDiscard();
            if (drawPile.isEmpty()) return false;
            card = drawPile.remove(drawPile.size() - 1);
        }
        hands.get(playerId).add(card);
        hasDrawn = true;
        return true;
    }

    private int rankIndex(String rank, boolean highAce) {
        if (rank == null) return 0;
        return switch (rank) {
            case "A" -> highAce ? 14 : 1;
            case "2" -> 2;
            case "3" -> 3;
            case "4" -> 4;
            case "5" -> 5;
            case "6" -> 6;
            case "7" -> 7;
            case "8" -> 8;
            case "9" -> 9;
            case "10" -> 10;
            case "J" -> 11;
            case "Q" -> 12;
            case "K" -> 13;
            default -> 0;
        };
    }

    private boolean isJoker(ChkobbaState.Card card) { return "JKR".equals(card.getRank()); }

    private int scoreForRank(String rank, boolean highAce) {
        if ("A".equals(rank)) return highAce ? 11 : 1;
        return "10".equals(rank) || "J".equals(rank) || "Q".equals(rank) || "K".equals(rank) ? 10 : Integer.parseInt(rank);
    }

    private String rankForIndex(int index) { return index == 1 || index == 14 ? "A" : index <= 10 ? String.valueOf(index) : switch (index) { case 11 -> "J"; case 12 -> "Q"; default -> "K"; }; }

    private MeldValidation validateMeld(List<ChkobbaState.Card> cards) {
        if (cards == null || cards.size() < 3 || cards.size() > 13) return null;
        List<ChkobbaState.Card> naturals = cards.stream().filter(card -> !isJoker(card)).toList();
        List<ChkobbaState.Card> jokers = cards.stream().filter(this::isJoker).toList();
        if (jokers.size() > naturals.size()) return null;
        MeldValidation set = validateSet(cards, naturals, jokers);
        if (set != null) return set;
        MeldValidation lowAceRun = validateRun(cards.size(), naturals, jokers, false);
        return lowAceRun != null ? lowAceRun : validateRun(cards.size(), naturals, jokers, true);
    }

    private MeldValidation validateSet(List<ChkobbaState.Card> cards, List<ChkobbaState.Card> naturals, List<ChkobbaState.Card> jokers) {
        if (cards.size() > 4 || naturals.isEmpty()) return null;
        String rank = naturals.get(0).getRank();
        if (!naturals.stream().allMatch(card -> rank.equals(card.getRank())) || naturals.stream().map(ChkobbaState.Card::getSuit).distinct().count() != naturals.size()) return null;
        List<String> missingSuits = ChkobbaState.SUITS.stream().filter(suit -> naturals.stream().noneMatch(card -> suit.equals(card.getSuit()))).toList();
        if (jokers.size() > missingSuits.size()) return null;
        Map<String, JokerReplacement> replacements = new LinkedHashMap<>();
        for (int index = 0; index < jokers.size(); index++) replacements.put(jokers.get(index).getId(), new JokerReplacement(missingSuits.get(index), rank, scoreForRank(rank, true)));
        return new MeldValidation("set", scoreForRank(rank, true) * cards.size(), replacements);
    }

    private MeldValidation validateRun(int totalCards, List<ChkobbaState.Card> naturals, List<ChkobbaState.Card> jokers, boolean highAce) {
        if (naturals.isEmpty()) return null;
        String suit = naturals.get(0).getSuit();
        if (!naturals.stream().allMatch(card -> suit.equals(card.getSuit()))) return null;
        List<Integer> ranks = naturals.stream().map(card -> rankIndex(card.getRank(), highAce)).sorted().toList();
        if (new HashSet<>(ranks).size() != ranks.size() || ranks.contains(0)) return null;
        for (int start = 1; start <= 15 - totalCards; start++) {
            List<Integer> interval = java.util.stream.IntStream.range(start, start + totalCards).boxed().toList();
            if (!interval.containsAll(ranks)) continue;
            List<Integer> missing = interval.stream().filter(rank -> !ranks.contains(rank)).toList();
            if (missing.size() != jokers.size()) continue;
            Map<String, JokerReplacement> replacements = new LinkedHashMap<>();
            int score = 0;
            for (int rank : interval) score += scoreForRank(rankForIndex(rank), highAce);
            for (int index = 0; index < jokers.size(); index++) replacements.put(jokers.get(index).getId(), new JokerReplacement(suit, rankForIndex(missing.get(index)), scoreForRank(rankForIndex(missing.get(index)), highAce)));
            return new MeldValidation("run", score, replacements);
        }
        return null;
    }

    private boolean isValidMeld(List<ChkobbaState.Card> cards) { return validateMeld(cards) != null; }

    public int calculateMeldScore(List<ChkobbaState.Card> cards) { MeldValidation validation = validateMeld(cards); return validation == null ? 0 : validation.score(); }

    public boolean lay(String playerId, List<String> cardIds) {
        if (!started || winner != null || !hasDrawn || !Objects.equals(turnId, playerId) || cardIds == null) return false;
        List<ChkobbaState.Card> hand = hands.get(playerId);
        if (hand == null || cardIds.size() < 3 || cardIds.size() != cardIds.stream().distinct().count()) return false;
        List<ChkobbaState.Card> selected = hand.stream().filter(card -> cardIds.contains(card.getId())).toList();
        if (selected.size() != cardIds.size()) return false;
        List<ResolvedMeld> resolved = splitIntoMelds(selected);
        if (resolved == null) return false;
        boolean hasLaidBefore = playerHasLaid.getOrDefault(playerId, false);
        int totalScore = resolved.stream().mapToInt(meld -> meld.validation().score()).sum();
        if (!hasLaidBefore && totalScore < minMeldScore) return false;

        hand.removeIf(card -> cardIds.contains(card.getId()));
        for (ResolvedMeld meld : resolved) {
            melds.add(new GameWebSocketController.RamiMeld(meld.validation().type(), meld.cards()));
            jokerReplacements.putAll(meld.validation().replacements());
        }
        playerHasLaid.put(playerId, true);
        if (hand.isEmpty()) finishRound(playerId);
        return true;
    }

    public boolean discard(String playerId, String cardId) {
        if (!started || winner != null || !hasDrawn || !Objects.equals(turnId, playerId) || cardId == null) return false;
        List<ChkobbaState.Card> hand = hands.get(playerId);
        if (hand == null) return false;
        ChkobbaState.Card card = hand.stream().filter(item -> cardId.equals(item.getId())).findFirst().orElse(null);
        if (card == null || isJoker(card)) return false;
        hand.remove(card);
        discardPile.add(card);
        if (hand.isEmpty()) {
            finishRound(playerId);
            hasDrawn = false;
        } else {
            turnId = nextPlayer(playerId);
            hasDrawn = false;
        }
        return true;
    }

    private void finishRound(String winningPlayerId) {
        winner = winningPlayerId;
        players.keySet().forEach(id -> {
            if (!id.equals(winningPlayerId)) {
                int penalty = hands.getOrDefault(id, List.of()).stream().mapToInt(this::cardPenalty).sum();
                scores.put(id, scores.getOrDefault(id, 0) + penalty);
            }
        });
    }

    private int cardPenalty(ChkobbaState.Card card) {
        if (isJoker(card)) return 20;
        return scoreForRank(card.getRank(), true);
    }

    public boolean replaceJoker(String playerId, int meldIndex, String jokerId, String replacementCardId) {
        if (!started || winner != null || !hasDrawn || !Objects.equals(turnId, playerId) || !playerHasLaid.getOrDefault(playerId, false) || meldIndex < 0 || meldIndex >= melds.size()) return false;
        JokerReplacement expected = jokerReplacements.get(jokerId);
        GameWebSocketController.RamiMeld meld = melds.get(meldIndex);
        List<ChkobbaState.Card> hand = hands.get(playerId);
        if (expected == null || hand == null || !meld.getCards().stream().anyMatch(card -> jokerId.equals(card.getId()))) return false;
        ChkobbaState.Card replacement = hand.stream().filter(card -> replacementCardId.equals(card.getId())).findFirst().orElse(null);
        if (replacement == null || isJoker(replacement) || !expected.suit().equals(replacement.getSuit()) || !expected.rank().equals(replacement.getRank())) return false;
        List<ChkobbaState.Card> updated = new ArrayList<>(meld.getCards());
        int jokerPosition = java.util.stream.IntStream.range(0, updated.size()).filter(index -> jokerId.equals(updated.get(index).getId())).findFirst().orElse(-1);
        if (jokerPosition < 0) return false;
        ChkobbaState.Card joker = updated.set(jokerPosition, new ChkobbaState.Card(replacement.getId(), replacement.getSuit(), replacement.getRank(), replacement.getValue()));
        hand.remove(replacement);
        hand.add(joker);
        meld.setCards(updated);
        jokerReplacements.remove(jokerId);
        return true;
    }

    public boolean extendMeld(String playerId, int meldIndex, List<String> cardIds) {
        if (!started || winner != null || !hasDrawn || !Objects.equals(turnId, playerId) || !playerHasLaid.getOrDefault(playerId, false) || meldIndex < 0 || meldIndex >= melds.size() || cardIds == null || cardIds.isEmpty() || cardIds.size() != cardIds.stream().distinct().count()) return false;
        List<ChkobbaState.Card> hand = hands.get(playerId);
        if (hand == null) return false;
        List<ChkobbaState.Card> additions = hand.stream().filter(card -> cardIds.contains(card.getId())).toList();
        if (additions.size() != cardIds.size()) return false;
        GameWebSocketController.RamiMeld meld = melds.get(meldIndex);
        List<ChkobbaState.Card> combined = new ArrayList<>(meld.getCards());
        combined.addAll(additions);
        MeldValidation validation = validateMeld(combined);
        if (validation == null) return false;
        meld.getCards().stream().filter(this::isJoker).forEach(card -> jokerReplacements.remove(card.getId()));
        hand.removeIf(card -> cardIds.contains(card.getId()));
        meld.setCards(combined);
        jokerReplacements.putAll(validation.replacements());
        if (hand.isEmpty()) finishRound(playerId);
        return true;
    }

    private List<ResolvedMeld> splitIntoMelds(List<ChkobbaState.Card> cards) {
        if (cards.size() < 3) return null;
        return splitIntoMelds(cards, new ArrayList<>());
    }

    private List<ResolvedMeld> splitIntoMelds(List<ChkobbaState.Card> remaining, List<ResolvedMeld> resolved) {
        if (remaining.isEmpty()) return new ArrayList<>(resolved);
        if (remaining.size() < 3) return null;
        int combinations = 1 << remaining.size();
        for (int mask = 1; mask < combinations; mask++) {
            if (Integer.bitCount(mask) < 3) continue;
            List<ChkobbaState.Card> candidate = new ArrayList<>();
            List<ChkobbaState.Card> rest = new ArrayList<>();
            for (int index = 0; index < remaining.size(); index++) {
                if ((mask & (1 << index)) != 0) candidate.add(remaining.get(index)); else rest.add(remaining.get(index));
            }
            MeldValidation validation = validateMeld(candidate);
            if (validation == null) continue;
            resolved.add(new ResolvedMeld(candidate, validation));
            List<ResolvedMeld> result = splitIntoMelds(rest, resolved);
            if (result != null) return result;
            resolved.remove(resolved.size() - 1);
        }
        return null;
    }

    public boolean isBotTurn() {
        return botEnabled && winner == null && Objects.equals(turnId, BOT_ID);
    }

    public boolean playBotTurn() {
        if (!isBotTurn()) return false;
        List<ChkobbaState.Card> botHand = hands.get(BOT_ID);
        String source = discardWouldHelp(botHand) ? "discard" : "deck";
        if (!draw(BOT_ID, source)) return false;
        BotMelds botMelds = findBotMelds(botHand);
        if (!botMelds.cards().isEmpty() && (playerHasLaid.getOrDefault(BOT_ID, false) || botMelds.score() >= minMeldScore)) {
            lay(BOT_ID, botMelds.cards().stream().map(ChkobbaState.Card::getId).toList());
            if (winner != null) return true;
        }
        ChkobbaState.Card discard = botHand.stream().filter(card -> !isJoker(card)).min(java.util.Comparator.comparingInt(this::discardCost)).orElse(null);
        return discard != null && discard(BOT_ID, discard.getId());
    }

    private boolean discardWouldHelp(List<ChkobbaState.Card> hand) {
        ChkobbaState.Card top = discardTop();
        if (top == null) return false;
        List<ChkobbaState.Card> withDiscard = new ArrayList<>(hand);
        withDiscard.add(top);
        return findMeld(withDiscard) != null;
    }

    private int discardCost(ChkobbaState.Card card) {
        int connections = 0;
        for (ChkobbaState.Card other : hands.getOrDefault(BOT_ID, List.of())) {
            if (other == card || isJoker(other)) continue;
            if (card.getRank().equals(other.getRank())) connections += 4;
            if (card.getSuit().equals(other.getSuit()) && Math.abs(rankIndex(card.getRank(), false) - rankIndex(other.getRank(), false)) <= 2) connections += 3;
        }
        return card.getValue() * 10 - connections * 10;
    }

    private BotMelds findBotMelds(List<ChkobbaState.Card> cards) {
        List<ChkobbaState.Card> remaining = new ArrayList<>(cards);
        List<ChkobbaState.Card> selected = new ArrayList<>();
        int score = 0;
        List<ChkobbaState.Card> found;
        while ((found = findMeld(remaining)) != null) {
            selected.addAll(found);
            score += calculateMeldScore(found);
            remaining.removeAll(found);
        }
        return new BotMelds(selected, score);
    }

    public ChkobbaState.Card discardTop() {
        return discardPile.isEmpty() ? null : discardPile.get(discardPile.size() - 1);
    }

    private void beginRound(String firstPlayerId) {
        drawPile.clear();
        discardPile.clear();
        melds.clear();
        hands.clear();
        playerHasLaid.clear();
        drawPile.addAll(newDeck());
        Collections.shuffle(drawPile, random);
        players.keySet().forEach(id -> {
            hands.put(id, new ArrayList<>());
            playerHasLaid.put(id, false);
        });
        for (int i = 0; i < INITIAL_HAND_SIZE; i++) {
            for (String id : players.keySet()) {
                if (!drawPile.isEmpty()) {
                    hands.get(id).add(drawPile.remove(drawPile.size() - 1));
                }
            }
        }
        if (!drawPile.isEmpty()) {
            discardPile.add(drawPile.remove(drawPile.size() - 1));
        }
        turnId = firstPlayerId;
        hasDrawn = false;
        started = true;
    }

    private void recycleDiscard() {
        if (discardPile.size() <= 1) return;
        ChkobbaState.Card top = discardPile.remove(discardPile.size() - 1);
        drawPile.addAll(discardPile);
        discardPile.clear();
        discardPile.add(top);
        Collections.shuffle(drawPile, random);
    }

    private String nextPlayer(String id) {
        List<String> ids = new ArrayList<>(players.keySet());
        if (ids.isEmpty()) return null;
        return ids.get((ids.indexOf(id) + 1) % ids.size());
    }

    private String meldType(List<ChkobbaState.Card> cards) { MeldValidation validation = validateMeld(cards); return validation == null ? "run" : validation.type(); }

    private List<ChkobbaState.Card> findMeld(List<ChkobbaState.Card> cards) {
        for (int size = 3; size <= Math.min(13, cards.size()); size++) {
            List<ChkobbaState.Card> found = findMeld(cards, 0, size, new ArrayList<>());
            if (found != null) return found;
        }
        return null;
    }

    private List<ChkobbaState.Card> findMeld(List<ChkobbaState.Card> cards, int start, int size, List<ChkobbaState.Card> candidate) {
        if (candidate.size() == size) return isValidMeld(candidate) ? new ArrayList<>(candidate) : null;
        for (int index = start; index <= cards.size() - (size - candidate.size()); index++) {
            candidate.add(cards.get(index));
            List<ChkobbaState.Card> found = findMeld(cards, index + 1, size, candidate);
            candidate.remove(candidate.size() - 1);
            if (found != null) return found;
        }
        return null;
    }

    private List<ChkobbaState.Card> newDeck() {
        List<ChkobbaState.Card> cards = new ArrayList<>();
        List<String> ranks = List.of("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K");
        List<Integer> values = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 10, 10);
        int deckCopies = players.size() <= 2 ? 1 : 2;
        for (int copy = 0; copy < deckCopies; copy++) {
            for (String suit : ChkobbaState.SUITS) {
                for (int i = 0; i < ranks.size(); i++) {
                    cards.add(new ChkobbaState.Card("RAMI-" + copy + "-" + suit + "-" + ranks.get(i), suit, ranks.get(i), values.get(i)));
                }
            }
        }
        for (int j = 1; j <= deckCopies * 2; j++) {
            cards.add(new ChkobbaState.Card("RAMI-JKR-" + j, "JOKER", "JKR", 20));
        }
        return cards;
    }
}
