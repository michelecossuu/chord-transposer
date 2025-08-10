package com.michelecossu.chords.transposer.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TransposeRequest(
        @NotBlank String sourceFileName,
        @NotBlank @Pattern(regexp = "^[A-G][#b]?[m]?$") String targetKey
) {}
