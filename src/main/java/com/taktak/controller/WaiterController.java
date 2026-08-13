package com.taktak.controller;

import com.taktak.dto.AssignTablesRequest;
import com.taktak.dto.WaiterDTO;
import com.taktak.service.IWaiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WaiterController {

    private final IWaiterService waiterService;

    @GetMapping("/cafes/{cafeSlug}/waiters/active")
    public ResponseEntity<List<WaiterDTO>> getActiveWaiters(@PathVariable String cafeSlug) {
        return ResponseEntity.ok(waiterService.getActiveWaiters(cafeSlug));
    }

    @PostMapping("/waiters/{waiterId}/assign-tables")
    public ResponseEntity<WaiterDTO> assignTables(
            @PathVariable String waiterId,
            @RequestBody AssignTablesRequest request
    ) {
        return ResponseEntity.ok(waiterService.assignTables(waiterId, request.getTableNumbers()));
    }

    @PostMapping("/cafes/{cafeSlug}/waiters")
    public ResponseEntity<WaiterDTO> createWaiter(
            @PathVariable String cafeSlug,
            @RequestBody Map<String, String> body
    ) {
        String name = body.get("name");
        String pinCode = body.get("pinCode");
        String shiftHours = body.get("shiftHours");
        return ResponseEntity.ok(waiterService.createWaiter(cafeSlug, name, pinCode, shiftHours));
    }

    @PutMapping("/waiters/{waiterId}")
    public ResponseEntity<WaiterDTO> updateWaiter(
            @PathVariable String waiterId,
            @RequestBody Map<String, Object> body
    ) {
        String name = (String) body.get("name");
        String pinCode = (String) body.get("pinCode");
        String shiftHours = (String) body.get("shiftHours");
        Boolean isActive = (Boolean) body.get("isActive");

        return ResponseEntity.ok(waiterService.updateWaiter(waiterId, name, pinCode, shiftHours, isActive));
    }

    @DeleteMapping("/waiters/{waiterId}")
    public ResponseEntity<Void> deleteWaiter(@PathVariable String waiterId) {
        waiterService.deleteWaiter(waiterId);
        return ResponseEntity.noContent().build();
    }
}
