package com.taktak.game.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Administrative control for ephemeral, table-scoped game rooms. */
@RestController
@RequestMapping("/api/cafes/{slug}/game-rooms")
@RequiredArgsConstructor
public class GameRoomController {
    private final GameWebSocketController gameWebSocketController;

    @DeleteMapping
    public ResponseEntity<Map<String, Integer>> clearGameRooms(@PathVariable String slug) {
        int cleared = gameWebSocketController.clearRoomsForCafe(slug);
        return ResponseEntity.ok(Map.of("clearedRooms", cleared));
    }
}
