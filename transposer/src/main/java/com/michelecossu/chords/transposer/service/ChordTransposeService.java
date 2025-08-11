package com.michelecossu.chords.transposer.service;

import com.michelecossu.chords.transposer.web.request.TransposeRequest;
import com.michelecossu.chords.transposer.web.response.TransposeResponse;
import java.io.IOException;

public interface ChordTransposeService {

    /**
     * Generates a PDF file with transposed chords based on the provided request.
     *
     * @param request the request containing the original chords and the desired transposition
     * @return a TransposeResponse containing the details of the transposed file
     * @throws IOException if an error occurs during PDF generation
     */
    TransposeResponse generatePdfWithTransposedChords(TransposeRequest request) throws IOException;
}
