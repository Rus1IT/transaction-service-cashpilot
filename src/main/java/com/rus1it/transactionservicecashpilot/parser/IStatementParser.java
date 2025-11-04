package com.rus1it.transactionservicecashpilot.parser;

import com.rus1it.transactionservicecashpilot.parser.model.BankStatement;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.IOException;

public interface IStatementParser {

    BankStatement parse(PDDocument document) throws IOException;

    boolean canParse(String firstPageText);

    String getBankName();
}