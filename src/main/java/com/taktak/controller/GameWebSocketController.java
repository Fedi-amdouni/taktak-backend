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
    @MessageMapping("/table/{tableId}/game/uno/start")
    public void startUno(@DestinationVariable String tableId) { synchronized (rooms) { TableGameRoom room=room(tableId); room.uno.start(); broadcastUno(tableId,room.uno); } }
    @MessageMapping("/table/{tableId}/game/uno/play")
    public void playUno(@DestinationVariable String tableId, UnoPlayRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(room.uno.play(request.getPlayerId(),request.getCard(),request.getColor())) broadcastUno(tableId,room.uno); } }
    @MessageMapping("/table/{tableId}/game/uno/draw")
    public void drawUno(@DestinationVariable String tableId, UnoDrawRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(room.uno.drawCard(request.getPlayerId())) broadcastUno(tableId,room.uno); } }

    @MessageMapping("/table/{tableId}/game/party/join")
    public void joinParty(@DestinationVariable String tableId, PartyRequest request) { synchronized (rooms) { String name=cleanName(request.getName()); if(name==null)return; TableGameRoom room=room(tableId); room.party.join(request.getMode(),request.getPlayerId(),name); broadcast(tableId,partyEvent(room.party)); } }
    @MessageMapping("/table/{tableId}/game/party/start")
    public void startParty(@DestinationVariable String tableId, PartyRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); room.party.start(request.getMode()); broadcast(tableId,partyEvent(room.party)); } }
    @MessageMapping("/table/{tableId}/game/party/next")
    public void nextParty(@DestinationVariable String tableId, PartyRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); room.party.next(request.getPlayerId()); broadcast(tableId,partyEvent(room.party)); } }
    @MessageMapping("/table/{tableId}/game/party/reveal")
    public void revealParty(@DestinationVariable String tableId, PartyRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId); if(room.party.reveal(request.getPlayerId())) broadcast(tableId,partyEvent(room.party)); } }
    @MessageMapping("/table/{tableId}/game/ludo/join")
    public void joinLudo(@DestinationVariable String tableId, RouletteJoinRequest request) { synchronized (rooms) { String name=cleanName(request.getName());if(name==null)return;TableGameRoom room=room(tableId);room.ludo.join(request.getPlayerId(),name);broadcast(tableId,ludoEvent(room.ludo)); } }
    @MessageMapping("/table/{tableId}/game/ludo/start")
    public void startLudo(@DestinationVariable String tableId) { synchronized (rooms) { TableGameRoom room=room(tableId);room.ludo.start();broadcast(tableId,ludoEvent(room.ludo)); } }
    @MessageMapping("/table/{tableId}/game/ludo/roll")
    public void rollLudo(@DestinationVariable String tableId, UnoDrawRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId);if(room.ludo.roll(request.getPlayerId()))broadcast(tableId,ludoEvent(room.ludo)); } }
    @MessageMapping("/table/{tableId}/game/ludo/move")
    public void moveLudo(@DestinationVariable String tableId, LudoMoveRequest request) { synchronized (rooms) { TableGameRoom room=room(tableId);if(room.ludo.move(request.getPlayerId(),request.getTokenIndex()))broadcast(tableId,ludoEvent(room.ludo)); } }

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
    private GameEvent unoEvent(UnoState state) { GameEvent event=new GameEvent(); event.setType("uno_state"); event.setUnoPlayers(new ArrayList<>(state.players.values())); event.setUnoHandCounts(state.hands.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size()))); event.setUnoTopCard(state.topCard); event.setUnoTurnId(state.turnId); event.setUnoWinner(state.winner); event.setUnoStarted(state.started); return event; }
    private GameEvent partyEvent(AdvancedPartyState state) { GameEvent event=new GameEvent(); event.setType("party_state"); event.setPartyMode(state.mode); event.setPartyPlayers(new ArrayList<>(state.players.values())); event.setPartyTurnId(state.turnId); event.setPartyPrompt(state.prompt()); event.setPartyAnswer(state.answer()); event.setPartyDiscussion(state.discussion()); event.setPartyRevealed(state.revealed); event.setPartyStarted(state.started); return event; }
    private GameEvent ludoEvent(LudoState state) { GameEvent event=new GameEvent();event.setType("ludo_state");event.setLudoPlayers(new ArrayList<>(state.players.values()));event.setLudoTokens(state.tokens);event.setLudoTurnId(state.turnId);event.setLudoWinner(state.winner);event.setLudoDice(state.dice);event.setLudoStarted(state.started);event.setLudoCanRoll(state.canRoll);return event; }

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
    @Data public static class PartyRequest { private String playerId; private String name; private String mode; }
    @Data public static class LudoMoveRequest { private String playerId; private Integer tokenIndex; }
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
        private List<Player> unoPlayers; private List<String> unoHand; private Map<String,Integer> unoHandCounts; private String unoTopCard; private String unoTurnId; private String unoWinner; private boolean unoStarted;
        private List<Player> partyPlayers; private String partyMode; private String partyTurnId; private String partyPrompt; private String partyAnswer; private String partyDiscussion; private boolean partyRevealed; private boolean partyStarted;
        private List<Player> ludoPlayers; private Map<String,int[]> ludoTokens; private String ludoTurnId; private String ludoWinner; private Integer ludoDice; private boolean ludoStarted; private boolean ludoCanRoll;

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

    private static class TableGameRoom {
        final Map<String, String> roulettePlayers = new LinkedHashMap<>();
        final ConnectFourState connectFour = new ConnectFourState();
        final UnoState uno = new UnoState();
        final AdvancedPartyState party = new AdvancedPartyState();
        final LudoState ludo = new LudoState();
    }

    private record QuizCard(String question, String answer, String discussion) {}

    private static class AdvancedPartyState {
        final LinkedHashMap<String, Player> players = new LinkedHashMap<>();
        String mode="quiz"; String turnId; int index; boolean started; boolean revealed;
        final List<QuizCard> quiz=List.of(
                new QuizCard("Fi Coupe du Monde 1978, Tounes reb7et anehou équipe w wallat awel équipe 3arbiya w ifri9iya reb7et match fel Mondial ?","El Mexique, 3-1.","Est-ce que l exploit hedha mazeltou أهم men les participations l okhrin mta3 Tounes ? 3lech ?"),
                new QuizCard("Chkoun howa l gardien lwa7id fi terikh elli reb7 Ballon d'Or ?","Lev Yashin, سنة 1963.","El gardien yestahel nafs l reconnaissance kima l attaquant, walla le ?"),
                new QuizCard("Fel foot, wa9teh joueur ma ynajjemch يكون hors-jeu مباشرة ba3d reprise ? Semmi zouz حالات.","Ba3d touche, corner walla coup de pied de but.","El VAR 7assen قانون hors-jeu walla 9تل rou7 l jeu ?"),
                new QuizCard("Chnowa l ma3na التكتيكي mta3 faux numéro 9 ?","Attaquant يرجع lel milieu bech يجرّ défenseurs w يخلق espaces.","Tفضل équipe منظمة tactiquement walla équipe تلعب بحرية وإبداع ?"),
                new QuizCard("Chkoun l arba3 منظمات ettounsia elli reb7ou Nobel de la Paix 2015 ?","UGTT, UTICA, Ligue Tunisienne des Droits de l Homme, w Ordre National des Avocats.","El dialogue ينجم ديما يحل أزمة سياسية كبيرة ?"),
                new QuizCard("Chnowa l 7adث elli عادة نعتبروه الشرارة المباشرة للحرب العالمية الأولى ?","اغتيال l archiduc François-Ferdinand fi Sarajevo سنة 1914.","الحروب تبدأ بسبب حادثة واحدة walla تراكمات أعمق ?"),
                new QuizCard("Anehou sa7ra هي الأكبر fel 3alem ken نحسبو sa7ra bقلة الأمطار, moch b الرمل ?","L Antarctique.","التغير المناخي ينجم يبدل تعريف المناطق الصحراوية مستقبلا ?"),
                new QuizCard("Chnowa l kawkab elli nharou أطول men 3amou ?","Vénus: دورانو حول روحو أطول من دورانو حول الشمس.","هل استعمار كواكب أخرى حل واقعي walla هروب men مشاكل الأرض ?"),
                new QuizCard("Fi جسم الإنسان, anehou organe ينجم يعاود يبني نسبة كبيرة men ro7ou ?","El kebda, le foie.","إلى أي حد الطب يلزم يتدخل باش يطوّل عمر الإنسان ?"),
                new QuizCard("Ken ترمي قطعة نقد مرتين, chnowa احتمال تجيك face مرتين ?","1 على 4، يعني 25%.","الناس تفهم الاحتمالات مليح walla غالبا قراراتنا عاطفية ?"),
                new QuizCard("Fi problème Monty Hall: 3 بيبان، بعد ما المقدم يفتح باب خاسر، تبدل اختيارك walla تبقى ?","تبدل: فرصة الربح تولّي 2/3، مقابل 1/3 كان تبقى.","علاش مخّنا يقاوم نتيجة صحيحة كي تكون ضد الحدس ?"),
                new QuizCard("Chnowa Paradoxe du bateau de Thésée ?","Ken تبدل كل قطع سفينة وحدة بوحدة، السؤال: هل تبقى نفس السفينة walla تولّي حاجة أخرى ?","شنوة اللي يصنع هوية الإنسان: جسمو، ذكرياتو walla علاقاتو ?"),
                new QuizCard("Fi théorie des jeux, chnowa dilemme du prisonnier يورّي ?","زوز أشخاص عقلانيين ينجموا يختاروا نتيجة أسوأ خاطر ما يثقوش في بعضهم.","التعاون يحتاج ثقة walla قوانين وعقوبات ?"),
                new QuizCard("Chkoun اقترح الاختبار الشهير باش نقيّمو هل machine تنجم تبان ذكية fi conversation ?","Alan Turing سنة 1950.","إذا AI تقنعك اللي هي إنسان، هذا يعني بالضرورة اللي هي تفهم ?"),
                new QuizCard("Chnowa العنصر الكيميائي elli رمزو W ?","Tungstène, ويتسمّى زادة Wolfram.","شنوة أهم اليوم: نحفظو المعلومة walla نعرفو كيفاش نلقاوها ونثبتوها ?"),
                new QuizCard("Anehou nombre premier الوحيد bin 90 w 100 ?","97.","الرياضيات اكتشاف موجود من قبل walla اختراع بشري ?"),
                new QuizCard("Chnowa أعمق نقطة معروفة fi mo7itat l ardh ?","Challenger Deep fi fosse des Mariannes.","نصرفو أكثر على استكشاف البحر walla الفضاء ?"),
                new QuizCard("Tounes خذات استقلالها fi anehou تاريخ، وإعلان الجمهورية صار fi anehou سنة ?","20 مارس 1956؛ الجمهورية أُعلنت سنة 1957.","كيفاش يلزم الشباب اليوم يعاود يعرّف الاستقلال الحقيقي ?")
        );
        final List<String> truths=List.of(
                "Sra7a: chnowa ra2y تبدّل عندك 180 درجة في آخر عامين، و3lech ?",
                "Action: دافع دقيقة على رأي إنت شخصيا ما توافقش عليه.",
                "Sra7a: ken fama قرار واحد للمجتمع تنجم تبدلو, chnowa يكون ?",
                "Action: اختار موضوع خلافي واعمل عليه argument pour وargument contre.",
                "Sra7a: وقتاش آخر مرة اعترفت اللي إنت غالط ?",
                "Action: فسّر فكرة صعيبة في 30 ثانية كأنك تحكي لطفل عمره 8 سنين.",
                "Sra7a: شنوّة الحاجة اللي الناس تحكم عليك فيها بالغلط ?",
                "Action: قل حاجة باهية وصادقة على كل لاعب.",
                "Sra7a: تختار نجاح كبير مع ضغط دائم walla حياة هادئة ونجاح عادي ?",
                "Action: اقنع المجموعة بفكرة غريبة يختاروها هما.",
                "Sra7a: شنوّة أهم عندك: الحرية، الأمان، walla العدالة ? 3lech ?",
                "Action: احكي موقف في دقيقة، مرة من وجهة نظرك ومرة من وجهة نظر الشخص الآخر."
        );
        void join(String requestedMode,String id,String name){if(requestedMode!=null&&!started&&!requestedMode.equals(mode)){mode=requestedMode;players.clear();}if(players.containsKey(id))players.put(id,new Player(id,name));else if(!started&&players.size()<4)players.put(id,new Player(id,name));}
        void start(String requestedMode){if(requestedMode!=null)mode=requestedMode;if(players.size()<2)return;started=true;revealed=false;index=0;turnId=players.keySet().iterator().next();}
        boolean reveal(String id){if(!started||!"quiz".equals(mode)||!Objects.equals(turnId,id))return false;revealed=true;return true;}
        void next(String id){if(!started||!Objects.equals(turnId,id)||("quiz".equals(mode)&&!revealed))return;List<String> ids=new ArrayList<>(players.keySet());turnId=ids.get((ids.indexOf(id)+1)%ids.size());index=(index+1)%("quiz".equals(mode)?quiz.size():truths.size());revealed=false;}
        String prompt(){if(!started)return null;return "quiz".equals(mode)?quiz.get(index%quiz.size()).question():truths.get(index%truths.size());}
        String answer(){return started&&revealed&&"quiz".equals(mode)?quiz.get(index%quiz.size()).answer():null;}
        String discussion(){return started&&revealed&&"quiz".equals(mode)?quiz.get(index%quiz.size()).discussion():null;}
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
