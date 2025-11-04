package com.rus1it.transactionservicecashpilot.parser.service;

import com.rus1it.transactionservicecashpilot.parser.IStatementParser;
import com.rus1it.transactionservicecashpilot.parser.model.BankStatement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@Slf4j
@RequiredArgsConstructor
public class ParsingFacadeService {

    private final ParserProviderService parserProvider;

    public BankStatement parseStatement(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Файл пустой");
        }

        log.info("Начало парсинга файла: {}", file.getOriginalFilename());

        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF((RandomAccessRead) file.getInputStream())) {

            IStatementParser parser = parserProvider.getParser(document);
            log.info("Используется парсер: {}", parser.getBankName());

            BankStatement statement = parser.parse(document);

            log.info("Парсинг завершен. Найдено {} транзакций.", statement.getTransactions().size());
            return statement;

        } catch (Exception e) {
            log.error("Ошибка парсинга файла {}: {}", file.getOriginalFilename(), e.getMessage());
            throw new IOException("Ошибка парсинга PDF: " + e.getMessage(), e);
        }
    }

}