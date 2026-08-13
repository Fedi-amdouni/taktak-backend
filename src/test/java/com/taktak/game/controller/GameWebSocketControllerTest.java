package com.taktak.game.controller;

import com.taktak.service.IPartyQuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class GameWebSocketControllerTest {

    @Test
    void clearsOnlyTheRequestedCafesEphemeralGameRooms() {
        GameWebSocketController controller = new GameWebSocketController(
                mock(SimpMessagingTemplate.class), mock(IPartyQuestionService.class));

        GameWebSocketController.RouletteJoinRequest monastirPlayer = new GameWebSocketController.RouletteJoinRequest();
        monastirPlayer.setPlayerId("player-1");
        monastirPlayer.setName("Amina");
        controller.joinRoulette("monastir-lounge-5", monastirPlayer);

        GameWebSocketController.RouletteJoinRequest carthagePlayer = new GameWebSocketController.RouletteJoinRequest();
        carthagePlayer.setPlayerId("player-2");
        carthagePlayer.setName("Sami");
        controller.joinRoulette("carthage-lounge-5", carthagePlayer);

        assertEquals(1, controller.clearRoomsForCafe("monastir-lounge"));
        assertEquals(0, controller.clearRoomsForCafe("monastir-lounge"));
        assertEquals(1, controller.clearRoomsForCafe("carthage-lounge"));
    }

    @Test
    void removesAnAbandonedPlayersRoomWithoutAClientLeaveMessage() {
        GameWebSocketController controller = new GameWebSocketController(
                mock(SimpMessagingTemplate.class), mock(IPartyQuestionService.class));

        GameWebSocketController.RouletteJoinRequest player = new GameWebSocketController.RouletteJoinRequest();
        player.setPlayerId("abandoned-player");
        player.setName("Amina");
        controller.joinRoulette("monastir-lounge-5", player);

        controller.removePlayer("abandoned-player");

        assertEquals(0, controller.clearRoomsForCafe("monastir-lounge"));
    }
}
