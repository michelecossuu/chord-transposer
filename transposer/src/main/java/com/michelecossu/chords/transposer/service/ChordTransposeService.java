package com.michelecossu.chords.transposer.service;

import com.michelecossu.chords.transposer.web.request.TransposeRequest;
import org.springframework.core.io.ByteArrayResource;
import java.io.IOException;

public interface ChordTransposeService {
    ByteArrayResource generatePdfWithTransposedChords(TransposeRequest request) throws IOException;
}
