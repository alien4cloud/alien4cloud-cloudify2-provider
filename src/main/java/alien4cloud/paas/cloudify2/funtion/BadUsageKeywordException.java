package alien4cloud.paas.cloudify2.funtion;

import alien4cloud.tosca.container.ToscaFunctionConstants;

/**
 * Exception thrown when using a {@link ToscaFunctionConstants} keyword in a bad way.
 *
 * @author 'Igor Ngouagna'
 *
 */
public class BadUsageKeywordException extends FunctionProcessorException {

    private static final long serialVersionUID = 1L;

    public BadUsageKeywordException(String message, Throwable cause) {
        super(message, cause);
    }

    public BadUsageKeywordException(String message) {
        super(message);
    }

}
