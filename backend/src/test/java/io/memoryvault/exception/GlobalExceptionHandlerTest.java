package io.memoryvault.exception;

import io.memoryvault.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleApiException_returnsCorrectStatusAndErrorCode() {
        ApiException ex = new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");

        ResponseEntity<ApiResponse<Void>> response = handler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("ERROR");
        assertThat(response.getBody().message()).isEqualTo("User not found");
        assertThat(response.getBody().errorCode()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void handleValidation_joinsErrorsAndReturnsBadRequest() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError error1 = new FieldError("object", "email", "must not be blank");
        FieldError error2 = new FieldError("object", "password", "must be at least 8 chars");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                mock(MethodParameter.class), bindingResult
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).contains("email: must not be blank", "password: must be at least 8 chars");
    }

    @Test
    void handleMalformedJson_returnsBadRequestWithErrorCode() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMalformedJson(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("MALFORMED_JSON");
        assertThat(response.getBody().message()).isEqualTo("Malformed JSON request body");
    }

    @Test
    void handleMethodNotAllowed_returns405() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getBody().message()).contains("Method not allowed: POST");
    }

    @Test
    void handleMissingParam_returnsBadRequest() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("q", "String");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingParam(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("MISSING_PARAMETER");
        assertThat(response.getBody().message()).contains("Required parameter missing: q");
    }

    @Test
    void handleConstraintViolation_formatsViolations() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> cv = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("size");
        when(cv.getPropertyPath()).thenReturn(path);
        when(cv.getMessage()).thenReturn("must be less than or equal to 100");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(cv));

        ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).contains("size: must be less than or equal to 100");
    }

    @Test
    void handleGeneric_returnsInternalServerError() {
        Exception ex = new RuntimeException("Unexpected DB drop");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }
}
