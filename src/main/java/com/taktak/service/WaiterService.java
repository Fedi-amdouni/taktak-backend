package com.taktak.service;

import com.taktak.dto.WaiterDTO;
import com.taktak.model.Cafe;
import com.taktak.model.TableAssignment;
import com.taktak.model.Waiter;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.TableAssignmentRepository;
import com.taktak.repository.WaiterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaiterService {

    private final WaiterRepository waiterRepository;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final CafeRepository cafeRepository;

    @Transactional(readOnly = true)
    public List<WaiterDTO> getActiveWaiters(String cafeSlug) {
        Cafe cafe = cafeRepository.findBySlug(cafeSlug)
                .orElseThrow(() -> new RuntimeException("Café non trouvé : " + cafeSlug));

        List<Waiter> waiters = waiterRepository.findByCafeIdAndIsActiveTrue(cafe.getId());
        return waiters.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public WaiterDTO loginByPin(String cafeSlug, String pinCode) {
        log.info("Tentative de connexion par PIN pour café: {}", cafeSlug);
        Cafe cafe = cafeRepository.findBySlug(cafeSlug)
                .orElseThrow(() -> new RuntimeException("Café non trouvé : " + cafeSlug));

        String cleanPin = pinCode != null ? pinCode.trim() : "";
        Waiter waiter = waiterRepository.findByCafeIdAndPinCodeAndIsActiveTrue(cafe.getId(), cleanPin)
                .orElseThrow(() -> {
                    log.warn("Échec connexion PIN '{}' pour le café {}", cleanPin, cafeSlug);
                    return new RuntimeException("Code PIN invalide pour ce café");
                });

        log.info("Connexion réussie pour le serveur {} (ID: {})", waiter.getName(), waiter.getId());
        return toDTO(waiter);
    }

    @Transactional
    public WaiterDTO assignTables(String waiterIdStr, List<Integer> tableNumbers) {
        UUID waiterId = UUID.fromString(waiterIdStr);
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new RuntimeException("Serveur non trouvé"));

        tableAssignmentRepository.deleteByWaiterId(waiterId);

        if (tableNumbers != null && !tableNumbers.isEmpty()) {
            List<TableAssignment> assignments = tableNumbers.stream()
                    .map(tn -> TableAssignment.builder()
                            .waiterId(waiterId)
                            .cafeId(waiter.getCafeId())
                            .tableNumber(tn)
                            .build())
                    .collect(Collectors.toList());

            tableAssignmentRepository.saveAll(assignments);
        }

        return toDTO(waiter);
    }

    @Transactional
    public WaiterDTO createWaiter(String cafeSlug, String name, String pinCode, String shiftHours) {
        Cafe cafe = cafeRepository.findBySlug(cafeSlug)
                .orElseThrow(() -> new RuntimeException("Café non trouvé"));

        Waiter waiter = waiterRepository.findByCafeIdAndName(cafe.getId(), name)
                .orElseGet(() -> Waiter.builder()
                        .cafeId(cafe.getId())
                        .name(name)
                        .pinCode(pinCode.trim())
                        .shiftHours(shiftHours)
                        .isActive(true)
                        .build());

        waiter.setName(name);
        waiter.setPinCode(pinCode.trim());
        waiter.setShiftHours(shiftHours);
        waiter.setIsActive(true);
        Waiter saved = waiterRepository.save(waiter);

        return toDTO(saved);
    }

    @Transactional
    public WaiterDTO updateWaiter(String waiterIdStr, String name, String pinCode, String shiftHours, Boolean isActive) {
        UUID waiterId = UUID.fromString(waiterIdStr);
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new RuntimeException("Serveur non trouvé"));

        if (name != null) waiter.setName(name);
        if (pinCode != null && !pinCode.isBlank()) waiter.setPinCode(pinCode.trim());
        if (shiftHours != null) waiter.setShiftHours(shiftHours);
        if (isActive != null) waiter.setIsActive(isActive);

        Waiter saved = waiterRepository.save(waiter);
        return toDTO(saved);
    }

    @Transactional
    public void deleteWaiter(String waiterIdStr) {
        UUID waiterId = UUID.fromString(waiterIdStr);
        tableAssignmentRepository.deleteByWaiterId(waiterId);
        waiterRepository.deleteById(waiterId);
    }

    private WaiterDTO toDTO(Waiter waiter) {
        List<TableAssignment> assignments = tableAssignmentRepository.findByWaiterId(waiter.getId());
        List<Integer> tables = assignments.stream()
                .map(TableAssignment::getTableNumber)
                .sorted()
                .collect(Collectors.toList());

        return WaiterDTO.builder()
                .id(waiter.getId().toString())
                .cafeId(waiter.getCafeId().toString())
                .name(waiter.getName())
                .pinCode(waiter.getPinCode())
                .shiftHours(waiter.getShiftHours())
                .isActive(waiter.getIsActive())
                .assignedTables(tables)
                .build();
    }
}
