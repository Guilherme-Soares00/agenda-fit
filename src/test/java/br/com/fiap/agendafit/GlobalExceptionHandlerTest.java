package br.com.fiap.agendafit;

import java.lang.reflect.Method;
import java.util.Arrays;

import br.com.fiap.agendafit.dto.ErrorResponse;
import br.com.fiap.agendafit.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void sanitizesUnexpectedFailuresAndMapsDatabaseConflicts() throws Exception {
        assertMapping(new IllegalStateException("private database detail"), Exception.class, 500,
                "An unexpected error occurred");
        assertMapping(new DataIntegrityViolationException("private constraint detail"), DataIntegrityViolationException.class,
                409, "Request conflicts with existing data");
        assertMapping(new PessimisticLockingFailureException("private lock detail"), PessimisticLockingFailureException.class,
                409, "Resource is being updated; please try again");
    }

    @Test
    void preservesKnownHttpStatusesWithoutExposingFrameworkDetails() throws Exception {
        ResponseEntity<?> response = assertMapping(new HttpRequestMethodNotSupportedException("private method detail"),
                Exception.class, 405, "Method Not Allowed");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    private ResponseEntity<?> assertMapping(Exception exception, Class<? extends Exception> mappedType, int status, String message) throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var method = Arrays.stream(handler.getClass().getMethods())
                .filter(candidate -> candidate.isAnnotationPresent(ExceptionHandler.class))
                .filter(candidate -> Arrays.asList(candidate.getAnnotation(ExceptionHandler.class).value()).contains(mappedType))
                .findFirst();
        assertThat(method).as(mappedType.getSimpleName()).isPresent();
        Method target = method.orElseThrow();
        Object result = target.getParameterCount() == 0 ? target.invoke(handler) : target.invoke(handler, exception);
        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse(message));
        return response;
    }
}
