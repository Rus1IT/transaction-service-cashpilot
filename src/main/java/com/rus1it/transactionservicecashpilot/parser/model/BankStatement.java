package com.rus1it.transactionservicecashpilot.parser.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class BankStatement {

    private String bankName;

    private String ownerName;
    private String iban;

    private LocalDate periodFrom;
    private LocalDate periodTo;

     /** Гибкое поле для хранения метаданных, уникальных для этой выписки. */

    @Builder.Default
    private Map<String, Object> additionalData = new HashMap<>();

    /** Список всех транзакций */
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    public void addTransaction(Transaction transaction) {
        this.transactions.add(transaction);
    }
}