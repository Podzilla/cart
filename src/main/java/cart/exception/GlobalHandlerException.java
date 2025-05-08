package cart.exception;

import org.springframework.http.HttpStatus;

public class GlobalHandlerException extends
        RuntimeException {

    private final HttpStatus status;

    public GlobalHandlerException(HttpStatus status,
                                  String message) {
        super(message);
        this.status = status;
    }

    public GlobalHandlerException(HttpStatus status,
                                  String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
