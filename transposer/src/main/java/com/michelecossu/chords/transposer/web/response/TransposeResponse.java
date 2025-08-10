package com.michelecossu.chords.transposer.web.response;

public record TransposeResponse(
        String originalContent,
        String transposedContent,
        String originalKey,
        String targetKey,
        int semitones,
        boolean success
) {}
