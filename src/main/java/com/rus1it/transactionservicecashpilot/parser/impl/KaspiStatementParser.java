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

@Service
@Slf4j
public class KaspiStatementParser implements IStatementParser {

    private static final String BANK_NAME = "Kaspi Bank";
    private static final Currency KZT = Currency.getInstance("KZT");

    // Имя
    private static final Pattern OWNER_PATTERN = Pattern.compile(
            "ҮЗІНДІ КӨШІРМЕ\n\n(.+?)\n\n(.+?)\n");
    // IBAN
    private static final Pattern IBAN_PATTERN = Pattern.compile(
            "Шот нөмірі:\\n\",\"(KZ\\d{18})");
    // Период
    private static final Pattern PERIOD_PATTERN = Pattern.compile(
            "(\\d{2}\\.\\d{2}\\.\\d{2})ж\\. бастап (\\d{2}\\.\\d{2}\\.\\d{2})ж\\. дейінгі");
    // Сводка
    private static final Pattern SUMMARY_REPLENISH_PATTERN = Pattern.compile(
            "Толықтыру\\n\",\"(.+?)\\n");
    private static final Pattern SUMMARY_TRANSFER_PATTERN = Pattern.compile(
            "Аударым\\n\",\"(.+?)\\n");
    private static final Pattern SUMMARY_PURCHASE_PATTERN = Pattern.compile(
            "Зат сатып алу\\n\",\"(.+?)\\n");
    // Лимит
    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "Басқа толықтырулар\\n\",\"(.+?)\\n");

    // Главный RegEx для транзакций (с MULTILINE)
    private static final Pattern TRANSACTION_PATTERN = Pattern.compile(
            "^(\\d{2}\\.\\d{2}\\.\\d{2})\\s+([+\\-]\\s*[\\d\\s,]+)\\s*(?:T|₸)\\s+(.+?)\\s+(.+)$",
            Pattern.MULTILINE);

    @Override
    public BankStatement parse(PDDocument document) throws IOException {
        String text = extractTextSorted(document);

        BankStatement statement = BankStatement.builder()
                .bankName(BANK_NAME)
                .build();

        // 1. Парсим метаданные
        parseOwner(text, statement);
        parseIban(text, statement);
        parsePeriod(text, statement);

        // 2. Парсим уникальные данные Kaspi
        parseKaspiSpecificData(text, statement);

        // 3. Парсим транзакции
        parseTransactions(text, statement);

        return statement;
    }

    @Override
    public boolean canParse(String firstPageText) {
        return firstPageText.contains("Kaspi Gold") && firstPageText.contains("ҮЗІНДІ КӨШІРМЕ");
    }

    @Override
    public String getBankName() {
        return BANK_NAME;
    }

    private String extractTextSorted(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(document);
    }


    private void parseOwner(String text, BankStatement statement) {
        Matcher m = OWNER_PATTERN.matcher(text);
        if (m.find()) {
            statement.setOwnerName(m.group(1).trim() + " " + m.group(2).trim());
        }
    }

    private void parseIban(String text, BankStatement statement) {
        Matcher m = IBAN_PATTERN.matcher(text);
        if (m.find()) {
            statement.setIban(m.group(1).trim());
        }
    }

    private void parsePeriod(String text, BankStatement statement) {
        Matcher m = PERIOD_PATTERN.matcher(text);
        if (m.find()) {
            statement.setPeriodFrom(DataNormalizer.parseKaspiDate(m.group(1)));
            statement.setPeriodTo(DataNormalizer.parseKaspiDate(m.group(2)));
        }
    }

    private void parseKaspiSpecificData(String text, BankStatement statement) {
        Matcher mLimit = LIMIT_PATTERN.matcher(text);
        if (mLimit.find()) {
            try {
                // "300 000,00 T"
                BigDecimal limit = DataNormalizer.parseKaspiAmount(mLimit.group(1));
                statement.getAdditionalData().put("cashWithdrawalLimit", limit);
            } catch (Exception e) {
                log.warn("Не удалось спарсить лимит Kaspi");
            }
        }

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

    private void parseTransactions(String text, BankStatement statement) {
        Matcher m = TRANSACTION_PATTERN.matcher(text);

        while (m.find()) {
            try {
                Transaction transaction = Transaction.builder()
                        .date(DataNormalizer.parseKaspiDate(m.group(1)))
                        .amount(DataNormalizer.parseKaspiAmount(m.group(2)))
                        .currency(KZT)
                        .type(m.group(3).trim())
                        .description(m.group(4).trim())
                        .build();

                statement.addTransaction(transaction);

            } catch (Exception e) {
                log.error("Сбой парсинга строки транзакции Kaspi: {}", m.group(0), e);
            }
        }
    }
}