package com.anvritai.abhay.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;

@Service
public class DocumentExtractionService {
    private static final Pattern PDF_TEXT = Pattern.compile("\\((.{2,500}?)\\)", Pattern.DOTALL);
    private static final Pattern GSTIN = Pattern.compile("\\b[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]\\b");
    private static final Map<String, Pattern> FIELD_PATTERNS = Map.ofEntries(
            Map.entry("invoice_number", labelled("(?:invoice|bill)(?:\\s+no|\\s+number|\\s*#)?")),
            Map.entry("invoice_date", Pattern.compile("(?i)(?:invoice\\s+date|date)\\s*[:#-]?\\s*(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}|\\d{4}-\\d{2}-\\d{2})")),
            Map.entry("vendor_name", labelled("(?:vendor|supplier)")),
            Map.entry("customer_name", labelled("(?:customer|buyer|bill\\s+to)")),
            Map.entry("taxable_amount", amount("(?:taxable(?:\\s+amount)?|subtotal|sub\\s+total)")),
            Map.entry("cgst_amount", amount("cgst")),
            Map.entry("sgst_amount", amount("sgst")),
            Map.entry("igst_amount", amount("igst")),
            Map.entry("cess_amount", amount("cess")),
            Map.entry("total_amount", amount("(?:grand\\s+total|invoice\\s+total|total\\s+amount|amount\\s+payable)")),
            Map.entry("hsn_sac", Pattern.compile("(?i)(?:hsn|sac|hsn/sac)\\s*[:#-]?\\s*([0-9]{4,8})")),
            Map.entry("payment_reference", labelled("(?:payment\\s+reference|payment\\s+ref)")),
            Map.entry("bank_reference", labelled("(?:bank\\s+reference|utr|transaction\\s+id)")));

    public ExtractionOutcome extract(String extension, byte[] content) {
        String text;
        String extractor;
        if ("csv".equals(extension)) {
            text = new String(content, StandardCharsets.UTF_8).replace("\uFEFF", "");
            extractor = "CSV_TEXT_V1";
        } else if ("pdf".equals(extension)) {
            text = pdfText(content);
            extractor = "PDF_TEXT_FOUNDATION_V1";
            if (text.isBlank()) return ExtractionOutcome.pending("OCR_PENDING",
                    "Scanned PDF requires an OCR engine.");
        } else if (Set.of("jpg", "jpeg", "png").contains(extension)) {
            return ExtractionOutcome.pending("OCR_PENDING", "Image document requires an OCR engine.");
        } else if ("xlsx".equals(extension)) {
            return ExtractionOutcome.pending("PENDING", "Spreadsheet cell extraction is queued.");
        } else {
            return ExtractionOutcome.pending("PENDING", "Document extraction is queued.");
        }
        Map<String, ExtractedValue> fields = extractFields(text);
        BigDecimal confidence = fields.isEmpty() ? new BigDecimal("0.3000")
                : fields.values().stream().map(ExtractedValue::confidence)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(fields.size()), 4, java.math.RoundingMode.HALF_UP);
        return new ExtractionOutcome(text, fields, confidence, "COMPLETED", extractor, "Extraction completed.");
    }

    private Map<String, ExtractedValue> extractFields(String text) {
        Map<String, ExtractedValue> result = new LinkedHashMap<>();
        Matcher gstin = GSTIN.matcher(text.toUpperCase(Locale.ROOT));
        if (gstin.find()) result.put("gstin", value(gstin.group(), "0.9800"));
        FIELD_PATTERNS.forEach((name, pattern) -> {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String raw = matcher.group(1).trim();
                String normalized = name.endsWith("_amount") ? normalizeAmount(raw) : raw;
                result.put(name, new ExtractedValue(raw, normalized, new BigDecimal("0.8500")));
            }
        });
        return result;
    }

    private String pdfText(byte[] content) {
        String raw = new String(content, StandardCharsets.ISO_8859_1);
        StringBuilder text = new StringBuilder();
        Matcher matcher = PDF_TEXT.matcher(raw);
        while (matcher.find() && text.length() < 100_000) {
            text.append(matcher.group(1).replace("\\(", "(").replace("\\)", ")")).append('\n');
        }
        if (text.length() < 20) {
            String printable = raw.replaceAll("[^\\x20-\\x7E\\r\\n]", " ");
            if (printable.toLowerCase(Locale.ROOT).matches("(?s).*(invoice|gstin|taxable|total).*")) {
                text.append(printable);
            }
        }
        return text.toString().trim();
    }

    private static Pattern labelled(String label) {
        return Pattern.compile("(?im)" + label + "\\s*[:#-]?\\s*([^,\\r\\n]{2,120})");
    }
    private static Pattern amount(String label) {
        return Pattern.compile("(?i)" + label + "(?:\\s+amount)?\\s*[:#-]?\\s*(?:INR|Rs\\.?)?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)");
    }
    private String normalizeAmount(String value) { return value.replace(",", ""); }
    private ExtractedValue value(String value, String confidence) {
        return new ExtractedValue(value, value, new BigDecimal(confidence));
    }

    public record ExtractedValue(String raw, String normalized, BigDecimal confidence) { }
    public record ExtractionOutcome(
            String text, Map<String, ExtractedValue> fields, BigDecimal confidence,
            String jobStatus, String extractor, String message) {
        static ExtractionOutcome pending(String status, String message) {
            return new ExtractionOutcome("", Map.of(), BigDecimal.ZERO, status, "METADATA_ONLY_V1", message);
        }
    }
}
