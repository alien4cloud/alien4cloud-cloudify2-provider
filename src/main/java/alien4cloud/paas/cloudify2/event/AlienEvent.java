package alien4cloud.paas.cloudify2.event;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class AlienEvent extends AlienEventDescription {

    private String event;

    public AlienEvent() {
    }

    public AlienEvent(String applicationName, String serviceName, String event) {
        super(applicationName, serviceName);
        this.event = event;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

}
