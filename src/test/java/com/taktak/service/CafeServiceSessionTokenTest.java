package com.taktak.service;

import com.taktak.model.Cafe;
import com.taktak.model.CafeTable;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.CafeTableRepository;
import com.taktak.repository.CategoryRepository;
import com.taktak.repository.OrderRepository;
import com.taktak.repository.ProductRepository;
import com.taktak.service.impl.CafeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CafeServiceSessionTokenTest {
    private CafeTableRepository tables;
    private OrderRepository orders;
    private CafeServiceImpl service;
    private Cafe cafe;

    @BeforeEach
    void setUp() {
        CafeRepository cafes = mock(CafeRepository.class);
        tables = mock(CafeTableRepository.class);
        orders = mock(OrderRepository.class);
        cafe = Cafe.builder().id(UUID.randomUUID()).slug("monastir-lounge").name("Monastir Lounge").build();
        when(cafes.findBySlug("monastir-lounge")).thenReturn(Optional.of(cafe));
        when(orders.findByCafeIdOrderByCreatedAtDesc(cafe.getId())).thenReturn(List.of());
        service = new CafeServiceImpl(
                cafes,
                mock(CategoryRepository.class),
                mock(ProductRepository.class),
                tables,
                orders,
                mock(SimpMessagingTemplate.class),
                mock(IOrderService.class)
        );
    }

    @Test
    void validatesTheSessionWithoutExposingItsToken() {
        CafeTable table = tableWithToken("valid-secret-token");
        when(tables.findByCafeIdAndTableNumber(cafe.getId().toString(), 5)).thenReturn(Optional.of(table));

        Map<String, Object> valid = service.getTableStatus("monastir-lounge", 5, "valid-secret-token");
        Map<String, Object> invalid = service.getTableStatus("monastir-lounge", 5, "wrong-token");

        assertEquals(true, valid.get("sessionValid"));
        assertEquals(false, invalid.get("sessionValid"));
        assertFalse(valid.containsKey("sessionToken"));
        assertFalse(invalid.containsKey("sessionToken"));
    }

    @Test
    void generatesAFullLengthTokenForLegacyTables() {
        CafeTable table = tableWithToken(null);
        when(tables.findByCafeIdAndTableNumber(cafe.getId().toString(), 5)).thenReturn(Optional.of(table));
        when(tables.save(table)).thenReturn(table);

        Map<String, Object> result = service.getTableStatus("monastir-lounge", 5, null);

        assertEquals(false, result.get("sessionValid"));
        assertNotNull(table.getSessionToken());
        assertEquals(36, table.getSessionToken().length());
        verify(tables).save(table);
    }

    private CafeTable tableWithToken(String token) {
        CafeTable table = new CafeTable();
        table.setCafeId(cafe.getId().toString());
        table.setTableNumber(5);
        table.setSessionToken(token);
        return table;
    }
}
