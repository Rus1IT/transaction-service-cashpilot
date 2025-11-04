package com.rus1it.transactionservicecashpilot.parser.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class Transaction {

    private LocalDate date;

    private BigDecimal amount;

    private Currency currency;

    private String description;

    private String type;

     // Гибкое поле для хранения данных, уникальных для этого банка.
    @Builder.Default
    private Map<String, Object> additionalData = new HashMap<>();
}