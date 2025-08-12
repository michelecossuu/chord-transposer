package com.michelecossu.chords.transposer.web.request;

import com.michelecossu.chords.transposer.validation.FileExists;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TransposeRequest(
        @FileExists String sourceFileName,
        @NotBlank(message = "Target key required")
        @Pattern(
                regexp = "^(?!Cb$|Fb$|E#$|B#$|Cbm$|Fbm$|E#m$|B#m$)[A-G][#b]?m?$",
                message = "Invalid key format"
        ) String targetKey
) {}
