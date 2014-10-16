package alien4cloud.paas.cloudify2.event;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class BlockStorageEvent extends AlienEvent {

    private String volumeId;

    public BlockStorageEvent() {
    }

    public BlockStorageEvent(String applicationName, String serviceName, String event, String volumeId) {
        super(applicationName, serviceName, event);
        this.volumeId = volumeId;
    }

    public String getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(String volumeId) {
        this.volumeId = volumeId;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

}
