package com.michelecossu.chords.transposer.web.response;

public record RelativeTransposeResponse(
        String originalFileName,
        String transposedFileName,
        int semitones,
        boolean success
) {}
