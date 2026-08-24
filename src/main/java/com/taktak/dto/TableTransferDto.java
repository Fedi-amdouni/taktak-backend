package com.taktak.dto;

import lombok.Data;

@Data
public class TableTransferDto {
    private Integer sourceTableNumber;
    private Integer newTableNumber;
    private String participantId;
    private String sourceSessionToken;
    private String targetSessionToken;
}
