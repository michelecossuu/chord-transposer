package com.michelecossu.chords.transposer.service;

import com.michelecossu.chords.transposer.web.request.RelativeTransposeRequest;
import com.michelecossu.chords.transposer.web.request.TransposeRequest;
import com.michelecossu.chords.transposer.web.response.RelativeTransposeResponse;
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

    /**
     * Transposes chords by a specified number of semitones relative to the original chords.
     *
     * @param request the request containing the original chords and the number of semitones to transpose
     * @return a RelativeTransposeResponse containing the details of the transposed file
     */
    RelativeTransposeResponse transposeChordsBySemitones(RelativeTransposeRequest request);
}
