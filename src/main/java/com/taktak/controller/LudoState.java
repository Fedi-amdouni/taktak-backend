package com.taktak.controller;

import java.util.*;

/** Lightweight four-player Ludo engine scoped to one table room. */
final class LudoState {
    final LinkedHashMap<String, GameWebSocketController.Player> players = new LinkedHashMap<>();
    final Map<String, int[]> tokens = new LinkedHashMap<>();
    String turnId; String winner; Integer dice; boolean started; boolean canRoll = true;
    private final Random random = new Random();

    void join(String id, String name) {
        if (players.containsKey(id)) players.put(id, new GameWebSocketController.Player(id, name));
        else if (!started && players.size() < 4) players.put(id, new GameWebSocketController.Player(id, name));
    }

    void leave(String id) {
        if (id != null) {
            players.remove(id);
            tokens.remove(id);
        }
        if (players.isEmpty()) {
            started = false;
            turnId = null;
            winner = null;
            dice = null;
            canRoll = true;
        } else if (Objects.equals(turnId, id)) {
            advanceTurn();
        }
    }

    void start() {
        if (players.size() < 2) return;
        tokens.clear(); players.keySet().forEach(id -> tokens.put(id, new int[]{-1,-1,-1,-1}));
        turnId = players.keySet().iterator().next(); winner = null; dice = null; canRoll = true; started = true;
    }

    boolean roll(String id) {
        if (!started || winner != null || !canRoll || !Objects.equals(turnId, id)) return false;
        dice = random.nextInt(6) + 1; canRoll = false;
        if (!hasMove(id, dice)) { advanceTurn(); dice = null; canRoll = true; }
        return true;
    }

    boolean move(String id, Integer tokenIndex) {
        if (!started || winner != null || canRoll || dice == null || !Objects.equals(turnId,id) || tokenIndex == null || tokenIndex < 0 || tokenIndex > 3) return false;
        int[] mine=tokens.get(id); int position=mine[tokenIndex];
        if (position == -1) { if (dice != 6) return false; mine[tokenIndex]=0; }
        else { if (position + dice > 57) return false; mine[tokenIndex] += dice; }
        capture(id, mine[tokenIndex]);
        if (Arrays.stream(mine).allMatch(value -> value == 57)) winner=id;
        boolean extra = dice == 6 && winner == null; dice=null; canRoll=true; if(!extra) advanceTurn();
        return true;
    }

    private boolean hasMove(String id,int rolled){return Arrays.stream(tokens.get(id)).anyMatch(p -> (p==-1&&rolled==6)||(p>=0&&p<57&&p+rolled<=57));}
    private void advanceTurn(){List<String> ids=new ArrayList<>(players.keySet());turnId=ids.get((ids.indexOf(turnId)+1)%ids.size());}
    private void capture(String moverId,int progress){if(progress<0||progress>51)return;int global=globalCell(moverId,progress);if(Set.of(0,13,26,39).contains(global))return;for(var entry:tokens.entrySet()){if(entry.getKey().equals(moverId))continue;for(int i=0;i<4;i++)if(entry.getValue()[i]>=0&&entry.getValue()[i]<=51&&globalCell(entry.getKey(),entry.getValue()[i])==global)entry.getValue()[i]=-1;}}
    private int globalCell(String id,int progress){List<String> ids=new ArrayList<>(players.keySet());return (ids.indexOf(id)*13+progress)%52;}
}
