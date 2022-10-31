package com.backend.eyeson.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResponseAngelInfoDto {
    private LocalDateTime angelAlarmStart;
    private LocalDateTime angelAlarmEnd;
    private int angelAlarmDay;
    private int angelCompCnt;
    private int angelHelpCnt;
    private char angelGender;
    private boolean angelActive;
}
