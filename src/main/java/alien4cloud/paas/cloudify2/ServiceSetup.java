package alien4cloud.paas.cloudify2;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.NetworkTemplate;
import alien4cloud.model.cloud.StorageTemplate;

import com.google.common.collect.Maps;

@Getter
@Setter
public class ServiceSetup {
    private String id;
    private ComputeTemplate computeTemplate;
    private NetworkTemplate network;
    private StorageTemplate storage;
    private Map<String, String> providerDeploymentProperties = Maps.newHashMap();
    private String deploymentId;

}
