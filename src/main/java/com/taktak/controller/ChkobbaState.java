package com.taktak.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-authoritative Chkobba engine for one table room.
 *
 * The engine keeps the traditional 40-card deck and scores a match to a
 * configurable target. A hand is dealt in batches of three cards, which lets the UI show
 * the familiar Chkobba rhythm while keeping all move validation on the server.
 */
final class ChkobbaState {
    static final int DEFAULT_TARGET_SCORE = 11;
    static final int TARGET_SCORE = DEFAULT_TARGET_SCORE;
    static final String BOT_ID = "chkobba-bot";
    static final String DINARI = "DINARI";
    static final List<String> SUITS = List.of(DINARI, "KOPPA", "SABRES", "BASTONI");
    private static final List<String> RANKS = List.of("A", "2", "3", "4", "5", "6", "7", "J", "Q", "K");
    private static final List<Integer> VALUES = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    final LinkedHashMap<String, GameWebSocketController.Player> players = new LinkedHashMap<>();
    final Map<String, List<Card>> hands = new LinkedHashMap<>();
    final List<Card> table = new ArrayList<>();
    final Map<String, List<Card>> captured = new LinkedHashMap<>();
    final Map<String, Integer> scores = new LinkedHashMap<>();
    final Map<String, String> teamByPlayerId = new LinkedHashMap<>();
    final Map<String, Integer> teamScores = new LinkedHashMap<>();
    final Map<String, Integer> scopaCounts = new LinkedHashMap<>();
    final Map<String, Integer> roundScores = new LinkedHashMap<>();
    final Map<String, Integer> lastRoundScores = new LinkedHashMap<>();

    String turnId;
    String winner;
    String lastRoundWinner;
    String lastMovePlayerId;
    Card lastMoveCard;
    int lastCaptureCount;
    boolean lastScopa;
    int deckRemaining;
    int round;
    int dealNumber;
    int targetScore = DEFAULT_TARGET_SCORE;
    boolean botEnabled;
    boolean teamMode;
    boolean started;
    String winnerTeam;
    String botPartnerId;

    private final List<Card> deck = new ArrayList<>();
    private final Random random = new Random();
    private String lastCapturerId;

    void join(String id, String name) {
        if (id == null || id.isBlank()) return;
        if (players.containsKey(id)) {
            players.put(id, new GameWebSocketController.Player(id, name));
        } else if (!started && players.size() < 4) {
            players.put(id, new GameWebSocketController.Player(id, name));
            scores.put(id, 0);
        }
    }

    void leave(String id) {
        if (id != null) {
            players.remove(id);
            hands.remove(id);
            scores.remove(id);
            teamByPlayerId.remove(id);
        }
        if (players.isEmpty()) {
            started = false;
            turnId = null;
            winner = null;
            winnerTeam = null;
            teamScores.clear();
        } else if (Objects.equals(turnId, id)) {
            turnId = players.keySet().iterator().next();
        }
    }

    void start() {
        start(DEFAULT_TARGET_SCORE, false);
    }

    void start(Integer requestedTargetScore, boolean withBot) {
        start(requestedTargetScore, withBot, false, null);
    }

    void start(Integer requestedTargetScore, boolean withBot, boolean requestedTeamMode, String requestedBotPartnerId) {
        if (started && winner == null) return;
        teamMode = requestedTeamMode;
        botPartnerId = null;
        if (teamMode && withBot && players.size() == 3 && !players.containsKey(BOT_ID) && players.containsKey(requestedBotPartnerId)) {
            players.put(BOT_ID, new GameWebSocketController.Player(BOT_ID, "Bot Sirocco"));
            botPartnerId = requestedBotPartnerId;
        } else if (!teamMode && withBot && players.size() == 1 && !players.containsKey(BOT_ID)) {
            players.put(BOT_ID, new GameWebSocketController.Player(BOT_ID, "Bot Sirocco"));
        }
        if ((!teamMode && !withBot) || (teamMode && !withBot)) players.remove(BOT_ID);
        if (teamMode && players.size() != 4) return;
        if (!teamMode && players.size() < 2) return;
        targetScore = requestedTargetScore != null && requestedTargetScore == 21 ? 21 : DEFAULT_TARGET_SCORE;
        botEnabled = withBot && players.containsKey(BOT_ID);
        configureTeams();
        scores.clear();
        players.keySet().forEach(id -> scores.put(id, 0));
        winner = null;
        lastRoundWinner = null;
        winnerTeam = null;
        round = 1;
        lastRoundScores.clear();
        beginRound(players.keySet().iterator().next());
    }

    private void configureTeams() {
        teamByPlayerId.clear();
        teamScores.clear();
        if (!teamMode) return;
        List<String> ids = new ArrayList<>(players.keySet());
        String teamA = "TEAM_A";
        String teamB = "TEAM_B";
        if (players.containsKey(BOT_ID) && botPartnerId != null) {
            teamByPlayerId.put(botPartnerId, teamA);
            teamByPlayerId.put(BOT_ID, teamA);
            List<String> opponents = ids.stream().filter(id -> !id.equals(botPartnerId) && !id.equals(BOT_ID)).toList();
            opponents.forEach(id -> teamByPlayerId.put(id, teamB));
        } else {
            teamByPlayerId.put(ids.get(0), teamA);
            teamByPlayerId.put(ids.get(2), teamA);
            teamByPlayerId.put(ids.get(1), teamB);
            teamByPlayerId.put(ids.get(3), teamB);
        }
        teamScores.put(teamA, 0);
        teamScores.put(teamB, 0);
    }

    boolean isBotTurn() {
        return botEnabled && started && winner == null && Objects.equals(turnId, BOT_ID);
    }

    boolean playBotTurn() {
        if (!isBotTurn()) return false;
        List<Card> hand = hands.getOrDefault(BOT_ID, List.of());
        Card bestCard = null;
        List<String> bestCapture = List.of();
        int bestScore = Integer.MIN_VALUE;
        for (Card card : hand) {
            List<List<Card>> options = legalCaptures(card);
            if (options.isEmpty()) options = List.of(List.of());
            for (List<Card> option : options) {
                int value = option.size() * 100;
                if (option.size() == table.size() && !table.isEmpty()) value += 40;
                if ("DINARI-7".equals(card.id)) value += 25;
                value += random.nextInt(10);
                if (value > bestScore) {
                    bestScore = value;
                    bestCard = card;
                    bestCapture = option.stream().map(capturedCard -> capturedCard.id).toList();
                }
            }
        }
        return bestCard != null && play(BOT_ID, bestCard.id, bestCapture);
    }

    boolean play(String playerId, String cardId, List<String> captureIds) {
        if (!started || winner != null || !Objects.equals(turnId, playerId) || cardId == null) return false;
        List<Card> hand = hands.get(playerId);
        if (hand == null) return false;
        Card played = hand.stream().filter(card -> card.id.equals(cardId)).findFirst().orElse(null);
        if (played == null) return false;

        List<String> requestedIds = captureIds == null ? List.of() : captureIds;
        if (requestedIds.stream().anyMatch(Objects::isNull)
                || requestedIds.size() != requestedIds.stream().distinct().count()) return false;

        List<List<Card>> legal = legalCaptures(played);
        boolean validCapture = legal.stream().anyMatch(option -> sameIds(option, requestedIds));
        if (!validCapture && !(legal.isEmpty() && requestedIds.isEmpty())) return false;

        hand.remove(played);
        List<Card> capturedCards = table.stream().filter(card -> requestedIds.contains(card.id)).toList();
        if (!capturedCards.isEmpty()) {
            table.removeIf(card -> requestedIds.contains(card.id));
            captured.get(playerId).add(played);
            captured.get(playerId).addAll(capturedCards);
            lastCapturerId = playerId;
        } else {
            table.add(played);
        }

        lastMovePlayerId = playerId;
        lastMoveCard = played;
        lastCaptureCount = capturedCards.size();
        lastScopa = !capturedCards.isEmpty() && table.isEmpty() && !isLastMoveOfRound();
        if (lastScopa) scopaCounts.put(playerId, scopaCounts.getOrDefault(playerId, 0) + 1);

        if (allHandsEmpty()) {
            if (deck.isEmpty()) finishRound(playerId);
            else {
                dealNumber++;
                dealNextHand();
                turnId = nextPlayer(playerId);
            }
        } else {
            turnId = nextPlayer(playerId);
        }
        return true;
    }

    private List<List<Card>> legalCaptures(Card played) {
        List<Card> exact = table.stream().filter(card -> card.value == played.value).toList();
        if (!exact.isEmpty()) return exact.stream().map(List::of).toList();

        List<List<Card>> options = new ArrayList<>();
        for (int mask = 1; mask < (1 << table.size()); mask++) {
            List<Card> option = new ArrayList<>();
            int sum = 0;
            for (int index = 0; index < table.size(); index++) {
                if ((mask & (1 << index)) != 0) {
                    Card card = table.get(index);
                    option.add(card);
                    sum += card.value;
                }
            }
            if (sum == played.value) options.add(option);
        }
        return options;
    }

    private boolean sameIds(List<Card> option, List<String> requestedIds) {
        Set<String> expected = option.stream().map(card -> card.id).collect(Collectors.toSet());
        return expected.size() == requestedIds.size() && expected.equals(Set.copyOf(requestedIds));
    }

    private void beginRound(String firstPlayerId) {
        deck.clear();
        deck.addAll(newDeck());
        Collections.shuffle(deck, random);
        table.clear();
        hands.clear();
        captured.clear();
        scopaCounts.clear();
        roundScores.clear();
        players.keySet().forEach(id -> {
            hands.put(id, new ArrayList<>());
            captured.put(id, new ArrayList<>());
            scopaCounts.put(id, 0);
            roundScores.put(id, 0);
        });
        for (int i = 0; i < 4; i++) table.add(draw());
        dealNextHand();
        turnId = firstPlayerId;
        dealNumber = 1;
        deckRemaining = deck.size();
        lastCapturerId = null;
        lastMovePlayerId = null;
        lastMoveCard = null;
        lastCaptureCount = 0;
        lastScopa = false;
        started = true;
    }

    private void dealNextHand() {
        for (String id : players.keySet()) {
            List<Card> hand = hands.get(id);
            for (int i = 0; i < 3 && !deck.isEmpty(); i++) hand.add(draw());
        }
        deckRemaining = deck.size();
    }

    private Card draw() {
        return deck.remove(deck.size() - 1);
    }

    private void finishRound(String lastPlayerId) {
        String recipient = lastCapturerId != null ? lastCapturerId : lastPlayerId;
        if (!table.isEmpty()) {
            captured.get(recipient).addAll(table);
            table.clear();
        }

        Map<String, Integer> cardCounts = new LinkedHashMap<>();
        Map<String, Integer> dinariCounts = new LinkedHashMap<>();
        Map<String, Integer> sevenCounts = new LinkedHashMap<>();
        for (String id : players.keySet()) {
            List<Card> cards = captured.getOrDefault(id, List.of());
            cardCounts.put(id, cards.size());
            dinariCounts.put(id, (int) cards.stream().filter(card -> DINARI.equals(card.suit)).count());
            sevenCounts.put(id, (int) cards.stream().filter(card -> card.value == 7).count());
            int points = scopaCounts.getOrDefault(id, 0);
            if (cards.stream().anyMatch(card -> "DINARI-7".equals(card.id))) points++;
            roundScores.put(id, points);
        }
        awardUniqueMax(cardCounts);
        awardUniqueMax(dinariCounts);
        awardUniqueMax(sevenCounts);
        for (String id : players.keySet()) scores.put(id, scores.getOrDefault(id, 0) + roundScores.getOrDefault(id, 0));

        lastRoundScores.clear();
        lastRoundScores.putAll(roundScores);
        if (teamMode) {
            teamScores.clear();
            scores.forEach((playerId, score) -> teamScores.merge(teamByPlayerId.get(playerId), score, Integer::sum));
            winnerTeam = teamScores.entrySet().stream().filter(entry -> entry.getValue() >= targetScore).map(Map.Entry::getKey).findFirst().orElse(null);
            String leadingTeam = teamScores.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            lastRoundWinner = players.keySet().stream().filter(id -> Objects.equals(teamByPlayerId.get(id), leadingTeam)).findFirst().orElse(null);
            winner = players.keySet().stream().filter(id -> Objects.equals(teamByPlayerId.get(id), winnerTeam)).findFirst().orElse(null);
        } else {
            lastRoundWinner = roundScores.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            winner = scores.entrySet().stream().filter(entry -> entry.getValue() >= targetScore).map(Map.Entry::getKey).findFirst().orElse(null);
        }
        if (winner == null) {
            round++;
            beginRound(nextPlayer(lastPlayerId));
        } else {
            turnId = null;
            deckRemaining = 0;
        }
    }

    private void awardUniqueMax(Map<String, Integer> values) {
        int max = values.values().stream().max(Comparator.naturalOrder()).orElse(0);
        if (max <= 0 || values.values().stream().filter(value -> value == max).count() != 1) return;
        values.entrySet().stream().filter(entry -> entry.getValue() == max).findFirst()
                .ifPresent(entry -> roundScores.put(entry.getKey(), roundScores.getOrDefault(entry.getKey(), 0) + 1));
    }

    private boolean allHandsEmpty() {
        return hands.values().stream().allMatch(List::isEmpty);
    }

    private boolean isLastMoveOfRound() {
        return deck.isEmpty() && allHandsEmpty();
    }

    private String nextPlayer(String id) {
        List<String> ids = new ArrayList<>(players.keySet());
        if (ids.isEmpty()) return null;
        int index = ids.indexOf(id);
        return ids.get((index + 1 + ids.size()) % ids.size());
    }

    private List<Card> newDeck() {
        List<Card> cards = new ArrayList<>();
        for (String suit : SUITS) {
            for (int i = 0; i < RANKS.size(); i++) {
                String rank = RANKS.get(i);
                cards.add(new Card(suit + "-" + rank, suit, rank, VALUES.get(i)));
            }
        }
        return cards;
    }

    /** Public getters keep Jackson's wire format stable without exposing state mutation. */
    public static final class Card {
        private final String id;
        private final String suit;
        private final String rank;
        private final int value;

        Card(String id, String suit, String rank, int value) {
            this.id = id;
            this.suit = suit;
            this.rank = rank;
            this.value = value;
        }

        public String getId() { return id; }
        public String getSuit() { return suit; }
        public String getRank() { return rank; }
        public int getValue() { return value; }
    }
}
