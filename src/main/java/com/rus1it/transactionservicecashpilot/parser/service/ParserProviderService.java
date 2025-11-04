package com.rus1it.transactionservicecashpilot.parser.service;

import com.rus1it.transactionservicecashpilot.parser.IStatementParser;
import com.rus1it.transactionservicecashpilot.parser.exception.ParserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor // Lombok DI
public class ParserProviderService {

    private final List<IStatementParser> parsers;

    public IStatementParser getParser(PDDocument document) throws IOException {
        String firstPageText = extractFirstPageText(document);

        return parsers.stream()
                .filter(parser -> parser.canParse(firstPageText))
                .findFirst()
                .orElseThrow(() -> new ParserNotFoundException("Не найден подходящий парсер для этого PDF-файла."));
    }

    private String extractFirstPageText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setEndPage(1);
        stripper.setSortByPosition(true);
        return stripper.getText(document);
    }
}