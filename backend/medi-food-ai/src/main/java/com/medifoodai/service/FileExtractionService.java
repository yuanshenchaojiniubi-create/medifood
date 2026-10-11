package com.medifoodai.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Service
public class FileExtractionService {

    private static final int MAX_TEXT_LENGTH = 8000;

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "";
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            filename = "";
        }
        String lower = filename.toLowerCase();

        try {
            if (lower.endsWith(".pdf")) {
                return extractPdf(file);
            } else if (lower.endsWith(".docx")) {
                return extractDocx(file);
            } else if (lower.endsWith(".txt")) {
                return extractTxt(file);
            } else {
                return "";
            }
        } catch (Exception e) {
            log.error("文件文本提取失败: filename={}", filename, e);
            return "";
        }
    }

    public boolean isSupportedFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".txt");
    }

    private String extractPdf(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             PDDocument doc = Loader.loadPDF(is.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            return truncate(text);
        }
    }

    private String extractDocx(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            return truncate(text);
        }
    }

    private String extractTxt(MultipartFile file) throws Exception {
        String text = new String(file.getBytes(), "UTF-8");
        return truncate(text);
    }

    private String truncate(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) + "\n...(内容过长已截断)" : text;
    }
}