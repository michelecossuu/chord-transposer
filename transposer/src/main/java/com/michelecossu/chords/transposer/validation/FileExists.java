package com.michelecossu.chords.transposer.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = FileExistsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface FileExists {
    String message() default "File does not exist";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}