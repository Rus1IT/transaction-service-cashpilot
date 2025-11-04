package com.rus1it.transactionservicecashpilot.parser.util;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@UtilityClass
public class DataNormalizer {

    public static final DateTimeFormatter KASPI_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yy").withLocale(Locale.ROOT);

    public LocalDate parseKaspiDate(String dateString) {
        return LocalDate.parse(dateString.trim(), KASPI_DATE_FORMATTER);
    }

    public BigDecimal parseKaspiAmount(String amountString) {
        String cleanString = amountString
                .replaceAll("[\\sT₸]", "")
                .replace(",", ".")
                .trim();

        return new BigDecimal(cleanString);
    }
}