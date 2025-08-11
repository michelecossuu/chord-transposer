package com.michelecossu.chords.transposer.web.response;

public record TransposeResponse(
        String originalFileName,
        String transposedFileName,
        String originalKey,
        String targetKey,
        boolean success
) {}
