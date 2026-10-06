package com.back.car_rent.dto;

import lombok.*;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEventDTO {
    private String id;
    private String start;
    private String end;
    private String title;
    private String className;
    private Map<String, Object> extendedProps;
}