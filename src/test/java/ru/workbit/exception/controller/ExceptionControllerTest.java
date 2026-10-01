package ru.workbit.exception.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

@DisplayName("ExceptionControllerTest")
class ExceptionControllerTest {

    private final ExceptionController controller = new ExceptionController();

    @Nested
    @DisplayName("HandleSpringValidation")
    class HandleSpringValidation {

        private static final String OBJECT_NAME = "request";
        private static final String FIELD = "email";

        @Test
        @DisplayName("Подставляет сгенерированное сообщение, когда у ошибки поля нет своего")
        void fallsBackToGeneratedMessageWhenFieldErrorHasNone() throws Exception {
            // given
            var exception = methodArgumentNotValid(new FieldError(OBJECT_NAME, FIELD, null, false, null, null, null));

            // when
            var response = controller.handleSpringValidation(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            var body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.message()).isEqualTo("Validation Failed");
            assertThat(body.errors()).containsExactly("Invalid value for field 'email'");
        }

        @Test
        @DisplayName("Использует сообщение ошибки поля, когда оно задано")
        void usesFieldErrorMessageWhenPresent() throws Exception {
            // given
            var exception = methodArgumentNotValid(new FieldError(OBJECT_NAME, FIELD, "must not be blank"));

            // when
            var response = controller.handleSpringValidation(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            var body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.message()).isEqualTo("Validation Failed");
            assertThat(body.errors()).containsExactly("must not be blank");
        }

        private MethodArgumentNotValidException methodArgumentNotValid(FieldError fieldError) throws Exception {
            BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), OBJECT_NAME);
            bindingResult.addError(fieldError);
            var parameter = new MethodParameter(
                    ExceptionController.class.getMethod("handleSpringValidation", Exception.class), 0);
            return new MethodArgumentNotValidException(parameter, bindingResult);
        }
    }
}
