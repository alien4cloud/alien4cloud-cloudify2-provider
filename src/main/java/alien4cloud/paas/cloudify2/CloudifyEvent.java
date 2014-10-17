package alien4cloud.paas.cloudify2;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

@Getter
@Setter
public class CloudifyEvent {

    private String id;

    private Integer eventIndex;

    private String applicationName;
    private String serviceName;
    private String instanceId;
    private String deploymentId;
    private String event;

    private Date dateTimestamp;

    public CloudifyEvent() {
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

}
