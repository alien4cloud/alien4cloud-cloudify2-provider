package alien4cloud.paas.cloudify2;

import com.gigaspaces.annotation.pojo.SpaceId;
import com.gigaspaces.annotation.pojo.SpaceRouting;

/**
 * Contains the latest instance state of a node in a TOSCA topology.
 */
public class NodeInstanceState {
    private String topologyId;
    private String nodeTemplateId;
    private String instanceId;
    private String instanceState;

    @SpaceRouting
    @SpaceId(autoGenerate = false)
    public String getId() {
        if (topologyId == null || nodeTemplateId == null || instanceId == null) {
            return null;
        }
        return topologyId + "-" + nodeTemplateId + "-" + instanceId;
    }

    public void setId(String id) {
    }

    public String getTopologyId() {
        return topologyId;
    }

    public void setTopologyId(String topologyId) {
        this.topologyId = topologyId;
    }

    public String getNodeTemplateId() {
        return nodeTemplateId;
    }

    public void setNodeTemplateId(String nodeTemplateId) {
        this.nodeTemplateId = nodeTemplateId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceState() {
        return instanceState;
    }

    public void setInstanceState(String instanceState) {
        this.instanceState = instanceState;
    }
}