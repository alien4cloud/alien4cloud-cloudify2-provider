package alien4cloud.paas.cloudify2.exception;

import alien4cloud.exception.TechnicalException;


/**
 * Exception which is triggered by IT test it-self and not alien
 *
 * @author mkv
 */
public class A4CCloudifyDriverITException extends TechnicalException {

    private static final long serialVersionUID = 1970578297756505647L;

    public A4CCloudifyDriverITException(String message, Throwable cause) {
        super(message, cause);
    }

    public A4CCloudifyDriverITException(String message) {
        super(message);
    }

}
