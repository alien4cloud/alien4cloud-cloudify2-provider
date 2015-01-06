package alien4cloud.paas.cloudify2.funtion;

import alien4cloud.paas.exception.PaaSTechnicalException;

public class FunctionProcessorException extends PaaSTechnicalException {

    private static final long serialVersionUID = 1L;

    public FunctionProcessorException(String message, Throwable cause) {
        super(message, cause);
    }

    public FunctionProcessorException(String message) {
        super(message);
    }

}
