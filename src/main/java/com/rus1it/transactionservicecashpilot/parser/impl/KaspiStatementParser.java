package com.rus1it.transactionservicecashpilot.parser.impl;

import com.rus1it.transactionservicecashpilot.parser.IStatementParser;
import com.rus1it.transactionservicecashpilot.parser.model.BankStatement;
import com.rus1it.transactionservicecashpilot.parser.model.Transaction;
import com.rus1it.transactionservicecashpilot.parser.util.DataNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Мультиязычный парсер для выписок Kaspi Bank (KZ, RU, EN).
 * Использует стратегию setSortByPosition(true) для извлечения текста.
 */
@Service
@Slf4j
public class KaspiStatementParser implements IStatementParser {

    private static final String BANK_NAME = "Kaspi Bank";
    private static final Currency KZT = Currency.getInstance("KZT");

    /**
     * Ищет ФИО владельца.
     * Поддерживает 2-строчный (KZ/RU) и 1-строчный (EN) форматы.
     * Группа 1: Имя (KZ/RU)
     * Группа 2: Фамилия (KZ/RU)
     * Группа 3: Полное имя (EN)
     */
    private static final Pattern OWNER_PATTERN = Pattern.compile(
            "\\R+" + // Пропускаем заголовок
                    "(?:" + // Начало группы ИЛИ
                    // Вариант KZ/RU (Группы 1 и 2)
                    "(?:([\\p{L}]+).*" + // Группа 1: Имя
                    "\\R+" +
                    "([\\p{L}\\s]+?)" +  // Группа 2: Фамилия (нежадная)
                    "\\s+(?:Шот нөмірі:|Номер счета:))" + // Якорь для остановки
                    "|" + // ИЛИ
                    // Вариант EN (Группа 3)
                    "(?:([A-Z\\s]+) Card number:)" + // Группа 3: Полное имя
                    ")" + // Конец группы ИЛИ
                    ".*", // Игнорируем остаток строки
            Pattern.MULTILINE);

    /**
     * Ищет IBAN (KZ + 18 букв/цифр). Универсален для всех языков.
     */
    private static final Pattern IBAN_PATTERN = Pattern.compile(
            "(KZ[A-Za-z0-9]{18})");

    /**
     * Ищет период выписки (KZ, RU, EN).
     * Группы (1,2) - KZ, (3,4) - RU, (5,6) - EN.
     */
    private static final Pattern PERIOD_PATTERN = Pattern.compile(
            "(?:(\\d{2}\\.\\d{2}\\.\\d{2})ж\\. бастап (\\d{2}\\.\\d{2}\\.\\d{2})ж\\. дейінгі)" + // KZ
                    "|" +
                    "(?:за период с (\\d{2}\\.\\d{2}\\.\\d{2}) по (\\d{2}\\.\\d{2}\\.\\d{2}))" + // RU
                    "|" +
                    "(?:from (\\d{2}\\.\\d{2}\\.\\d{2}) to (\\d{2}\\.\\d{2}\\.\\d{2}))"); // EN

    // --- Паттерны для сводки (summary) ---
    private static final Pattern SUMMARY_REPLENISH_PATTERN = Pattern.compile(
            "(?:Толықтыру|Пополнения|Replenishment)\\s+([+\\-]?[\\d\\s,]+)\\s*(?:T|₸)");
    private static final Pattern SUMMARY_TRANSFER_PATTERN = Pattern.compile(
            "(?:Аударым|Переводы|Transfers)\\s+([+\\-]?[\\d\\s,]+)\\s*(?:T|₸)");
    private static final Pattern SUMMARY_PURCHASE_PATTERN = Pattern.compile(
            "(?:Зат сатып алу|Покупки|Purchases)\\s+([+\\-]?[\\d\\s,]+)\\s*(?:T|₸)");

    /**
     * Ищет лимит (KZ, RU, EN).
     */
    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "(?:Басқа толықтырулар|Другие пополнения|Other deposits)\\s+([+\\-]?[\\d\\s,]+)\\s*(?:T|₸)");

    /**
     * Основной паттерн для парсинга строк транзакций.
     * Группа 1: Дата
     * Группа 2: Сумма
     * Группа 3: Тип операции (мультиязычный)
     * Группа 4: Описание (опционально)
     */
    private static final Pattern TRANSACTION_PATTERN = Pattern.compile(
            "^(\\d{2}\\.\\d{2}\\.\\d{2})" + // Г1: Дата
                    "\\s+([+\\-]\\s*[\\d\\s,]+)" +  // Г2: Сумма
                    "\\s*(?:T|₸)" + // Валюта
                    "\\s+" +
                    // Г3: Типы (KZ|RU|EN)
                    "(Толықтыру|Пополнение|Replenishment|" +
                    "Аударым|Перевод|Transfers|" +
                    "Зат сатып алу|Покупка|Purchases|" +
                    "Əртүрлі|Разное|Others|" +
                    "Ақша алу|Снятия|Withdrawals)" +
                    "(?:\\s+(.+))?$", // Г4: Описание (опционально)
            Pattern.MULTILINE);

    /**
     * Главный метод парсинга.
     */
    @Override
    public BankStatement parse(PDDocument document) throws IOException {
        // Извлекаем текст, "собранный" по координатам
        String text = extractTextSorted(document);

        BankStatement statement = BankStatement.builder()
                .bankName(BANK_NAME)
                .build();

        // Поочередно извлекаем все данные из текста
        parseOwner(text, statement);
        parseIban(text, statement);
        parsePeriod(text, statement);
        parseKaspiSpecificData(text, statement); // Сводка и лимиты
        parseTransactions(text, statement); // Список транзакций

        return statement;
    }

    /**
     * "Сниффер": Проверяет, подходит ли этот парсер для файла.
     */
    @Override
    public boolean canParse(String firstPageText) {
        // Ищет "Kaspi Gold" И один из мультиязычных заголовков
        return firstPageText.contains("Kaspi Gold") &&
                (firstPageText.contains("ҮЗІНДІ КӨШІРМЕ") ||
                        firstPageText.contains("за период с") ||
                        firstPageText.contains("balance statement for the period"));
    }

    @Override
    public String getBankName() {
        return BANK_NAME;
    }

    /**
     * Извлекает текст из PDF, сортируя по позиции (X/Y).
     * Это критически важно для "сборки" таблиц в строки.
     */
    private String extractTextSorted(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true); // <--- Ключевая настройка
        return stripper.getText(document);
    }

    // --- Приватные методы-помощники ---

    /**
     * Извлекает ФИО, учитывая 1-строчный (EN) или 2-строчный (KZ/RU) формат.
     */
    private void parseOwner(String text, BankStatement statement) {
        Matcher m = OWNER_PATTERN.matcher(text);
        if (m.find()) {
            if (m.group(3) != null) { // Вариант EN (Группа 3)
                statement.setOwnerName(m.group(3).trim());
            } else if (m.group(1) != null) { // Вариант KZ/RU (Группы 1 и 2)
                statement.setOwnerName(m.group(1).trim() + " " + m.group(2).trim());
            }
        } else {
            log.warn("Не удалось найти OWNER_PATTERN");
        }
    }

    /**
     * Извлекает IBAN.
     */
    private void parseIban(String text, BankStatement statement) {
        Matcher m = IBAN_PATTERN.matcher(text);
        if (m.find()) {
            statement.setIban(m.group(1).trim());
        } else {
            log.warn("Не удалось найти IBAN_PATTERN ({}).", IBAN_PATTERN);
        }
    }

    /**
     * Извлекает даты начала/конца, проверяя, какая языковая версия найдена.
     */
    private void parsePeriod(String text, BankStatement statement) {
        Matcher m = PERIOD_PATTERN.matcher(text);
        if (m.find()) {
            if (m.group(1) != null) { // KZ (Группы 1, 2)
                statement.setPeriodFrom(DataNormalizer.parseKaspiDate(m.group(1)));
                statement.setPeriodTo(DataNormalizer.parseKaspiDate(m.group(2)));
            } else if (m.group(3) != null) { // RU (Группы 3, 4)
                statement.setPeriodFrom(DataNormalizer.parseKaspiDate(m.group(3)));
                statement.setPeriodTo(DataNormalizer.parseKaspiDate(m.group(4)));
            } else if (m.group(5) != null) { // EN (Группы 5, 6)
                statement.setPeriodFrom(DataNormalizer.parseKaspiDate(m.group(5)));
                statement.setPeriodTo(DataNormalizer.parseKaspiDate(m.group(6)));
            }
        } else {
            log.warn("Не удалось найти PERIOD_PATTERN");
        }
    }

    /**
     * Извлекает доп. данные (сводка, лимиты) в карту additionalData.
     */
    private void parseKaspiSpecificData(String text, BankStatement statement) {
        // Ищем Лимит
        Matcher mLimit = LIMIT_PATTERN.matcher(text);
        if (mLimit.find()) {
            try {
                BigDecimal limit = DataNormalizer.parseKaspiAmount(mLimit.group(1));
                statement.getAdditionalData().put("cashWithdrawalLimit", limit);
            } catch (Exception e) {
                log.warn("Не удалось спарсить лимит Kaspi: {}", mLimit.group(1));
            }
        } else {
            log.warn("Не удалось найти LIMIT_PATTERN");
        }

        // Ищем Сводку
        Matcher mReplenish = SUMMARY_REPLENISH_PATTERN.matcher(text);
        if (mReplenish.find()) {
            statement.getAdditionalData().put("summaryReplenish", mReplenish.group(1).trim());
        }
        Matcher mTransfer = SUMMARY_TRANSFER_PATTERN.matcher(text);
        if (mTransfer.find()) {
            statement.getAdditionalData().put("summaryTransfer", mTransfer.group(1).trim());
        }
        Matcher mPurchase = SUMMARY_PURCHASE_PATTERN.matcher(text);
        if (mPurchase.find()) {
            statement.getAdditionalData().put("summaryPurchase", mPurchase.group(1).trim());
        }
    }

    /**
     * Итерирует по всем строкам транзакций и парсит их.
     */
    private void parseTransactions(String text, BankStatement statement) {
        Matcher m = TRANSACTION_PATTERN.matcher(text);

        // Ищем все совпадения
        while (m.find()) {
            try {
                // Описание (Группа 4) опционально
                String description = (m.group(4) != null) ? m.group(4).trim() : "";

                Transaction transaction = Transaction.builder()
                        .date(DataNormalizer.parseKaspiDate(m.group(1))) // Группа 1
                        .amount(DataNormalizer.parseKaspiAmount(m.group(2))) // Группа 2
                        .currency(KZT)
                        .type(m.group(3).trim()) // Группа 3
                        .description(description) // Группа 4
                        .build();

                statement.addTransaction(transaction);

            } catch (Exception e) {
                // Если одна строка не спарсилась, логируем и продолжаем
                log.error("Сбой парсинга строки транзакции: {}", m.group(0), e);
            }
        }
    }
}