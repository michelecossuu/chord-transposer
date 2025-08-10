package com.michelecossu.chords.transposer.web.controller;

import com.michelecossu.chords.transposer.service.ChordTransposeService;
import com.michelecossu.chords.transposer.web.exception.TargetKeyException;
import com.michelecossu.chords.transposer.web.request.TransposeRequest;
import com.michelecossu.chords.transposer.web.response.TransposeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping(path = "/api/v1/transpose")
public class ChordTransposerController {

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

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

}
