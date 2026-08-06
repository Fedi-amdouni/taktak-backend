package com.taktak.controller;

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
    private final com.taktak.service.PartyQuestionService partyQuestionService;
    private final Map<String, TableGameRoom> rooms = new LinkedHashMap<>();

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

        RamiMeld(String type, List<ChkobbaState.Card> cards) {
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

        void start(String requestedMode, String requestedTheme, com.taktak.service.PartyQuestionService questionService) {
            if (requestedMode != null && !requestedMode.isBlank()) this.mode = requestedMode;
            if (requestedTheme != null && !requestedTheme.isBlank()) this.theme = requestedTheme;
            if (players.isEmpty()) return;

            started = true;
            revealed = false;
            currentChoice = null;
            turnId = players.keySet().iterator().next();
            fetchQuestion(questionService);
        }

        boolean chooseChoice(String id, String choice, com.taktak.service.PartyQuestionService questionService) {
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

        void next(String id, com.taktak.service.PartyQuestionService questionService) {
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

        void fetchQuestion(com.taktak.service.PartyQuestionService questionService) {
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

    private static class PartyState {
        final LinkedHashMap<String, Player> players = new LinkedHashMap<>(); String mode="quiz"; String turnId; int index; boolean started;
        final Map<String,List<String>> prompts=Map.of(
                "quiz",List.of(
                        "Chnowa akber kawkab fi système solaire ?",
                        "9addech men continent fama fel 3alem ?",
                        "Chnowa esm l 3asma mta3 l Japon ?",
                        "Chkoun reb7 Coupe du Monde 2022 ?",
                        "9addech men joueur yal3bou fi équipe foot wa7da fel terrain ?",
                        "Chnowa l club ettounsi elli yetla9ab b Taraji Dawla ?",
                        "Chkoun akther joueur reb7 Ballon d'Or ?",
                        "Fi anehou bled tsaret awel Coupe du Monde ?",
                        "Chnowa esm l b7ar elli bin Tounes w Italia ?",
                        "Chnowa a9reb kawkab lel chams ?",
                        "9addech men 3adhma fi jism l insan l kebir ?",
                        "Chnowa l 7ayawen elli ynajem ybaddel lounou ?",
                        "Ken 3andek 3 tfe7at w klit wa7da, 9addech yab9awlek ?",
                        "Chnowa akber mo7it fel 3alem ?",
                        "Chkoun kteb Riwayet Les Misérables ?",
                        "Chnowa esm l instrument elli fih 88 touche ?",
                        "Anehou film fih personnage Jack w Rose ?",
                        "Chnowa l logha l akther mosta3mla fel 3alem ?",
                        "9addech men youm fi 3am kabiss ?",
                        "Chnowa l wilaya ettounsia elli ma3roufa b amphithéâtre El Jem ?",
                        "Chkoun awel insan mcha lel 9amar ?",
                        "Chnowa l gaz elli netnafsouh bech n3ichou ?",
                        "Anehou ath9el: 1 kilo 7did walla 1 kilo coton ?",
                        "Chnowa esm l monnaie mta3 l Japon ?"
                ),
                "truth",List.of(
                        "Sra7a: chnowa akber kedhba 9oltha l s7abek ?",
                        "Action: 9alled wa7ed mel groupe 20 secondes w houma y7awlou ya3rfouh.",
                        "Sra7a: chkoun akher personne stalkitha fel réseaux sociaux ?",
                        "Action: ghanni refrain mta3 ghneya t7ebha b sout 3ali.",
                        "Sra7a: chnowa akther maw9ef 7achem sarlik ?",
                        "Action: e7ki b accent ekher lel tour jey.",
                        "Sra7a: ken tnajem tbaddel 7aja wa7da fi ro7ek, chnowa heya ?",
                        "Action: a3mel pub improvisée l ay 7aja 9oddemek.",
                        "Sra7a: chkoun mel groupe ken awel impression mte3ek 3lih ghalta ?",
                        "Action: ab3ath vocal t9oul fih nokta l wa7ed men s7abek.",
                        "Sra7a: chnowa akther décision nedemt 3liha ?",
                        "Action: warri akher photo fel galerie mte3ek, ken ma famech 7aja privée.",
                        "Sra7a: ken terba7 milliard, chnowa awel 7aja techriha ?",
                        "Action: a3mel 10 secondes danse bla musique.",
                        "Sra7a: chnowa l 3ada l khayba elli t7eb tna7iha ?",
                        "Action: 9oul 3 qualités fi joueur ekher yekhtarouh l groupe.",
                        "Sra7a: chkoun celebrity t7eb ta3mel m3ah dîner ?",
                        "Action: khalli joueur ekher yekhtarlek photo de profil moddet 5 d9aye9.",
                        "Sra7a: chnowa akther 7aja tkhawfek fel mosta9bel ?",
                        "Action: a7ki nokta; ken 7add ma dha7ek, 3awed action okhra.",
                        "Sra7a: chnowa secret sghir ma ya3rfouhouch 3lik barcha ?",
                        "Action: semmi 5 aghani fi 15 secondes.",
                        "Sra7a: ken tnajem tsefer taw, win temchi w m3a chkoun ?",
                        "Action: mathel scène men film w khalli l groupe y5amem chnowa."
                ),
                "words",List.of("Trouve 5 villes tunisiennes en 15 secondes.","Fais deviner « brik » sans dire manger, œuf ou feuille.","Cite 6 choses qu’on trouve dans un café tunisien.","Fais deviner « Sidi Bou Saïd » sans dire bleu, blanc ou Tunis.","Trouve 5 mots tunisiens qui commencent par M."));
        void join(String requestedMode,String id,String name){if(requestedMode!=null&&!started&&prompts.containsKey(requestedMode)&&!requestedMode.equals(mode)){mode=requestedMode;players.clear();}if(players.containsKey(id))players.put(id,new Player(id,name));else if(!started&&players.size()<4)players.put(id,new Player(id,name));}
        void start(String requestedMode){if(requestedMode!=null&&prompts.containsKey(requestedMode))mode=requestedMode;if(players.size()<2)return;started=true;index=0;turnId=players.keySet().iterator().next();}
        void next(String id){if(!started||!java.util.Objects.equals(turnId,id))return;List<String> ids=new ArrayList<>(players.keySet());turnId=ids.get((ids.indexOf(id)+1)%ids.size());index=(index+1)%prompts.get(mode).size();}
        String prompt(){return started?prompts.get(mode).get(index%prompts.get(mode).size()):null;}
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
