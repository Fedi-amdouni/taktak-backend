package com.taktak.controller;

import java.util.*;

/** Compact UNO rules engine for a single in-memory table room (2 to 4 players). */
final class UnoState {
    static final List<String> COLORS = List.of("RED", "BLUE", "GREEN", "YELLOW");
    final LinkedHashMap<String, GameWebSocketController.Player> players = new LinkedHashMap<>();
    final Map<String, List<String>> hands = new LinkedHashMap<>();
    final List<String> deck = new ArrayList<>();
    String topCard; String turnId; String chosenColor; String winner; int direction = 1; boolean started;

    void join(String id, String name) { if (players.containsKey(id)) players.put(id, new GameWebSocketController.Player(id, name)); else if (!started && players.size() < 4) players.put(id, new GameWebSocketController.Player(id, name)); }
    void start() { if (players.size() < 2) return; deck.clear(); hands.clear(); for (String color : COLORS) { for (int n=0;n<=9;n++) { deck.add(color+":"+n); if(n>0) deck.add(color+":"+n); } for(String a:List.of("SKIP","REVERSE","DRAW2")){deck.add(color+":"+a);deck.add(color+":"+a);} } for(int i=0;i<4;i++){deck.add("WILD:WILD");deck.add("WILD:DRAW4");} Collections.shuffle(deck); for(String id:players.keySet()){List<String> hand=new ArrayList<>();for(int i=0;i<7;i++)hand.add(draw());hands.put(id,hand);} do {topCard=draw();} while(topCard.startsWith("WILD:")); turnId=players.keySet().iterator().next(); chosenColor=null; winner=null; direction=1; started=true; }
    boolean play(String id, String card, String color) { if(!started||winner!=null||!Objects.equals(id,turnId)||!hands.getOrDefault(id,List.of()).contains(card)||!valid(card))return false; hands.get(id).remove(card); topCard=card; chosenColor=card.startsWith("WILD:")&&COLORS.contains(color)?color:null; if(hands.get(id).isEmpty()){winner=id;return true;} int steps=1; String value=card.split(":")[1]; if("SKIP".equals(value))steps=2; if("REVERSE".equals(value)){direction*=-1;if(players.size()==2)steps=2;} if("DRAW2".equals(value)) {drawTo(next(id,1),2);steps=2;} if("DRAW4".equals(value)){drawTo(next(id,1),4);steps=2;} turnId=next(id,steps); return true; }
    boolean drawCard(String id){if(!started||winner!=null||!Objects.equals(id,turnId))return false; hands.computeIfAbsent(id,k->new ArrayList<>()).add(draw());turnId=next(id,1);return true;}
    private boolean valid(String card){String[] a=card.split(":"),b=topCard.split(":");return a[0].equals("WILD")||a[0].equals(chosenColor!=null?chosenColor:b[0])||a[1].equals(b[1]);}
    private String draw(){if(deck.isEmpty())return "RED:0";return deck.remove(deck.size()-1);} private void drawTo(String id,int count){for(int i=0;i<count;i++)hands.get(id).add(draw());} private String next(String id,int steps){List<String> ids=new ArrayList<>(players.keySet());int p=ids.indexOf(id);return ids.get(Math.floorMod(p+direction*steps,ids.size()));}
}
