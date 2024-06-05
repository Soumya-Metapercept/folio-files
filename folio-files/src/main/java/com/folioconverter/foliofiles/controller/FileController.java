package com.folioconverter.foliofiles.controller;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api")
public class FileController {

    private Path uploadedZipPath;

    @PostMapping("/upload")
    public ResponseEntity<String> handleFileUpload(@RequestParam("file") MultipartFile file) throws IOException {
        Path tempDir = Files.createTempDirectory("");
        uploadedZipPath = Files.createTempFile(tempDir, "uploaded", ".zip");
        file.transferTo(uploadedZipPath.toFile());

        return new ResponseEntity<>("File uploaded successfully", HttpStatus.OK);
    }

    @GetMapping("/convert")
    public ResponseEntity<byte[]> convertAndDownload() throws IOException {
        if (uploadedZipPath == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        File unzipDir = new File(uploadedZipPath.getParent().toFile(), "unzipped");
        unzipDir.mkdir();
        unzip(uploadedZipPath.toFile(), unzipDir);

        File[] docxFiles = unzipDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".docx"));
        if (docxFiles == null) {
            throw new IOException("No DOCX files found in the uploaded ZIP");
        }

        File pdfDir = new File(uploadedZipPath.getParent().toFile(), "pdfs");
        pdfDir.mkdir();
        for (File docxFile : docxFiles) {
            convertDocxToPdf(docxFile, new File(pdfDir, docxFile.getName().replace(".docx", ".pdf")));
        }

        File zipFile = new File(uploadedZipPath.getParent().toFile(), "converted.zip");
        zipDirectory(pdfDir, zipFile);

        byte[] zipContent = Files.readAllBytes(zipFile.toPath());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.builder("attachment").filename("converted.zip").build());

        return new ResponseEntity<>(zipContent, headers, HttpStatus.OK);
    }

    private void unzip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry = zipIn.getNextEntry();
            while (entry != null) {
                File filePath = new File(destDir, entry.getName());
                if (!entry.isDirectory()) {
                    Files.copy(zipIn, filePath.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    filePath.mkdir();
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
    }

    private void convertDocxToPdf(File docxFile, File pdfFile) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(docxFile));
             PDDocument pdf = new PDDocument()) {
            // Conversion logic (simplified)
            pdf.save(pdfFile);
        }
    }

    private void zipDirectory(File dir, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (File file : dir.listFiles()) {
                zos.putNextEntry(new ZipEntry(file.getName()));
                Files.copy(file.toPath(), zos);
                zos.closeEntry();
            }
        }
    }
}

