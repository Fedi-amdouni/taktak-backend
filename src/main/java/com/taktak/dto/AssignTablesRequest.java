package com.taktak.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssignTablesRequest {
    private List<Integer> tableNumbers;
}
