package alien4cloud.paas.cloudify2.exception;

import alien4cloud.paas.exception.PaaSTechnicalException;

public class PaaSEventException extends PaaSTechnicalException {

    private static final long serialVersionUID = -7285334798897608882L;

    public PaaSEventException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaaSEventException(String message) {
        super(message);
    }
}
