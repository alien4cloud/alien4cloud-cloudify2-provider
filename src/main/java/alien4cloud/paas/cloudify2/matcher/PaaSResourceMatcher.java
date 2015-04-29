package alien4cloud.paas.cloudify2.matcher;

import java.util.Map;

import lombok.Getter;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.CloudImage;
import alien4cloud.model.cloud.CloudImageFlavor;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.HighAvailabilityComputeTemplate;
import alien4cloud.model.cloud.NetworkTemplate;
import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.paas.cloudify2.CloudifyComputeTemplate;
import alien4cloud.paas.exception.ResourceMatchingFailedException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.utils.MappingUtil;

import com.google.common.collect.Maps;

/**
 * Find a valid template based on the TOSCA node compute properties.
 */
@Component
@Getter
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class PaaSResourceMatcher {

    private Map<ComputeTemplate, String> alienTemplateToCloudifyTemplateMapping = Maps.newHashMap();
    private Map<HighAvailabilityComputeTemplate, String> alienTemplateToCloudifyHATemplateMapping = Maps.newHashMap();
    private Map<NetworkTemplate, String> alienNetworkToCloudifyNetworkMapping = Maps.newHashMap();
    private Map<StorageTemplate, String> alienStorageToCloudifyStorageMapping = Maps.newHashMap();
    private CloudResourceMatcherConfig cloudResourceMatcherConfig;

    /**
     * Match a cloudify template based on the compute node.
     *
     * @param computeNode The compute node. Could be a simple or a HA compute node
     * @return The template that matches the given compute node.
     */
    public synchronized String getTemplate(ComputeTemplate computeNode) {
        if (computeNode instanceof HighAvailabilityComputeTemplate) {
            return alienTemplateToCloudifyHATemplateMapping.get(computeNode);
        } else {
            return alienTemplateToCloudifyTemplateMapping.get(computeNode);
        }
    }

    /**
     * Match a cloudify network based on the network.
     *
     * @param network The network.
     * @return The template that matches the given network.
     */
    public synchronized String getNetwork(NetworkTemplate network) {
        return alienNetworkToCloudifyNetworkMapping.get(network);
    }

    /**
     * Match a cloudify storage based on the storage
     *
     * @param storage the storage
     * @return the template name which match the given storage
     */
    public synchronized String getStorage(StorageTemplate storage) {
        return alienStorageToCloudifyStorageMapping.get(storage);
    }

    public synchronized void configure(CloudResourceMatcherConfig config, Map<String, CloudifyComputeTemplate> paaSComputeTemplateMap) {
        this.cloudResourceMatcherConfig = config;
        Map<String, CloudImage> imageMapping = MappingUtil.getReverseMapping(config.getImageMapping());
        Map<String, CloudImageFlavor> flavorsMapping = MappingUtil.getReverseMapping(config.getFlavorMapping());

        for (Map.Entry<String, CloudifyComputeTemplate> paaSComputeTemplateEntry : paaSComputeTemplateMap.entrySet()) {
            String paaSResourceId = paaSComputeTemplateEntry.getKey();
            String iaaSImageId = paaSComputeTemplateEntry.getValue().getImageId();
            String iaaSFlavorId = paaSComputeTemplateEntry.getValue().getHardwareId();
            CloudImage alienCloudImage = imageMapping.get(iaaSImageId);
            CloudImageFlavor alienCloudImageFlavor = flavorsMapping.get(iaaSFlavorId);
            if (alienCloudImage != null && alienCloudImageFlavor != null) {
                alienTemplateToCloudifyTemplateMapping.put(new ComputeTemplate(alienCloudImage.getId(), alienCloudImageFlavor.getId(), paaSResourceId),
                        paaSResourceId);
            }
        }
        alienNetworkToCloudifyNetworkMapping = config.getNetworkMapping();
        alienStorageToCloudifyStorageMapping = config.getStorageMapping();
    }

    /**
     * Validate if a NodeTemplate is from a specific type
     *
     * @param node
     */
    public void verifyNode(PaaSNodeTemplate node, String type) {
        IndexedNodeType indexedNodeType = node.getIndexedToscaElement();
        if (!ToscaUtils.isFromType(type, indexedNodeType)) {
            throw new ResourceMatchingFailedException("Failed to match type <" + indexedNodeType.getElementId() + "> only <" + type + "> type is supported");
        }
    }
}