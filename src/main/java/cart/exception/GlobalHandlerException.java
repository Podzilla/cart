package cart.exception;

import org.springframework.http.HttpStatus;

public class GlobalHandlerException extends
        RuntimeException {

    private final HttpStatus status;

    public GlobalHandlerException(final
                                  HttpStatus status, final String message) {
        super(message);
        this.status = status;
    }

    public GlobalHandlerException(final
                                  HttpStatus status, final String message,
                                  final Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
