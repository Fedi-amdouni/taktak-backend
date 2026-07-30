package com.taktak.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaiterDTO {
    private String id;
    private String cafeId;
    private String name;
    private String pinCode;
    private String shiftHours;
    private Boolean isActive;
    private List<Integer> assignedTables;
}
