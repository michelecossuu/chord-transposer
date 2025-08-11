package com.michelecossu.chords.transposer.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class FileExistsValidator implements ConstraintValidator<FileExists, String> {

    @Value("${app.files.directory}")
    private String filesDirectory;

    @Override
    public boolean isValid(String fileName, ConstraintValidatorContext context) {
        try {
            Path filePath = Paths.get(filesDirectory, fileName).normalize();

            if (!Files.exists(filePath)) {
                addCustomConstraintViolation(context, "File does not exist: " + fileName);
                return false;
            }

            if (!Files.isRegularFile(filePath)) {
                addCustomConstraintViolation(context, "Not a regular file: " + fileName);
                return false;
            }

            return true;
        } catch (Exception e) {
            addCustomConstraintViolation(context, "Error validating file: " + e.getMessage());
            return false;
        }
    }

    private void addCustomConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode("sourceFileName")
                .addConstraintViolation();
    }

}