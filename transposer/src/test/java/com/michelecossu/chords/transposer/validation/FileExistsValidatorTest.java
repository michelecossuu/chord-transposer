package com.michelecossu.chords.transposer.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileExistsValidatorTest {

    private FileExistsValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder builder;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder;

    private final String testDirectory = System.getProperty("java.io.tmpdir");
    private Path testFile;
    private Path testDirectory2;

    @BeforeEach
    void setUp() throws IOException {
        validator = new FileExistsValidator();
        ReflectionTestUtils.setField(validator, "filesDirectory", testDirectory);

        // Create a test file
        testFile = Files.createTempFile("test-file", ".txt");

        // Create a test directory
        testDirectory2 = Files.createTempDirectory("test-dir");

        // Mock chaining for custom error messages
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);
        when(nodeBuilder.addConstraintViolation()).thenReturn(context);
    }

    @Test
    @DisplayName("isValid with existing file returns true")
    void isValid_withExistingFile_returnsTrue() {
        String fileName = testFile.getFileName().toString();

        boolean result = validator.isValid(fileName, context);

        // Assert
        assertTrue(result);
        verifyNoInteractions(builder);
    }

    @Test
    @DisplayName("isValid with non-existent file returns false")
    void isValid_withNonExistentFile_returnsFalse() {
        String fileName = "non-existent-file.txt";

        boolean result = validator.isValid(fileName, context);

        // Assert
        assertFalse(result);
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("File does not exist"));
    }

    @Test
    @DisplayName("isValid with non-regular file returns false")
    void isValid_withDirectory_returnsFalse() {
        String fileName = testDirectory2.getFileName().toString();

        boolean result = validator.isValid(fileName, context);

        // Assert
        assertFalse(result);
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("Not a regular file"));
    }

    @Test
    @DisplayName("isValid with valid file but exception returns false")
    void isValid_withException_returnsFalse() {
        boolean result = validator.isValid(null, context);

        // Assert
        assertFalse(result);
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("File Name cannot be null or blank"));
    }

    @Test
    @DisplayName("isValid with relative path traversal handles path correctly")
    void isValid_withRelativePathTraversal_handlesPathCorrectly() {
        String fileName = "../some-file.txt";

        boolean result = validator.isValid(fileName, context);

        // Assert
        assertFalse(result);
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("File does not exist"));
    }
}