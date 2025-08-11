package com.michelecossu.chords.transposer.web.request;

import com.michelecossu.chords.transposer.validation.FileExists;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TransposeRequest(
        @NotBlank(message = "Source file name is required") @FileExists String sourceFileName,
        @NotBlank(message = "Target key required") @Pattern(regexp = "^[A-G][#b]?m?$") String targetKey
) {}
