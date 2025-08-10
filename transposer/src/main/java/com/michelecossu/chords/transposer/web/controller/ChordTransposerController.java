package com.michelecossu.chords.transposer.web.controller;

import com.michelecossu.chords.transposer.service.ChordTransposeService;
import com.michelecossu.chords.transposer.web.request.TransposeRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
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
    public ResponseEntity<ByteArrayResource> createTransposedFile(@Valid @RequestBody TransposeRequest request) throws IOException {
        ByteArrayResource resource = chordTransposeService.generatePdfWithTransposedChords(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(resource.getFile().length())
                .body(resource);
    }

}
