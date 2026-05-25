package com.sky.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AbnormalOrderReport {
    private Long id;
    private LocalDate reportDate;
    private Integer totalCancelled;
    private Integer totalRejected;
    private Integer totalTimeout;
    private Integer avgCancelTime;
    private String topCancelReason;
    private String detailJson;
    private LocalDateTime createTime;
}