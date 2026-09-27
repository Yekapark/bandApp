package com.yeka.bandapp.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** BCrypt의 72바이트 한도. 문자 수 제한과 별도로, 암호화 전에 필드 오류로 안내한다. */
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = PasswordByteLength.Validator.class)
public @interface PasswordByteLength {
    String message() default "비밀번호가 너무 길어요. 한글이나 특수문자가 있다면 더 짧게 입력해 주세요.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<PasswordByteLength, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            // 빈 값은 @NotBlank가 처리한다.
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }
}
