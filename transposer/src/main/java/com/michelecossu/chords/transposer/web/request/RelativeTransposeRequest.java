package com.michelecossu.chords.transposer.web.request;

import com.michelecossu.chords.transposer.validation.FileExists;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RelativeTransposeRequest(
        @FileExists String sourceFileName,
        @NotNull(message = "Semitones must be provided")
        @Min(value = 1, message = "Semitones must be between 1 and 11")
        @Max(value = 11, message = "Semitones must be between 1 and 11")
        Integer semitones
) {}