package com.michelecossu.chords.transposer.web.controller;

import com.michelecossu.chords.transposer.service.ChordTransposeService;
import com.michelecossu.chords.transposer.web.exception.TargetKeyException;
import com.michelecossu.chords.transposer.web.request.TransposeRequest;
import com.michelecossu.chords.transposer.web.response.TransposeResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            byte[] pdfBytes = chordTransposeService.generatePdf(transposedContent, originalFileName, originalKey, request.targetKey());

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

}
