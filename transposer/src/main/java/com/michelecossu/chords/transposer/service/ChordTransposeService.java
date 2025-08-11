package com.michelecossu.chords.transposer.service;

import com.michelecossu.chords.transposer.web.request.TransposeRequest;
import org.springframework.core.io.ByteArrayResource;
import java.io.IOException;

public interface ChordTransposeService {

    /**
     * Generates a PDF file with transposed chords based on the provided request.
     *
     * @param request the request containing the original chords and the desired transposition
     * @return a ByteArrayResource containing the generated PDF file
     * @throws IOException if an error occurs during PDF generation
     */
    ByteArrayResource generatePdfWithTransposedChords(TransposeRequest request) throws IOException;
}
