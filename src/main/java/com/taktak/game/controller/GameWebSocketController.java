package com.taktak.game.controller;

import com.taktak.game.state.ChkobbaState;
import com.taktak.game.state.LudoState;
import com.taktak.game.state.RamiState;
import com.taktak.game.state.UnoState;
import com.taktak.service.IPartyQuestionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** In-memory, real-time games scoped to one scanned table. */
@Controller
@RequiredArgsConstructor
public class GameWebSocketController {
    private static final int ROWS = 6;
    private static final int COLUMNS = 7;

    private final SimpMessagingTemplate messagingTemplate;
    private final IPartyQuestionService partyQuestionService;
    private final Map<String, TableGameRoom> rooms = new LinkedHashMap<>();

    /**
     * Removes every in-memory game room for one cafe. Game room state is intentionally
     * ephemeral, so this is the safe way for an administrator to clear test lobbies.
     */
    public int clearRoomsForCafe(String cafeSlug) {
        String roomPrefix = cafeSlug + "-";
        synchronized (rooms) {
            int cleared = 0;
            var iterator = rooms.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getKey().startsWith(roomPrefix)) {
                    iterator.remove();
                    cleared++;
                }
            }
            return cleared;
        }
    }

    /** Clears a disconnected player from every game at every table they joined. */
    public void removePlayer(String playerId) {
        if (playerId == null || playerId.isBlank()) return;

        synchronized (rooms) {
            var iterator = rooms.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                String tableId = entry.getKey();
                TableGameRoom room = entry.getValue();

                if (room.roulettePlayers.remove(playerId) != null) {
                    broadcast(tableId, roulettePlayersEvent(room));
                }

                if (Objects.equals(room.connectFour.redPlayer == null ? null : room.connectFour.redPlayer.id, playerId)
                        || Objects.equals(room.connectFour.yellowPlayer == null ? null : room.connectFour.yellowPlayer.id, playerId)) {
                    room.connectFour.leave(playerId);
                    broadcast(tableId, connectFourEvent(room.connectFour));
                }

                if (room.uno.players.containsKey(playerId)) {
                    room.uno.leave(playerId);
                    boolean noHuman = room.uno.players.keySet().stream().noneMatch(id -> !id.startsWith("bot_"));
                    if (noHuman || room.uno.started) room.uno = new UnoState();
                    broadcastUno(tableId, room.uno);
                }

                if (room.party.players.containsKey(playerId)) {
                    room.party.leave(playerId);
                    boolean noHuman = room.party.players.keySet().stream().noneMatch(id -> !id.startsWith("bot_"));
                    if (noHuman || room.party.started) room.party = new AdvancedPartyState();
                    broadcast(tableId, partyEvent(room.party));
                }

                if (room.ludo.players.containsKey(playerId)) {
                    room.ludo.leave(playerId);
                    boolean noHuman = room.ludo.players.keySet().stream().noneMatch(id -> !id.startsWith("bot_"));
                    if (noHuman || room.ludo.started) room.ludo = new LudoState();
                    broadcast(tableId, ludoEvent(room.ludo));
                }

                if (room.chkobba.players.containsKey(playerId)) {
                    room.chkobba.leave(playerId);
                    boolean noHuman = room.chkobba.players.keySet().stream().noneMatch(id -> !id.startsWith("bot_"));
                    if (noHuman || room.chkobba.started) room.chkobba = new ChkobbaState();
                    broadcastChkobba(tableId, room.chkobba);
                }

                if (room.rami.players.containsKey(playerId)) {
                    room.rami.leave(playerId);
                    boolean noHuman = room.rami.players.keySet().stream().noneMatch(id -> !id.startsWith("bot_"));
                    if (noHuman || room.rami.started) room.rami = new RamiState();
                    broadcastRami(tableId, room.rami);
                }

                if (room.isEmpty()) iterator.remove();
            }
        }
    }

    @MessageMapping("/table/{tableId}/game/roulette/join")
    public void joinRoulette(@DestinationVariable String tableId, RouletteJoinRequest request) {
        synchronized (rooms) {
            TableGameRoom room = room(tableId);
            String name = cleanName(request.getName());
            if (name == null) return;
            room.roulettePlayers.put(request.getPlayerId(), name);
            broadcast(tableId, roulettePlayersEvent(room));
        }
    }

    @MessageMapping("/table/{tableId}/game/roulette/leave")
    public void leaveRoulette(@DestinationVariable String tableId, RouletteJoinRequest request) {
        synchronized (rooms) {
            TableGameRoom room = room(tableId);
            room.roulettePlayers.remove(request.getPlayerId());
            broadcast(tableId, roulettePlayersEvent(room));
        }
    }

    @MessageMapping("/table/{tableId}/game/roulette/start")
    public void startRoulette(@DestinationVariable String tableId) {
        synchronized (rooms) {
            TableGameRoom room = room(tableId);
            if (room.roulettePlayers.isEmpty()) return;
            List<String> players = new ArrayList<>(room.roulettePlayers.values());
            String loser = players.get((int) (Math.random() * players.size()));
            GameEvent event = GameEvent.rouletteSpin(loser, System.currentTimeMillis() + 2800);
            broadcast(tableId, event);
        }
    }

    @MessageMapping("/table/{tableId}/game/connect-four/join")
    public void joinConnectFour(@DestinationVariable String tableId, ConnectFourJoinRequest request) {
        synchronized (rooms) {
            TableGameRoom room = room(tableId);
            String name = cleanName(request.getName());
            if (name == null) return;
            room.connectFour.join(request.getPlayerId(), name);
            broadcast(tableId, connectFourEvent(room.connectFour));
        }
    }

    @MessageMapping("/table/{tableId}/game/connect-four/leave")
    public void leaveConnectFour(@DestinationVariable String tableId, ConnectFourJoinRequest request) {
        synchronized (rooms) {
            TableGameRoom room = room(tableId);
            room.connectFour.leave(request.getPlayerId());
            broadcast(tableId, connectFourEvent(room.connectFour));
        }
    }

    @MessageMapping("/table/{tableId}/game/connect-four/move")
    public void connectFourMove(@DestinationVariable String tableId, ConnectFourMoveRequest request) {
        synchronized (rooms) {
            TableGameRoom room = room(tableId);
            if (room.connectFour.play(request.getPlayerId(), request.getColumn())) {
                broadcast(tableId, connectFourEvent(room.connectFour));
            }
        }
    }

    @MessageMapping("/table/{tableId}/game/connect-four/replay")
    public void replayConnectFour(@DestinationVariable String tableId) {
        synchronized (rooms) {
            TableGameRoom room = room(tableId);
            room.connectFour.reset();
            broadcast(tableId, connectFourEvent(room.connectFour));
        }
    }

    @MessageMapping("/table/{tableId}/game/uno/join")
    public void joinUno(@DestinationVariable String tableId, RouletteJoinRequest request) { synchronized (rooms) { String name=cleanName(request.getName()); if(name==null)return; TableGameRoom room=room(tableId); room.uno.join(request.getPlayerId(),name); broadcastUno(tableId, room.uno); } }
    @MessageMapping("/table/{tableId}/game/uno/leave")
    public void leaveUno(@DestinationVariable String tableId, PartyRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(request!=null) room.uno.leave(request.getPlayerId()); boolean noHuman = room.uno.players.keySet().stream().noneMatch(id -> !id.startsWith("bot_")); if (noHuman || room.uno.started) { room.uno = new UnoState(); } broadcastUno(tableId, room.uno); } }
    @MessageMapping("/table/{tableId}/game/uno/start")
    public void startUno(@DestinationVariable String tableId) { synchronized (rooms) { TableGameRoom room=room(tableId); room.uno.start(); broadcastUno(tableId,room.uno); } }
    @MessageMapping("/table/{tableId}/game/uno/play")
    public void playUno(@DestinationVariable String tableId, UnoPlayRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(room.uno.play(request.getPlayerId(),request.getCard(),request.getColor())) broadcastUno(tableId,room.uno); } }
    @MessageMapping("/table/{tableId}/game/uno/draw")
    public void drawUno(@DestinationVariable String tableId, UnoDrawRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(room.uno.drawCard(request.getPlayerId())) broadcastUno(tableId,room.uno); } }

    @MessageMapping("/table/{tableId}/game/party/join")
    public void joinParty(@DestinationVariable String tableId, PartyRequest request) { synchronized (rooms) { String name=cleanName(request.getName()); if(name==null)return; TableGameRoom room=room(tableId); room.party.join(request.getMode(),request.getPlayerId(),name); broadcast(tableId,partyEvent(room.party)); } }
    @MessageMapping("/table/{tableId}/game/party/leave")
    public void leaveParty(@DestinationVariable String tableId, PartyRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(request!=null) room.party.leave(request.getPlayerId()); boolean noHuman = room.party.players.keySet().stream().noneMatch(id -> !id.startsWith("bot_")); if (noHuman || room.party.started) { room.party = new AdvancedPartyState(); } broadcast(tableId,partyEvent(room.party)); } }
    @MessageMapping("/table/{tableId}/game/party/start")
    public void startParty(@DestinationVariable String tableId, PartyRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); room.party.start(request.getMode(), request.getTheme(), partyQuestionService); broadcast(tableId,partyEvent(room.party)); } }
    @MessageMapping("/table/{tableId}/game/party/choice")
    public void choiceParty(@DestinationVariable String tableId, PartyRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(room.party.chooseChoice(request.getPlayerId(), request.getChoice(), partyQuestionService)) broadcast(tableId,partyEvent(room.party)); } }
    @MessageMapping("/table/{tableId}/game/party/next")
    public void nextParty(@DestinationVariable String tableId, PartyRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); room.party.next(request.getPlayerId(), partyQuestionService); broadcast(tableId,partyEvent(room.party)); } }
    @MessageMapping("/table/{tableId}/game/party/reveal")
    public void revealParty(@DestinationVariable String tableId, PartyRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(room.party.reveal(request.getPlayerId())) broadcast(tableId,partyEvent(room.party)); } }
    @MessageMapping("/table/{tableId}/game/party/reset")
    public void resetParty(@DestinationVariable String tableId) { synchronized (rooms) { TableGameRoom room=room(tableId); room.party.reset(); broadcast(tableId,partyEvent(room.party)); } }

    @MessageMapping("/table/{tableId}/game/ludo/join")
    public void joinLudo(@DestinationVariable String tableId, RouletteJoinRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); String name=cleanName(request.getName()); if(name!=null && request.getPlayerId()!=null) room.ludo.join(request.getPlayerId(),name); broadcast(tableId,ludoEvent(room.ludo)); } }
    @MessageMapping("/table/{tableId}/game/ludo/leave")
    public void leaveLudo(@DestinationVariable String tableId, RouletteJoinRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(request!=null) room.ludo.leave(request.getPlayerId()); boolean noHuman = room.ludo.players.keySet().stream().noneMatch(id -> !id.startsWith("bot_")); if (noHuman || room.ludo.started) { room.ludo = new LudoState(); } broadcast(tableId,ludoEvent(room.ludo)); } }
    @MessageMapping("/table/{tableId}/game/ludo/start")
    public void startLudo(@DestinationVariable String tableId, LudoStartRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); room.ludo.start(request != null && Boolean.TRUE.equals(request.getBotEnabled())); broadcast(tableId,ludoEvent(room.ludo)); playBotLudo(tableId, room.ludo); } }
    @MessageMapping("/table/{tableId}/game/ludo/roll")
    public void rollLudo(@DestinationVariable String tableId, UnoDrawRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(room.ludo.roll(request.getPlayerId())) { broadcast(tableId,ludoEvent(room.ludo)); playBotLudo(tableId, room.ludo); } } }
    @MessageMapping("/table/{tableId}/game/ludo/move")
    public void moveLudo(@DestinationVariable String tableId, LudoMoveRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(room.ludo.move(request.getPlayerId(),request.getTokenIndex())) { broadcast(tableId,ludoEvent(room.ludo)); playBotLudo(tableId, room.ludo); } } }
    @MessageMapping("/table/{tableId}/game/ludo/pass")
    public void passLudo(@DestinationVariable String tableId, UnoDrawRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(room.ludo.passNoMove(request.getPlayerId())) { broadcast(tableId,ludoEvent(room.ludo)); playBotLudo(tableId, room.ludo); } } }

    @MessageMapping("/table/{tableId}/game/chkobba/join")
    public void joinChkobba(@DestinationVariable String tableId, RouletteJoinRequest request) { synchronized (rooms) { String name=cleanName(request.getName());if(name==null)return;TableGameRoom room=room(tableId);room.chkobba.join(request.getPlayerId(),name);broadcastChkobba(tableId,room.chkobba); } }
    @MessageMapping("/table/{tableId}/game/chkobba/leave")
    public void leaveChkobba(@DestinationVariable String tableId, RouletteJoinRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(request!=null) room.chkobba.leave(request.getPlayerId()); boolean noHuman = room.chkobba.players.keySet().stream().noneMatch(id -> !id.startsWith("bot_")); if (noHuman || room.chkobba.started) { room.chkobba = new ChkobbaState(); } broadcastChkobba(tableId,room.chkobba); } }
    @MessageMapping("/table/{tableId}/game/chkobba/start")
    public void startChkobba(@DestinationVariable String tableId, ChkobbaStartRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId);room.chkobba.start(request == null ? null : request.getTargetScore(), request != null && Boolean.TRUE.equals(request.getBotEnabled()), request != null && Boolean.TRUE.equals(request.getTeamMode()), request == null ? null : request.getBotPartnerId());broadcastChkobba(tableId,room.chkobba);playBotTurns(tableId,room.chkobba); } }
    @MessageMapping("/table/{tableId}/game/chkobba/play")
    public void playChkobba(@DestinationVariable String tableId, ChkobbaPlayRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId);if(room.chkobba.play(request.getPlayerId(),request.getCardId(),request.getCaptureIds())) { broadcastChkobba(tableId,room.chkobba);playBotTurns(tableId,room.chkobba); } } }
    @MessageMapping("/table/{tableId}/game/chkobba/replay")
    public void replayChkobba(@DestinationVariable String tableId, ChkobbaStartRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId);room.chkobba.start(request == null ? null : request.getTargetScore(), request != null && Boolean.TRUE.equals(request.getBotEnabled()), request != null && Boolean.TRUE.equals(request.getTeamMode()), request == null ? null : request.getBotPartnerId());broadcastChkobba(tableId,room.chkobba);playBotTurns(tableId,room.chkobba); } }

    @MessageMapping("/table/{tableId}/game/rami/join")
    public void joinRami(@DestinationVariable String tableId, RouletteJoinRequest request) { synchronized (rooms) { String name=cleanName(request.getName());if(name==null)return;TableGameRoom room=room(tableId);room.rami.join(request.getPlayerId(),name);broadcastRami(tableId,room.rami); } }
    @MessageMapping("/table/{tableId}/game/rami/leave")
    public void leaveRami(@DestinationVariable String tableId, RouletteJoinRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(request!=null) room.rami.leave(request.getPlayerId()); boolean noHuman = room.rami.players.keySet().stream().noneMatch(id -> !id.startsWith("bot_")); if (noHuman || room.rami.started) { room.rami = new RamiState(); } broadcastRami(tableId,room.rami); } }
    @MessageMapping("/table/{tableId}/game/rami/start")
    public void startRami(@DestinationVariable String tableId, RamiStartRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId);room.rami.start(request != null && Boolean.TRUE.equals(request.getBotEnabled()), request == null ? null : request.getMinMeldScore());broadcastRami(tableId,room.rami);playBotRami(tableId,room.rami); } }
    @MessageMapping("/table/{tableId}/game/rami/draw")
    public void drawRami(@DestinationVariable String tableId, RamiDrawRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId);if(room.rami.draw(request.getPlayerId(),request.getSource())){broadcastRami(tableId,room.rami);playBotRami(tableId,room.rami);} } }
    @MessageMapping("/table/{tableId}/game/rami/lay")
    public void layRami(@DestinationVariable String tableId, RamiCardsRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId);if(room.rami.lay(request.getPlayerId(),request.getCardIds())){broadcastRami(tableId,room.rami);playBotRami(tableId,room.rami);} } }
    @MessageMapping("/table/{tableId}/game/rami/discard")
    public void discardRami(@DestinationVariable String tableId, RamiCardRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId);if(room.rami.discard(request.getPlayerId(),request.getCardId())){broadcastRami(tableId,room.rami);playBotRami(tableId,room.rami);} } }
    @MessageMapping("/table/{tableId}/game/rami/replace-joker")
    public void replaceRamiJoker(@DestinationVariable String tableId, RamiJokerRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId);if(request != null && room.rami.replaceJoker(request.getPlayerId(), request.getMeldIndex(), request.getJokerId(), request.getReplacementCardId())) broadcastRami(tableId,room.rami); } }
    @MessageMapping("/table/{tableId}/game/rami/extend-meld")
    public void extendRamiMeld(@DestinationVariable String tableId, RamiExtendRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId);if(request != null && room.rami.extendMeld(request.getPlayerId(), request.getMeldIndex(), request.getCardIds())) broadcastRami(tableId,room.rami); } }
    @MessageMapping("/table/{tableId}/game/rami/replay")
    public void replayRami(@DestinationVariable String tableId, RamiStartRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId);room.rami.start(request != null && Boolean.TRUE.equals(request.getBotEnabled()), request == null ? null : request.getMinMeldScore());broadcastRami(tableId,room.rami);playBotRami(tableId,room.rami); } }

    private TableGameRoom room(String tableId) {
        return rooms.computeIfAbsent(tableId, ignored -> new TableGameRoom());
    }

    private void broadcast(String tableId, GameEvent event) {
        messagingTemplate.convertAndSend("/topic/table/" + tableId + "/game", event);
    }

    private GameEvent roulettePlayersEvent(TableGameRoom room) {
        GameEvent event = new GameEvent();
        event.setType("roulette_players");
        event.setPlayers(new ArrayList<>(room.roulettePlayers.values()));
        return event;
    }

    private GameEvent connectFourEvent(ConnectFourState state) {
        GameEvent event = new GameEvent();
        event.setType("connect_four_state");
        event.setBoard(state.board);
        event.setRedPlayer(state.redPlayer);
        event.setYellowPlayer(state.yellowPlayer);
        event.setTurn(state.turn);
        event.setWinner(state.winner);
        event.setDraw(state.draw);
        return event;
    }
    private void broadcastUno(String tableId, UnoState state) { broadcast(tableId, unoEvent(state)); state.hands.forEach((playerId, hand) -> { GameEvent handEvent=new GameEvent(); handEvent.setType("uno_hand"); handEvent.setUnoHand(hand); messagingTemplate.convertAndSend("/topic/table/"+tableId+"/game/uno/hand/"+playerId, handEvent); }); }
    private GameEvent unoEvent(UnoState state) { GameEvent event=new GameEvent(); event.setType("uno_state"); event.setUnoPlayers(new ArrayList<>(state.players.values())); event.setUnoHandCounts(state.hands.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size()))); event.setUnoTopCard(state.topCard); event.setUnoActiveColor(state.activeColor()); event.setUnoTurnId(state.turnId); event.setUnoWinner(state.winner); event.setUnoStarted(state.started); return event; }
    private GameEvent partyEvent(AdvancedPartyState state) { GameEvent event=new GameEvent(); event.setType("party_state"); event.setPartyMode(state.mode); event.setPartyPlayers(new ArrayList<>(state.players.values())); event.setPartyTurnId(state.turnId); event.setPartyPrompt(state.prompt()); event.setPartyAnswer(state.answer()); event.setPartyDiscussion(state.discussion()); event.setPartyRevealed(state.revealed); event.setPartyStarted(state.started); return event; }
    private GameEvent ludoEvent(LudoState state) { GameEvent event=new GameEvent();event.setType("ludo_state");event.setLudoPlayers(new ArrayList<>(state.players.values()));event.setLudoTokens(state.tokens);event.setLudoTurnId(state.turnId);event.setLudoWinner(state.winner);event.setLudoDice(state.dice);event.setLudoStarted(state.started);event.setLudoCanRoll(state.canRoll);return event; }
    private void broadcastChkobba(String tableId, ChkobbaState state) {
        broadcast(tableId, chkobbaEvent(state));
        state.hands.forEach((playerId, hand) -> {
            GameEvent handEvent = new GameEvent();
            handEvent.setType("chkobba_hand");
            handEvent.setChkobbaHand(new ArrayList<>(hand));
            messagingTemplate.convertAndSend("/topic/table/" + tableId + "/game/chkobba/hand/" + playerId, handEvent);
        });
    }
    private void playBotTurns(String tableId, ChkobbaState state) {
        if (!state.isBotTurn()) return;
        botScheduler.schedule(() -> {
            synchronized (rooms) {
                if (state.isBotTurn() && state.playBotTurn()) {
                    broadcastChkobba(tableId, state);
                    if (state.isBotTurn()) {
                        playBotTurns(tableId, state);
                    }
                }
            }
        }, 1600, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    private void broadcastRami(String tableId, RamiState state) {
        broadcast(tableId, ramiEvent(state));
        state.hands.forEach((playerId, hand) -> {
            GameEvent handEvent = new GameEvent();
            handEvent.setType("rami_hand");
            handEvent.setRamiHand(new ArrayList<>(hand));
            messagingTemplate.convertAndSend("/topic/table/" + tableId + "/game/rami/hand/" + playerId, handEvent);
        });
    }
    private final java.util.concurrent.ScheduledExecutorService botScheduler = java.util.concurrent.Executors.newScheduledThreadPool(2);

    private void playBotRami(String tableId, RamiState state) {
        if (!state.isBotTurn()) return;
        botScheduler.schedule(() -> {
            synchronized (rooms) {
                if (state.isBotTurn() && state.playBotTurn()) {
                    broadcastRami(tableId, state);
                    if (state.isBotTurn()) {
                        playBotRami(tableId, state);
                    }
                }
            }
        }, 850, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void playBotLudo(String tableId, LudoState state) {
        if (!state.isBotTurn()) return;
        botScheduler.schedule(() -> {
            synchronized (rooms) {
                if (state.isBotTurn() && state.playBotTurn()) {
                    broadcast(tableId, ludoEvent(state));
                    if (state.isBotTurn()) {
                        playBotLudo(tableId, state);
                    }
                }
            }
        }, 1100, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    private GameEvent ramiEvent(RamiState state) {
        GameEvent event = new GameEvent();
        event.setType("rami_state");
        event.setRamiPlayers(new ArrayList<>(state.players.values()));
        event.setRamiMelds(new ArrayList<>(state.melds));
        event.setRamiDiscardTop(state.discardTop());
        event.setRamiTurnId(state.turnId);
        event.setRamiWinner(state.winner);
        event.setRamiStarted(state.started);
        event.setRamiHasDrawn(state.hasDrawn);
        event.setRamiBotEnabled(state.botEnabled);
        event.setRamiDeckRemaining(state.drawPile.size());
        event.setRamiRound(state.round);
        event.setRamiScores(new LinkedHashMap<>(state.scores));
        event.setRamiMinMeldScore(state.minMeldScore);
        event.setRamiPlayerHasLaid(new LinkedHashMap<>(state.playerHasLaid));
        event.setRamiJokerReplacements(new LinkedHashMap<>(state.jokerReplacements));
        event.setRamiHandCounts(state.hands.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size(), (left, right) -> right, LinkedHashMap::new)));
        return event;
    }
    private GameEvent chkobbaEvent(ChkobbaState state) {
        GameEvent event = new GameEvent();
        event.setType("chkobba_state");
        event.setChkobbaPlayers(new ArrayList<>(state.players.values()));
        event.setChkobbaTable(new ArrayList<>(state.table));
        event.setChkobbaTurnId(state.turnId);
        event.setChkobbaWinner(state.winner);
        event.setChkobbaStarted(state.started);
        event.setChkobbaTargetScore(state.targetScore);
        event.setChkobbaBotEnabled(state.botEnabled);
        event.setChkobbaTeamMode(state.teamMode);
        event.setChkobbaTeamByPlayerId(new LinkedHashMap<>(state.teamByPlayerId));
        event.setChkobbaTeamScores(new LinkedHashMap<>(state.teamScores));
        event.setChkobbaWinnerTeam(state.winnerTeam);
        event.setChkobbaBotPartnerId(state.botPartnerId);
        event.setChkobbaScores(new LinkedHashMap<>(state.scores));
        event.setChkobbaCapturedCounts(state.captured.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size(), (left, right) -> right, LinkedHashMap::new)));
        event.setChkobbaScopaCounts(new LinkedHashMap<>(state.scopaCounts));
        event.setChkobbaRoundScores(new LinkedHashMap<>(state.roundScores));
        event.setChkobbaLastRoundScores(new LinkedHashMap<>(state.lastRoundScores));
        event.setChkobbaLastRoundWinner(state.lastRoundWinner);
        event.setChkobbaLastMovePlayerId(state.lastMovePlayerId);
        event.setChkobbaLastMoveCard(state.lastMoveCard);
        event.setChkobbaLastCaptureCount(state.lastCaptureCount);
        event.setChkobbaLastScopa(state.lastScopa);
        event.setChkobbaDeckRemaining(state.deckRemaining);
        event.setChkobbaRound(state.round);
        event.setChkobbaDealNumber(state.dealNumber);
        return event;
    }

    private String cleanName(String name) {
        if (name == null) return null;
        String cleaned = name.trim();
        return cleaned.isBlank() ? null : cleaned.substring(0, Math.min(cleaned.length(), 32));
    }

    @Data
    public static class RouletteJoinRequest { private String playerId; private String name; }
    @Data
    public static class ConnectFourJoinRequest { private String playerId; private String name; }
    @Data
    public static class ConnectFourMoveRequest { private String playerId; private Integer column; }
    @Data public static class UnoPlayRequest { private String playerId; private String card; private String color; }
    @Data public static class UnoDrawRequest { private String playerId; }
    @Data public static class PartyRequest { private String playerId; private String name; private String mode; private String theme; private String choice; }
    @Data public static class LudoStartRequest { private Boolean botEnabled; }
    @Data public static class LudoMoveRequest { private String playerId; private Integer tokenIndex; }
    @Data public static class ChkobbaPlayRequest { private String playerId; private String cardId; private List<String> captureIds; }
    @Data public static class ChkobbaStartRequest { private Integer targetScore; private Boolean botEnabled; private Boolean teamMode; private String botPartnerId; }
    @Data public static class RamiDrawRequest { private String playerId; private String source; }
    @Data public static class RamiStartRequest { private Boolean botEnabled; private Integer minMeldScore; }
    @Data public static class RamiCardsRequest { private String playerId; private List<String> cardIds; }
    @Data public static class RamiCardRequest { private String playerId; private String cardId; }
    @Data public static class RamiJokerRequest { private String playerId; private int meldIndex; private String jokerId; private String replacementCardId; }
    @Data public static class RamiExtendRequest { private String playerId; private int meldIndex; private List<String> cardIds; }
    @Data
    public static class GameEvent {
        private String type;
        private List<String> players;
        private String loser;
        private Long startsAt;
        private int[][] board;
        private Player redPlayer;
        private Player yellowPlayer;
        private String turn;
        private String winner;
        private boolean draw;
        private List<Player> unoPlayers; private List<String> unoHand; private Map<String,Integer> unoHandCounts; private String unoTopCard; private String unoActiveColor; private String unoTurnId; private String unoWinner; private boolean unoStarted;
        private List<Player> partyPlayers; private String partyMode; private String partyTheme; private String partyChoice; private String partyTurnId; private String partyPrompt; private String partyAnswer; private String partyDiscussion; private boolean partyRevealed; private boolean partyStarted;
        private List<Player> ludoPlayers; private Map<String,int[]> ludoTokens; private String ludoTurnId; private String ludoWinner; private Integer ludoDice; private boolean ludoStarted; private boolean ludoCanRoll;
        private List<Player> chkobbaPlayers; private List<ChkobbaState.Card> chkobbaTable; private List<ChkobbaState.Card> chkobbaHand; private String chkobbaTurnId; private String chkobbaWinner; private boolean chkobbaStarted; private int chkobbaTargetScore; private boolean chkobbaBotEnabled; private boolean chkobbaTeamMode; private Map<String,String> chkobbaTeamByPlayerId; private Map<String,Integer> chkobbaTeamScores; private String chkobbaWinnerTeam; private String chkobbaBotPartnerId; private Map<String,Integer> chkobbaScores; private Map<String,Integer> chkobbaCapturedCounts; private Map<String,Integer> chkobbaScopaCounts; private Map<String,Integer> chkobbaRoundScores; private Map<String,Integer> chkobbaLastRoundScores; private String chkobbaLastRoundWinner; private String chkobbaLastMovePlayerId; private ChkobbaState.Card chkobbaLastMoveCard; private int chkobbaLastCaptureCount; private boolean chkobbaLastScopa; private int chkobbaDeckRemaining; private int chkobbaRound; private int chkobbaDealNumber;
        private List<Player> ramiPlayers; private List<RamiMeld> ramiMelds; private ChkobbaState.Card ramiDiscardTop; private List<ChkobbaState.Card> ramiHand; private String ramiTurnId; private String ramiWinner; private boolean ramiStarted; private boolean ramiHasDrawn; private boolean ramiBotEnabled; private int ramiDeckRemaining; private int ramiRound; private Map<String,Integer> ramiScores; private Map<String,Integer> ramiHandCounts; private Integer ramiMinMeldScore; private Map<String,Boolean> ramiPlayerHasLaid; private Map<String, RamiState.JokerReplacement> ramiJokerReplacements;

        static GameEvent rouletteSpin(String loser, long startsAt) {
            GameEvent event = new GameEvent();
            event.type = "roulette_spin";
            event.loser = loser;
            event.startsAt = startsAt;
            return event;
        }
    }

    @Data
    public static class Player { private final String id; private final String name; }

    @Data
    public static class RamiMeld {
        private String type;
        private List<ChkobbaState.Card> cards;

        public RamiMeld() {}

        public RamiMeld(String type, List<ChkobbaState.Card> cards) {
            this.type = type;
            this.cards = new ArrayList<>(cards);
        }
    }

    private static class TableGameRoom {
        final Map<String, String> roulettePlayers = new LinkedHashMap<>();
        ConnectFourState connectFour = new ConnectFourState();
        UnoState uno = new UnoState();
        AdvancedPartyState party = new AdvancedPartyState();
        LudoState ludo = new LudoState();
        ChkobbaState chkobba = new ChkobbaState();
        RamiState rami = new RamiState();

        boolean isEmpty() {
            return roulettePlayers.isEmpty()
                    && connectFour.redPlayer == null && connectFour.yellowPlayer == null
                    && uno.players.isEmpty() && party.players.isEmpty() && ludo.players.isEmpty()
                    && chkobba.players.isEmpty() && rami.players.isEmpty();
        }
    }

    private record QuizCard(String question, String answer, String discussion) {}

    private static class AdvancedPartyState {
        final LinkedHashMap<String, Player> players = new LinkedHashMap<>();
        String mode = "quiz";
        String theme = "intimate";
        String turnId;
        String currentChoice;
        boolean started;
        boolean revealed;

        String currentPrompt;
        String currentAnswer;
        String currentDiscussion;

        void join(String requestedMode, String id, String name) {
            if (requestedMode != null && !requestedMode.isBlank()) {
                this.mode = requestedMode;
            }
            if (id != null && !id.isBlank() && name != null && !name.isBlank()) {
                players.put(id, new Player(id, name));
            }
        }

        void leave(String id) {
            if (id != null) players.remove(id);
            if (players.isEmpty()) {
                reset();
            } else if (Objects.equals(turnId, id)) {
                turnId = players.keySet().iterator().next();
                revealed = false;
                currentChoice = null;
                currentPrompt = null;
                currentAnswer = null;
                currentDiscussion = null;
            }
        }

        void reset() {
            started = false;
            revealed = false;
            currentChoice = null;
            turnId = null;
            currentPrompt = null;
            currentAnswer = null;
            currentDiscussion = null;
        }

        void start(String requestedMode, String requestedTheme, IPartyQuestionService questionService) {
            if (requestedMode != null && !requestedMode.isBlank()) this.mode = requestedMode;
            if (requestedTheme != null && !requestedTheme.isBlank()) this.theme = requestedTheme;
            if (players.isEmpty()) return;

            started = true;
            revealed = false;
            currentChoice = null;
            turnId = players.keySet().iterator().next();
            fetchQuestion(questionService);
        }

        boolean chooseChoice(String id, String choice, IPartyQuestionService questionService) {
            if (!started || !"truth".equals(mode) || !Objects.equals(turnId, id)) return false;
            this.currentChoice = choice;
            fetchQuestion(questionService);
            return true;
        }

        boolean reveal(String id) {
            if (!started || !"quiz".equals(mode) || !Objects.equals(turnId, id)) return false;
            revealed = true;
            return true;
        }

        void next(String id, IPartyQuestionService questionService) {
            if (!started || players.isEmpty()) return;
            List<String> ids = new ArrayList<>(players.keySet());
            int currIdx = ids.indexOf(turnId);
            if (currIdx == -1) currIdx = 0;
            turnId = ids.get((currIdx + 1) % ids.size());
            revealed = false;
            currentChoice = null;
            currentPrompt = null;
            currentAnswer = null;
            currentDiscussion = null;
            fetchQuestion(questionService);
        }

        void fetchQuestion(IPartyQuestionService questionService) {
            if (!started || questionService == null) return;
            if ("quiz".equals(mode)) {
                var q = questionService.getRandomQuizQuestion();
                this.currentPrompt = q.getPrompt();
                this.currentAnswer = q.getAnswer();
                this.currentDiscussion = q.getDiscussion();
            } else {
                if (currentChoice == null) {
                    this.currentPrompt = "👉 Choix requis : VÉRITÉ 💬 ou ACTION ⚡ ?";
                    this.currentAnswer = null;
                    this.currentDiscussion = null;
                } else if ("truth".equals(currentChoice)) {
                    var q = questionService.getRandomTruth(theme);
                    this.currentPrompt = q.getPrompt();
                    this.currentAnswer = null;
                    this.currentDiscussion = null;
                } else {
                    var q = questionService.getRandomAction(theme);
                    this.currentPrompt = q.getPrompt();
                    this.currentAnswer = null;
                    this.currentDiscussion = null;
                }
            }
        }

        String prompt() {
            return started ? currentPrompt : null;
        }

        String answer() {
            return started && revealed && "quiz".equals(mode) ? currentAnswer : null;
        }

        String discussion() {
            return started && revealed && "quiz".equals(mode) ? currentDiscussion : null;
        }
    }

    private static class ConnectFourState {
        int[][] board = new int[ROWS][COLUMNS];
        Player redPlayer;
        Player yellowPlayer;
        String turn = "RED";
        String winner;
        boolean draw;

        void join(String playerId, String name) {
            if (redPlayer != null && redPlayer.id.equals(playerId)) { redPlayer = new Player(playerId, name); return; }
            if (yellowPlayer != null && yellowPlayer.id.equals(playerId)) { yellowPlayer = new Player(playerId, name); return; }
            if (redPlayer == null) redPlayer = new Player(playerId, name);
            else if (yellowPlayer == null) yellowPlayer = new Player(playerId, name);
        }

        void leave(String playerId) {
            if (redPlayer != null && Objects.equals(redPlayer.id, playerId)) redPlayer = null;
            if (yellowPlayer != null && Objects.equals(yellowPlayer.id, playerId)) yellowPlayer = null;
            if (redPlayer == null && yellowPlayer == null) reset();
        }

        boolean play(String playerId, Integer column) {
            if (column == null || column < 0 || column >= COLUMNS || winner != null || draw || redPlayer == null || yellowPlayer == null) return false;
            Player active = "RED".equals(turn) ? redPlayer : yellowPlayer;
            if (!active.id.equals(playerId)) return false;
            for (int row = ROWS - 1; row >= 0; row--) {
                if (board[row][column] == 0) {
                    board[row][column] = "RED".equals(turn) ? 1 : 2;
                    if (hasFour(row, column)) winner = turn;
                    else if (Arrays.stream(board).flatMapToInt(Arrays::stream).noneMatch(cell -> cell == 0)) draw = true;
                    else turn = "RED".equals(turn) ? "YELLOW" : "RED";
                    return true;
                }
            }
            return false;
        }

        void reset() { board = new int[ROWS][COLUMNS]; winner = null; draw = false; turn = "RED"; }

        private boolean hasFour(int row, int column) {
            int token = board[row][column];
            return count(row, column, 0, 1, token) + count(row, column, 0, -1, token) >= 3
                    || count(row, column, 1, 0, token) + count(row, column, -1, 0, token) >= 3
                    || count(row, column, 1, 1, token) + count(row, column, -1, -1, token) >= 3
                    || count(row, column, 1, -1, token) + count(row, column, -1, 1, token) >= 3;
        }

        private int count(int row, int col, int rowStep, int colStep, int token) {
            int matches = 0;
            for (int r = row + rowStep, c = col + colStep; r >= 0 && r < ROWS && c >= 0 && c < COLUMNS && board[r][c] == token; r += rowStep, c += colStep) matches++;
            return matches;
        }
    }
}
