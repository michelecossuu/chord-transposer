package com.michelecossu.chords.transposer.web.controller;

import com.michelecossu.chords.transposer.service.ChordTransposeService;
import com.michelecossu.chords.transposer.web.request.RelativeTransposeRequest;
import com.michelecossu.chords.transposer.web.request.TransposeRequest;
import com.michelecossu.chords.transposer.web.response.RelativeTransposeResponse;
import com.michelecossu.chords.transposer.web.response.TransposeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    @PostMapping("/create-file")
    public ResponseEntity<TransposeResponse> createTransposedFile(@Valid @RequestBody TransposeRequest request) throws IOException {
        TransposeResponse response = chordTransposeService.generatePdfWithTransposedChords(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + response.transposedFileName() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @PostMapping("/transpose-by-semitones")
    public ResponseEntity<RelativeTransposeResponse> transposeByRelativeSemitones(
            @Valid @RequestBody RelativeTransposeRequest request) {

        RelativeTransposeResponse response = chordTransposeService.transposeChordsBySemitones(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + response.transposedFileName() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

}
