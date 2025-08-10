package com.michelecossu.chords.transposer.service;

import java.io.IOException;

public interface ChordTransposeService {

    String readFile(String fileName) throws IOException;
    String transposeContent(String content, int semitones);
    int calculateSemitones(String fromKey, String toKey);
    String detectKey(String content);
    byte[] generatePdf(String content, String sourceFileName, String originalKey, String targetKey) throws IOException;
}
