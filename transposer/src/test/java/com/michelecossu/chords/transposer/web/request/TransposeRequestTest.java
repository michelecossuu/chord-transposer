package com.michelecossu.chords.transposer.web.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TransposeRequest Validation Tests")
class TransposeRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        // Create validator without FileExistsValidator to isolate targetKey validation
        HibernateValidatorConfiguration config =
                jakarta.validation.Validation.byProvider(HibernateValidator.class).configure();

        ConstraintMapping mapping = config.createConstraintMapping();

        ValidatorFactory factory = config.addMapping(mapping).buildValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    @DisplayName("Target Key Validation")
    class TargetKeyValidation {

        @ParameterizedTest
        @ValueSource(strings = {
                "C", "D", "E", "F", "G", "A", "B",           // Natural keys
                "C#", "D#", "F#", "G#", "A#",               // Sharp keys
                "Db", "Eb", "Gb", "Ab", "Bb",               // Flat keys
                "Cm", "Dm", "Em", "Fm", "Gm", "Am", "Bm",   // Minor natural keys
                "C#m", "D#m", "F#m", "G#m", "A#m",          // Minor sharp keys
                "Dbm", "Ebm", "Gbm", "Abm", "Bbm"           // Minor flat keys
        })
        @DisplayName("Should accept valid musical keys")
        void shouldAcceptValidMusicalKeys(String validKey) {
            // Given
            TransposeRequest request = new TransposeRequest("valid-file.txt", validKey);

            // When
            Set<ConstraintViolation<TransposeRequest>> violations =
                    validator.validateProperty(request, "targetKey");

            // Then
            assertTrue(violations.isEmpty(),
                    () -> "Key '" + validKey + "' should be valid but got violations: " + violations);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Should reject null, empty, or blank target keys")
        void shouldRejectNullEmptyOrBlankTargetKeys(String invalidKey) {
            // Given
            TransposeRequest request = new TransposeRequest("valid-file.txt", invalidKey);

            // When
            Set<ConstraintViolation<TransposeRequest>> violations =
                    validator.validateProperty(request, "targetKey");

            // Then
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                            .anyMatch(v -> v.getMessage().contains("Target key required")),
                    "Should contain 'Target key required' message");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "H", "I", "J",                    // Invalid note names
                "CC", "DD", "AA",                 // Double letters
                "C##", "D##", "Ebb",              // Double accidentals
                "Cb", "Fb", "E#", "B#",           // Theoretically invalid (though musically possible)
                "c", "d", "e",                    // Lowercase notes
                "c#", "db", "em",                 // Lowercase with accidentals/minor
                "C major", "D minor",             // Full key names
                "CM", "Dm7", "C7",                // Chord notations
                "1", "2", "3",                    // Numbers
                "C/E", "D/F#",                    // Slash chords
                "C-major", "D_minor",             // Hyphen/underscore
                "C ", " D", " E ",                // Leading/trailing spaces
                "",                               // Empty string (covered above)
                "Cmaj", "Dmin",                   // Extended abbreviations
                "Do", "Re", "Mi"                  // Solfege notation
        })
        @DisplayName("Should reject invalid key formats")
        void shouldRejectInvalidKeyFormats(String invalidKey) {
            // Given
            TransposeRequest request = new TransposeRequest("valid-file.txt", invalidKey);

            // When
            Set<ConstraintViolation<TransposeRequest>> violations =
                    validator.validateProperty(request, "targetKey");

            // Then
            assertFalse(violations.isEmpty(),
                    () -> "Key '" + invalidKey + "' should be invalid but was accepted");

            // Verify it's specifically a pattern violation (not @NotBlank)
            boolean hasPatternViolation = violations.stream()
                    .anyMatch(v -> v.getConstraintDescriptor().getAnnotation() instanceof Pattern);

            if (!invalidKey.trim().isEmpty()) {
                assertTrue(hasPatternViolation,
                        () -> "Non-empty invalid key '" + invalidKey + "' should fail pattern validation");
            }
        }
    }

    @Nested
    @DisplayName("Record Structure and Immutability")
    class RecordStructureAndImmutability {

        @Test
        @DisplayName("Should create record with valid parameters")
        void shouldCreateRecordWithValidParameters() {
            // Given
            String sourceFileName = "test-file.txt";
            String targetKey = "C#m";

            // When
            TransposeRequest request = new TransposeRequest(sourceFileName, targetKey);

            // Then
            assertAll(
                    () -> assertEquals(sourceFileName, request.sourceFileName()),
                    () -> assertEquals(targetKey, request.targetKey()),
                    () -> assertNotNull(request.toString()),
                    () -> assertTrue(request.toString().contains(sourceFileName)),
                    () -> assertTrue(request.toString().contains(targetKey))
            );
        }

        @Test
        @DisplayName("Should maintain immutability characteristics")
        void shouldMaintainImmutabilityCharacteristics() {
            // Given
            TransposeRequest request1 = new TransposeRequest("file1.txt", "C");
            TransposeRequest request2 = new TransposeRequest("file1.txt", "C");
            TransposeRequest request3 = new TransposeRequest("file2.txt", "C");

            // Then
            assertAll(
                    () -> assertEquals(request1, request2, "Same content should be equal"),
                    () -> assertNotEquals(request1, request3, "Different content should not be equal"),
                    () -> assertEquals(request1.hashCode(), request2.hashCode(), "Same content should have same hash"),
                    () -> assertNotEquals(request1.hashCode(), request3.hashCode(), "Different content should have different hash")
            );
        }
    }

    @Nested
    @DisplayName("Integration with Bean Validation")
    class IntegrationWithBeanValidation {

        @Test
        @DisplayName("Should validate entire object with all constraints")
        void shouldValidateEntireObjectWithAllConstraints() {
            // Given
            TransposeRequest invalidRequest = new TransposeRequest("", "invalid-key");

            // When
            Set<ConstraintViolation<TransposeRequest>> invalidViolations =
                    validator.validate(invalidRequest);

            // Then
            // Note: Valid request might still have FileExists violations in real scenario
            // but we're focusing on the pattern validation here
            assertFalse(invalidViolations.isEmpty(), "Invalid request should have violations");

            // Verify targetKey pattern violation exists
            assertTrue(invalidViolations.stream()
                            .anyMatch(v -> v.getPropertyPath().toString().equals("targetKey")),
                    "Should have targetKey violations");
        }

        @Test
        @DisplayName("Should provide meaningful validation messages")
        void shouldProvideMeaningfulValidationMessages() {
            // Given
            TransposeRequest request = new TransposeRequest("file.txt", null);

            // When
            Set<ConstraintViolation<TransposeRequest>> violations =
                    validator.validateProperty(request, "targetKey");

            // Then
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                            .anyMatch(v -> v.getMessage().equals("Target key required")),
                    "Should contain custom validation message");
        }
    }

    @Nested
    @DisplayName("Musical Theory Edge Cases")
    class MusicalTheoryEdgeCases {

        @Test
        @DisplayName("Should handle enharmonic equivalents consistently")
        void shouldHandleEnharmonicEquivalentsConsistently() {
            // Given - Enharmonic equivalents (same pitch, different names)
            String[] enharmonicPairs = {
                    "C#", "Db",
                    "D#", "Eb",
                    "F#", "Gb",
                    "G#", "Ab",
                    "A#", "Bb"
            };

            // When & Then
            for (String key : enharmonicPairs) {
                TransposeRequest request = new TransposeRequest("file.txt", key);
                Set<ConstraintViolation<TransposeRequest>> violations =
                        validator.validateProperty(request, "targetKey");

                assertTrue(violations.isEmpty(),
                        () -> "Enharmonic key '" + key + "' should be valid");
            }
        }

        @Test
        @DisplayName("Should validate both major and minor variations")
        void shouldValidateBothMajorAndMinorVariations() {
            // Given
            String[] baseKeys = {"C", "D", "E", "F", "G", "A", "B"};

            // When & Then
            for (String baseKey : baseKeys) {
                // Test major (implicit)
                TransposeRequest majorRequest = new TransposeRequest("file.txt", baseKey);
                Set<ConstraintViolation<TransposeRequest>> majorViolations =
                        validator.validateProperty(majorRequest, "targetKey");

                // Test minor (explicit)
                TransposeRequest minorRequest = new TransposeRequest("file.txt", baseKey + "m");
                Set<ConstraintViolation<TransposeRequest>> minorViolations =
                        validator.validateProperty(minorRequest, "targetKey");

                assertAll(
                        () -> assertTrue(majorViolations.isEmpty(),
                                () -> "Major key '" + baseKey + "' should be valid"),
                        () -> assertTrue(minorViolations.isEmpty(),
                                () -> "Minor key '" + baseKey + "m' should be valid")
                );
            }
        }

        @Test
        @DisplayName("Should reject extended or complex key notations")
        void shouldRejectExtendedOrComplexKeyNotations() {
            // Given - Complex musical notations that exceed simple major/minor keys
            String[] complexNotations = {
                    "Cmaj7", "Dm7", "G7", "Am7b5",           // Extended chords
                    "C/E", "F/A", "G/B",                     // Slash chords
                    "Csus4", "Dsus2", "Esus",                // Suspended chords
                    "Cadd9", "Dadd11",                       // Added tone chords
                    "C6", "Dm6", "G6",                       // Sixth chords
                    "Cmaj9", "Dm9", "G13",                   // Extended jazz chords
                    "C°", "D°", "Edim",                      // Diminished
                    "C+", "D+", "Eaug",                      // Augmented
                    "C/major", "D/minor"                     // Verbose notation
            };

            // When & Then
            for (String complexKey : complexNotations) {
                TransposeRequest request = new TransposeRequest("file.txt", complexKey);
                Set<ConstraintViolation<TransposeRequest>> violations =
                        validator.validateProperty(request, "targetKey");

                assertFalse(violations.isEmpty(),
                        () -> "Complex notation '" + complexKey + "' should be rejected");
            }
        }
    }

    @Nested
    @DisplayName("Boundary and Stress Tests")
    class BoundaryAndStressTests {

        @Test
        @DisplayName("Should handle maximum valid key length")
        void shouldHandleMaximumValidKeyLength() {
            // Given - Longest valid key format is 3 characters (e.g., "A#m")
            String maxLengthKey = "A#m";

            // When
            TransposeRequest request = new TransposeRequest("file.txt", maxLengthKey);
            Set<ConstraintViolation<TransposeRequest>> violations =
                    validator.validateProperty(request, "targetKey");

            // Then
            assertTrue(violations.isEmpty(),
                    "Maximum length valid key should be accepted");
        }

        @Test
        @DisplayName("Should reject keys exceeding maximum length")
        void shouldRejectKeysExceedingMaximumLength() {
            // Given - Keys that are too long
            String[] tooLongKeys = {
                    "A#major", "Dbminor", "C#m7", "Fmaj7"
            };

            // When & Then
            for (String longKey : tooLongKeys) {
                TransposeRequest request = new TransposeRequest("file.txt", longKey);
                Set<ConstraintViolation<TransposeRequest>> violations =
                        validator.validateProperty(request, "targetKey");

                assertFalse(violations.isEmpty(),
                        () -> "Overly long key '" + longKey + "' should be rejected");
            }
        }

        @Test
        @DisplayName("Should validate regex pattern precision")
        void shouldValidateRegexPatternPrecision() {
            // Given - Test the exact regex boundaries
            String[] exactMatches = {"C", "C#", "Dm", "F#m", "Gb", "Abm"};
            String[] shouldFail = {
                    // Empty or invalid keys
                    "", "CC", "C##", "Cmm", "C#b", "C#bm",

                    // Explicitly excluded keys
                    "Cb", "Fb", "E#", "B#",
                    "Cbm", "Fbm", "E#m", "B#m"
            };

            // When & Then
            for (String exactMatch : exactMatches) {
                TransposeRequest request = new TransposeRequest("file.txt", exactMatch);
                Set<ConstraintViolation<TransposeRequest>> violations =
                        validator.validateProperty(request, "targetKey");

                assertTrue(violations.isEmpty(),
                        () -> "Exact pattern match '" + exactMatch + "' should be valid");
            }

            for (String shouldFailKey : shouldFail) {
                TransposeRequest request = new TransposeRequest("file.txt", shouldFailKey);
                Set<ConstraintViolation<TransposeRequest>> violations =
                        validator.validateProperty(request, "targetKey");

                assertFalse(violations.isEmpty(),
                        () -> "Pattern mismatch '" + shouldFailKey + "' should be invalid");
            }
        }
    }
}