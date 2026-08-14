package com.taktak.game.state;

import com.taktak.game.controller.GameWebSocketController;

import java.util.*;

/** Lightweight four-player Ludo engine scoped to one table room. */
public final class LudoState {
    public final LinkedHashMap<String, GameWebSocketController.Player> players = new LinkedHashMap<>();
    public final Map<String, int[]> tokens = new LinkedHashMap<>();
    public String turnId;
    public String winner;
    public Integer dice;
    public boolean started;
    public boolean canRoll = true;
    private final Random random = new Random();

    public void join(String id, String name) {
        if (winner != null) {
            started = false;
            winner = null;
        }
        if (id == null) return;
        if (players.containsKey(id)) {
            players.put(id, new GameWebSocketController.Player(id, name));
        } else if (!started && players.size() < 4) {
            players.put(id, new GameWebSocketController.Player(id, name));
        }
    }

    public void leave(String id) {
        if (id != null) {
            players.remove(id);
            tokens.remove(id);
        }
        boolean hasHumans = players.keySet().stream().anyMatch(pid -> !pid.startsWith("bot_"));
        if (players.isEmpty() || !hasHumans) {
            players.clear();
            tokens.clear();
            started = false;
            turnId = null;
            winner = null;
            dice = null;
            canRoll = true;
        } else if (Objects.equals(turnId, id)) {
            advanceTurn();
        }
    }

    public void start(Boolean botEnabled) {
        if (winner != null) winner = null;
        players.keySet().removeIf(pid -> pid.startsWith("bot_"));
        if (players.isEmpty()) return;

        boolean shouldAddBots = Boolean.TRUE.equals(botEnabled) || players.size() < 4;
        String[] botNames = {"Bot Sirocco", "Bot Jasmine", "Bot Malik"};
        int botIndex = 0;
        while (shouldAddBots && players.size() < 4 && botIndex < botNames.length) {
            String botId = "bot_" + (botIndex + 1);
            players.put(botId, new GameWebSocketController.Player(botId, botNames[botIndex]));
            botIndex++;
        }
        if (players.size() < 2) return;

        tokens.clear();
        players.keySet().forEach(id -> tokens.put(id, new int[]{-1, -1, -1, -1}));
        turnId = players.keySet().iterator().next();
        winner = null;
        dice = null;
        canRoll = true;
        started = true;
    }

    public boolean isBotTurn() {
        return started && winner == null && turnId != null && turnId.startsWith("bot_");
    }

    public boolean playBotTurn() {
        if (!isBotTurn()) return false;
        if (canRoll) {
            return roll(turnId);
        } else if (dice != null) {
            if (!hasMove(turnId, dice)) {
                return passNoMove(turnId);
            }
            int[] myTokens = tokens.get(turnId);
            if (myTokens == null) return false;
            int bestIndex = -1;
            int maxProgress = -2;
            for (int i = 0; i < 4; i++) {
                int pos = myTokens[i];
                if (pos == -1 && dice == 6) {
                    bestIndex = i;
                    break;
                }
                if (pos >= 0 && pos < 57 && pos + dice <= 57) {
                    if (pos > maxProgress) {
                        maxProgress = pos;
                        bestIndex = i;
                    }
                }
            }
            if (bestIndex != -1) {
                return move(turnId, bestIndex);
            } else {
                return passNoMove(turnId);
            }
        }
        return false;
    }

    public boolean roll(String id) {
        if (!started || winner != null || !canRoll || !Objects.equals(turnId, id)) return false;
        dice = random.nextInt(6) + 1;
        canRoll = false;
        return true;
    }

    public boolean passNoMove(String id) {
        if (!started || winner != null || canRoll || dice == null || !Objects.equals(turnId, id)) return false;
        if (hasMove(id, dice)) return false;
        advanceTurn();
        dice = null;
        canRoll = true;
        return true;
    }

    public boolean move(String id, Integer tokenIndex) {
        if (!started || winner != null || canRoll || dice == null || !Objects.equals(turnId, id) || tokenIndex == null || tokenIndex < 0 || tokenIndex > 3) return false;
        int[] mine = tokens.get(id);
        if (mine == null) return false;
        int position = mine[tokenIndex];
        if (position == -1) { if (dice != 6) return false; mine[tokenIndex] = 0; }
        else { if (position + dice > 57) return false; mine[tokenIndex] += dice; }
        capture(id, mine[tokenIndex]);
        if (Arrays.stream(mine).allMatch(value -> value == 57)) winner = id;
        boolean extra = dice == 6 && winner == null;
        dice = null;
        canRoll = true;
        if (!extra) advanceTurn();
        return true;
    }

    public boolean hasMove(String id, int rolled) {
        int[] myTokens = tokens.get(id);
        if (myTokens == null) return false;
        return Arrays.stream(myTokens).anyMatch(p -> (p == -1 && rolled == 6) || (p >= 0 && p < 57 && p + rolled <= 57));
    }

    private void advanceTurn() {
        List<String> ids = new ArrayList<>(players.keySet());
        if (ids.isEmpty()) return;
        int currIdx = ids.indexOf(turnId);
        if (currIdx == -1) currIdx = 0;
        turnId = ids.get((currIdx + 1) % ids.size());
    }

    private void capture(String moverId, int progress) {
        if (progress < 0 || progress > 51) return;
        int global = globalCell(moverId, progress);
        if (Set.of(0, 13, 26, 39).contains(global)) return;
        for (var entry : tokens.entrySet()) {
            if (entry.getKey().equals(moverId)) continue;
            for (int i = 0; i < 4; i++) {
                if (entry.getValue()[i] >= 0 && entry.getValue()[i] <= 51 && globalCell(entry.getKey(), entry.getValue()[i]) == global) {
                    entry.getValue()[i] = -1;
                }
            }
        }
    }

    private int globalCell(String id, int progress) {
        List<String> ids = new ArrayList<>(players.keySet());
        int idx = ids.indexOf(id);
        if (idx == -1) idx = 0;
        return (idx * 13 + progress) % 52;
    }
}
