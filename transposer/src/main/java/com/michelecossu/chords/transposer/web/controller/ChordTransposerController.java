package com.michelecossu.chords.transposer.web.controller;

import com.michelecossu.chords.transposer.service.ChordTransposeService;
import com.michelecossu.chords.transposer.web.exception.TargetKeyException;
import com.michelecossu.chords.transposer.web.request.TransposeRequest;
import com.michelecossu.chords.transposer.web.response.TransposeResponse;
import jakarta.validation.Valid;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping(path = "/api/v1/transpose")
public class ChordTransposerController {

    @Value("${app.files.directory}")
    private String filesDirectory;

    private final ChordTransposeService chordTransposeService;

    public ChordTransposerController(ChordTransposeService chordTransposeService) {
        this.chordTransposeService = chordTransposeService;
    }

    @PostMapping("/file")
    public ResponseEntity<TransposeResponse> transposeChords(@Valid @RequestBody TransposeRequest request) {
        try {
            // Read the file
            String originalContent = chordTransposeService.readFile(request.sourceFileName());

            // Retrieve the original key
            String originalKey = chordTransposeService.detectKey(originalContent);

            // Calculate the number of semitones to transpose
            int semitones;
            if (request.targetKey() != null && !request.targetKey().isEmpty()) {
                semitones = chordTransposeService.calculateSemitones(originalKey, request.targetKey());
            } else {
                throw new TargetKeyException("Target key is null or empty");
            }

            // Execute the transposition
            String transposedContent = chordTransposeService.transposeContent(originalContent, semitones);

            // Create the response object
            TransposeResponse response = new TransposeResponse(originalContent, transposedContent, originalKey,
                    request.targetKey(), semitones, true);
            return ResponseEntity.ok(response);

        } catch (IOException _) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);

        } catch (IllegalArgumentException _) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);

        } catch (Exception _) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/create-file")
    public ResponseEntity<ByteArrayResource> createTransposedFile(@Valid @RequestBody TransposeRequest request) {
        try {
            // Read the file
            String originalContent = chordTransposeService.readFile(request.sourceFileName());

            // Retrieve the original key
            String originalKey = chordTransposeService.detectKey(originalContent);

            // Calculate the number of semitones to transpose
            int semitones;
            if (request.targetKey() != null && !request.targetKey().isEmpty()) {
                semitones = chordTransposeService.calculateSemitones(originalKey, request.targetKey());
            } else {
                throw new TargetKeyException("Target key is null or empty");
            }

            // Execute the transposition
            String transposedContent = chordTransposeService.transposeContent(originalContent, semitones);

            // Generate new file name
            String originalFileName = request.sourceFileName();
            String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
            String outputFileName = baseName + "_" + request.targetKey() + ".pdf";

            // Generate PDF with transposed content
            byte[] pdfBytes = generatePdf(transposedContent, originalFileName, originalKey, request.targetKey());

            // Save PDF to local filesystem
            Path outputDirectory = Paths.get(filesDirectory);
            Files.createDirectories(outputDirectory); // Create directories if they don't exist
            Path outputPath = outputDirectory.resolve(outputFileName);
            Files.write(outputPath, pdfBytes);

            // Create resource for download
            ByteArrayResource resource = new ByteArrayResource(pdfBytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + outputFileName + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdfBytes.length)
                    .body(resource);
        } catch (IOException _) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        } catch (IllegalArgumentException _) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(null);
        } catch (Exception _) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    private byte[] generatePdf(String content, String sourceFileName, String originalKey, String targetKey) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            // Set title with sanitized text
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
            contentStream.newLineAtOffset(50, 750);
            String baseName = sourceFileName.substring(0, sourceFileName.lastIndexOf('.'));
            String titleText = sanitizeText(baseName + " (" + originalKey + " → " + targetKey + ")");
            contentStream.showText(titleText);
            contentStream.endText();

            // Set content
            contentStream.beginText();
            contentStream.setFont(PDType1Font.COURIER, 11);
            contentStream.setLeading(14); // Line spacing
            contentStream.newLineAtOffset(50, 720);

            // Process content line by line
            String[] lines = content.split("\n");
            float yPosition = 720;

            for (String line : lines) {
                // Sanitize each line
                String sanitizedLine = sanitizeText(line);

                // Check if we need a new page
                if (yPosition < 50) {
                    contentStream.endText();
                    contentStream.close();

                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);

                    contentStream = new PDPageContentStream(document, page);

                    contentStream.beginText();
                    contentStream.setFont(PDType1Font.COURIER, 11);
                    contentStream.setLeading(14);
                    yPosition = 750;
                    contentStream.newLineAtOffset(50, yPosition);
                }

                // Handle long lines
                if (sanitizedLine.length() > 100) {
                    for (int i = 0; i < sanitizedLine.length(); i += 100) {
                        String subLine = sanitizedLine.substring(i, Math.min(i + 100, sanitizedLine.length()));
                        contentStream.showText(subLine);
                        contentStream.newLine();
                        yPosition -= 14;
                    }
                } else {
                    contentStream.showText(sanitizedLine);
                    contentStream.newLine();
                    yPosition -= 14;
                }
            }

            contentStream.endText();
            contentStream.close();

            document.save(baos);
        }

        return baos.toByteArray();
    }

    /**
     * Sanitizes text to ensure compatibility with PDF standard fonts
     */
    private String sanitizeText(String text) {
        if (text == null) return "";

        // Replace special characters that might cause issues
        return text.replaceAll("[^ -~]", " ") // Replace non-ASCII printable chars with spaces
                .replace("→", "->")                  // Replace arrow with ASCII equivalent
                .replace("\t", "    ");              // Replace tabs with spaces
    }

}
