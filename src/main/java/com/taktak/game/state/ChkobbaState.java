package com.taktak.game.state;

import com.taktak.game.controller.GameWebSocketController;

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
public final class ChkobbaState {
    public static final int DEFAULT_TARGET_SCORE = 11;
    public static final int TARGET_SCORE = DEFAULT_TARGET_SCORE;
    public static final String BOT_ID = "chkobba-bot";
    public static final String DINARI = "DINARI";
    public static final List<String> SUITS = List.of(DINARI, "KOPPA", "SABRES", "BASTONI");
    private static final List<String> RANKS = List.of("A", "2", "3", "4", "5", "6", "7", "J", "Q", "K");
    private static final List<Integer> VALUES = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    public final LinkedHashMap<String, GameWebSocketController.Player> players = new LinkedHashMap<>();
    public final Map<String, List<Card>> hands = new LinkedHashMap<>();
    public final List<Card> table = new ArrayList<>();
    public final Map<String, List<Card>> captured = new LinkedHashMap<>();
    public final Map<String, Integer> scores = new LinkedHashMap<>();
    public final Map<String, String> teamByPlayerId = new LinkedHashMap<>();
    public final Map<String, Integer> teamScores = new LinkedHashMap<>();
    public final Map<String, Integer> scopaCounts = new LinkedHashMap<>();
    public final Map<String, Integer> roundScores = new LinkedHashMap<>();
    public final Map<String, Integer> lastRoundScores = new LinkedHashMap<>();

    public String turnId;
    public String winner;
    public String lastRoundWinner;
    public String lastMovePlayerId;
    public Card lastMoveCard;
    public int lastCaptureCount;
    public boolean lastScopa;
    public int deckRemaining;
    public int round;
    public int dealNumber;
    public int targetScore = DEFAULT_TARGET_SCORE;
    public boolean botEnabled;
    public boolean teamMode;
    public boolean started;
    public String winnerTeam;
    public String botPartnerId;

    private final List<Card> deck = new ArrayList<>();
    private final Random random = new Random();
    private String lastCapturerId;

    public void join(String id, String name) {
        if (id == null || id.isBlank()) return;
        if (players.containsKey(id)) {
            players.put(id, new GameWebSocketController.Player(id, name));
        } else if (!started && players.size() < 4) {
            players.put(id, new GameWebSocketController.Player(id, name));
            scores.put(id, 0);
        }
    }

    public void leave(String id) {
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

    public void start() {
        start(DEFAULT_TARGET_SCORE, false);
    }

    public void start(Integer requestedTargetScore, boolean withBot) {
        start(requestedTargetScore, withBot, false, null);
    }

    public void start(Integer requestedTargetScore, boolean withBot, boolean requestedTeamMode, String requestedBotPartnerId) {
        if (started && winner == null) return;
        teamMode = requestedTeamMode;
        targetScore = (requestedTargetScore != null && requestedTargetScore >= 5 && requestedTargetScore <= 31)
                ? requestedTargetScore
                : DEFAULT_TARGET_SCORE;
        botEnabled = withBot;
        if (botEnabled && players.size() == 1) {
            players.put(BOT_ID, new GameWebSocketController.Player(BOT_ID, "Bot Sirocco"));
            scores.put(BOT_ID, 0);
        } else if (botEnabled && players.size() == 3) {
            players.put(BOT_ID, new GameWebSocketController.Player(BOT_ID, "Bot Sirocco"));
            scores.put(BOT_ID, 0);
        } else if (!botEnabled) {
            players.remove(BOT_ID);
            hands.remove(BOT_ID);
            scores.remove(BOT_ID);
            teamByPlayerId.remove(BOT_ID);
        }

        if (teamMode && players.size() == 4) {
            List<String> ids = new ArrayList<>(players.keySet());
            if (withBot && requestedBotPartnerId != null && ids.contains(requestedBotPartnerId)) {
                botPartnerId = requestedBotPartnerId;
                List<String> humans = ids.stream().filter(id -> !BOT_ID.equals(id)).toList();
                String otherHuman = humans.stream().filter(id -> !id.equals(botPartnerId)).findFirst().orElse(humans.get(0));
                teamByPlayerId.put(ids.get(0), ids.get(0).equals(botPartnerId) || ids.get(0).equals(BOT_ID) ? "TEAM_A" : "TEAM_B");
                teamByPlayerId.put(ids.get(1), ids.get(1).equals(botPartnerId) || ids.get(1).equals(BOT_ID) ? "TEAM_A" : "TEAM_B");
                teamByPlayerId.put(ids.get(2), ids.get(2).equals(botPartnerId) || ids.get(2).equals(BOT_ID) ? "TEAM_A" : "TEAM_B");
                teamByPlayerId.put(ids.get(3), ids.get(3).equals(botPartnerId) || ids.get(3).equals(BOT_ID) ? "TEAM_A" : "TEAM_B");
            } else {
                botPartnerId = null;
                teamByPlayerId.put(ids.get(0), "TEAM_A");
                teamByPlayerId.put(ids.get(1), "TEAM_B");
                teamByPlayerId.put(ids.get(2), "TEAM_A");
                teamByPlayerId.put(ids.get(3), "TEAM_B");
            }
            teamScores.put("TEAM_A", 0);
            teamScores.put("TEAM_B", 0);
        } else {
            teamMode = false;
            teamByPlayerId.clear();
            teamScores.clear();
            botPartnerId = null;
        }

        winner = null;
        winnerTeam = null;
        round = 1;
        lastRoundWinner = null;
        lastMovePlayerId = null;
        lastMoveCard = null;
        lastCaptureCount = 0;
        lastScopa = false;
        scopaCounts.clear();
        for (String id : players.keySet()) {
            scores.put(id, 0);
            scopaCounts.put(id, 0);
        }

        resetMatchState();
    }

    public boolean isBotTurn() {
        return botEnabled && started && winner == null && Objects.equals(turnId, BOT_ID);
    }

    public boolean play(String playerId, String cardId, List<String> capturedCardIds) {
        return playCard(playerId, cardId, capturedCardIds);
    }

    public boolean playCard(String playerId, String cardId, List<String> capturedCardIds) {
        if (!started || winner != null || !Objects.equals(turnId, playerId)) return false;

        List<Card> hand = hands.get(playerId);
        if (hand == null) return false;

        Card played = hand.stream().filter(c -> c.getId().equals(cardId)).findFirst().orElse(null);
        if (played == null) return false;

        List<Card> targetCards = new ArrayList<>();
        if (capturedCardIds != null && !capturedCardIds.isEmpty()) {
            for (String cid : capturedCardIds) {
                Card found = table.stream().filter(c -> c.getId().equals(cid)).findFirst().orElse(null);
                if (found == null) return false;
                targetCards.add(found);
            }
        }

        if (targetCards.isEmpty()) {
            List<List<Card>> validCombos = findValidCaptureCombos(played, table);
            if (!validCombos.isEmpty()) return false;

            hand.remove(played);
            table.add(played);

            lastMovePlayerId = playerId;
            lastMoveCard = played;
            lastCaptureCount = 0;
            lastScopa = false;
        } else {
            int sum = targetCards.stream().mapToInt(Card::getValue).sum();
            if (sum != played.getValue()) return false;

            if (targetCards.size() > 1) {
                boolean exactMatchExists = table.stream().anyMatch(c -> c.getValue() == played.getValue());
                if (exactMatchExists) return false;
            }

            hand.remove(played);
            table.removeAll(targetCards);

            List<Card> capturedList = captured.computeIfAbsent(playerId, k -> new ArrayList<>());
            capturedList.add(played);
            capturedList.addAll(targetCards);

            lastCapturerId = playerId;
            lastMovePlayerId = playerId;
            lastMoveCard = played;
            lastCaptureCount = targetCards.size();

            if (table.isEmpty() && !isLastMoveOfRound()) {
                scopaCounts.put(playerId, scopaCounts.getOrDefault(playerId, 0) + 1);
                lastScopa = true;
            } else {
                lastScopa = false;
            }
        }

        advanceTurn();

        if (allHandsEmpty()) {
            if (deck.isEmpty()) {
                endRound();
            } else {
                dealBatch();
            }
        }

        return true;
    }

    public boolean playBotTurn() {
        if (!started || winner != null || !Objects.equals(turnId, BOT_ID)) return false;

        List<Card> botHand = hands.get(BOT_ID);
        if (botHand == null || botHand.isEmpty()) return false;

        Card bestCard = null;
        List<String> bestCaptureIds = null;
        int bestScore = -1;

        for (Card card : botHand) {
            List<List<Card>> validCombos = findValidCaptureCombos(card, table);
            if (validCombos.isEmpty()) {
                int score = 10 - card.getValue();
                if (score > bestScore) {
                    bestScore = score;
                    bestCard = card;
                    bestCaptureIds = List.of();
                }
            } else {
                for (List<Card> combo : validCombos) {
                    int score = 20 + combo.size() * 5;
                    if (card.getSuit().equals(DINARI)) score += 8;
                    if (card.getRank().equals("7") && card.getSuit().equals(DINARI)) score += 25;
                    for (Card c : combo) {
                        if (c.getSuit().equals(DINARI)) score += 5;
                        if (c.getRank().equals("7") && c.getSuit().equals(DINARI)) score += 20;
                    }
                    if (combo.size() == table.size() && !isLastMoveOfRound()) score += 30;

                    if (score > bestScore) {
                        bestScore = score;
                        bestCard = card;
                        bestCaptureIds = combo.stream().map(Card::getId).toList();
                    }
                }
            }
        }

        if (bestCard == null) bestCard = botHand.get(0);
        return playCard(BOT_ID, bestCard.getId(), bestCaptureIds);
    }

    private void resetMatchState() {
        deck.clear();
        deck.addAll(newDeck());
        Collections.shuffle(deck, random);

        table.clear();
        hands.clear();
        captured.clear();
        lastCapturerId = null;
        dealNumber = 0;

        for (String id : players.keySet()) {
            hands.put(id, new ArrayList<>());
            captured.put(id, new ArrayList<>());
        }

        for (int i = 0; i < 4; i++) {
            table.add(deck.remove(deck.size() - 1));
        }

        dealBatch();
        turnId = players.keySet().iterator().next();
        deckRemaining = deck.size();
        started = true;
    }

    private void dealBatch() {
        dealNumber++;
        for (String id : players.keySet()) {
            List<Card> hand = hands.get(id);
            for (int i = 0; i < 3 && !deck.isEmpty(); i++) {
                hand.add(deck.remove(deck.size() - 1));
            }
        }
        deckRemaining = deck.size();
    }

    private void advanceTurn() {
        turnId = nextPlayer(turnId);
    }

    private void endRound() {
        round++;
        if (lastCapturerId != null && !table.isEmpty()) {
            captured.computeIfAbsent(lastCapturerId, k -> new ArrayList<>()).addAll(table);
            table.clear();
        }

        Map<String, Integer> roundPoints = calculateRoundPoints();
        lastRoundScores.clear();
        lastRoundScores.putAll(roundPoints);

        if (teamMode) {
            int teamAScore = roundPoints.entrySet().stream()
                    .filter(e -> "TEAM_A".equals(teamByPlayerId.get(e.getKey())))
                    .mapToInt(Map.Entry::getValue).sum();
            int teamBScore = roundPoints.entrySet().stream()
                    .filter(e -> "TEAM_B".equals(teamByPlayerId.get(e.getKey())))
                    .mapToInt(Map.Entry::getValue).sum();

            teamScores.put("TEAM_A", teamScores.getOrDefault("TEAM_A", 0) + teamAScore);
            teamScores.put("TEAM_B", teamScores.getOrDefault("TEAM_B", 0) + teamBScore);

            for (String id : players.keySet()) {
                String team = teamByPlayerId.get(id);
                scores.put(id, teamScores.getOrDefault(team, 0));
            }

            if (teamScores.get("TEAM_A") >= targetScore || teamScores.get("TEAM_B") >= targetScore) {
                if (teamScores.get("TEAM_A") > teamScores.get("TEAM_B")) {
                    winnerTeam = "TEAM_A";
                    winner = players.keySet().stream().filter(id -> "TEAM_A".equals(teamByPlayerId.get(id))).findFirst().orElse(null);
                } else if (teamScores.get("TEAM_B") > teamScores.get("TEAM_A")) {
                    winnerTeam = "TEAM_B";
                    winner = players.keySet().stream().filter(id -> "TEAM_B".equals(teamByPlayerId.get(id))).findFirst().orElse(null);
                }
            }
        } else {
            for (Map.Entry<String, Integer> entry : roundPoints.entrySet()) {
                String id = entry.getKey();
                int current = scores.getOrDefault(id, 0);
                scores.put(id, current + entry.getValue());
            }

            String topPlayer = null;
            int maxScore = -1;
            for (Map.Entry<String, Integer> entry : scores.entrySet()) {
                if (entry.getValue() > maxScore) {
                    maxScore = entry.getValue();
                    topPlayer = entry.getKey();
                }
            }

            if (maxScore >= targetScore) {
                final int topScore = maxScore;
                long countAtTop = scores.values().stream().filter(s -> s == topScore).count();
                if (countAtTop == 1) {
                    winner = topPlayer;
                }
            }
        }

        if (winner == null) {
            resetMatchState();
        }
    }

    private Map<String, Integer> calculateRoundPoints() {
        Map<String, Integer> points = new LinkedHashMap<>();
        for (String id : players.keySet()) {
            points.put(id, scopaCounts.getOrDefault(id, 0));
        }

        String maxCardsPlayer = null;
        int maxCards = -1;
        boolean cardsTie = false;

        String maxDinariPlayer = null;
        int maxDinari = -1;
        boolean dinariTie = false;

        String max777Player = null;
        int max777Score = -1;
        boolean s777Tie = false;

        String eb7aDinariPlayer = null;

        for (Map.Entry<String, List<Card>> entry : captured.entrySet()) {
            String id = entry.getKey();
            List<Card> list = entry.getValue();

            if (list.size() > maxCards) {
                maxCards = list.size();
                maxCardsPlayer = id;
                cardsTie = false;
            } else if (list.size() == maxCards && maxCards > 0) {
                cardsTie = true;
            }

            long dinariCount = list.stream().filter(c -> DINARI.equals(c.getSuit())).count();
            if (dinariCount > maxDinari) {
                maxDinari = (int) dinariCount;
                maxDinariPlayer = id;
                dinariTie = false;
            } else if (dinariCount == maxDinari && maxDinari > 0) {
                dinariTie = true;
            }

            if (dinariCount == 10) {
                eb7aDinariPlayer = id;
            }

            int s777 = calculate777Score(list);
            if (s777 > max777Score) {
                max777Score = s777;
                max777Player = id;
                s777Tie = false;
            } else if (s777 == max777Score && max777Score > 0) {
                s777Tie = true;
            }

            if (list.stream().anyMatch(c -> c.getRank().equals("7") && DINARI.equals(c.getSuit()))) {
                points.put(id, points.get(id) + 1);
            }
        }

        if (!cardsTie && maxCardsPlayer != null) points.put(maxCardsPlayer, points.get(maxCardsPlayer) + 1);
        if (!dinariTie && maxDinariPlayer != null) points.put(maxDinariPlayer, points.get(maxDinariPlayer) + 1);
        if (!s777Tie && max777Player != null) points.put(max777Player, points.get(max777Player) + 1);

        if (eb7aDinariPlayer != null) {
            points.put(eb7aDinariPlayer, targetScore);
        }

        return points;
    }

    private int calculate777Score(List<Card> cards) {
        Map<String, Integer> bestBySuit = new LinkedHashMap<>();
        for (String suit : SUITS) {
            int bestVal = cards.stream()
                    .filter(c -> suit.equals(c.getSuit()))
                    .mapToInt(this::get777Val)
                    .max()
                    .orElse(0);
            bestBySuit.put(suit, bestVal);
        }
        return bestBySuit.values().stream().mapToInt(Integer::intValue).sum();
    }

    private int get777Val(Card card) {
        return switch (card.getRank()) {
            case "7" -> 21;
            case "6" -> 18;
            case "A" -> 16;
            case "5" -> 15;
            case "4" -> 14;
            case "3" -> 13;
            case "2" -> 12;
            default -> 10;
        };
    }

    private List<List<Card>> findValidCaptureCombos(Card played, List<Card> tableCards) {
        List<List<Card>> combos = new ArrayList<>();
        Card exact = tableCards.stream().filter(c -> c.getValue() == played.getValue()).findFirst().orElse(null);
        if (exact != null) {
            combos.add(List.of(exact));
            return combos;
        }

        findSumCombos(tableCards, played.getValue(), 0, new ArrayList<>(), combos);
        return combos;
    }

    private void findSumCombos(List<Card> cards, int target, int start, List<Card> current, List<List<Card>> result) {
        int sum = current.stream().mapToInt(Card::getValue).sum();
        if (sum == target && current.size() > 1) {
            result.add(new ArrayList<>(current));
            return;
        }
        if (sum > target) return;

        for (int i = start; i < cards.size(); i++) {
            current.add(cards.get(i));
            findSumCombos(cards, target, i + 1, current, result);
            current.remove(current.size() - 1);
        }
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

        public Card(String id, String suit, String rank, int value) {
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
