package alien4cloud.paas.cloudify2.event;

import java.util.Date;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import com.gigaspaces.annotation.pojo.SpaceId;
import com.gigaspaces.annotation.pojo.SpaceRouting;

public abstract class AlienEventDescription {

    private String id;

    private Integer eventIndex;

    private String applicationName;
    private String serviceName;
    private String instanceId;
    private String deploymentId;

    private Date dateTimestamp;

    public AlienEventDescription() {
    }

    public AlienEventDescription(String applicationName, String serviceName) {
        this.applicationName = applicationName;
        this.serviceName = serviceName;
    }

    @SpaceRouting
    @SpaceId(autoGenerate = true)
    private String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getEventIndex() {
        return eventIndex;
    }

    public void setEventIndex(Integer eventIndex) {
        this.eventIndex = eventIndex;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public Date getDateTimestamp() {
        return dateTimestamp;
    }

    public void setDateTimestamp(Date dateTimestamp) {
        this.dateTimestamp = dateTimestamp;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

}
