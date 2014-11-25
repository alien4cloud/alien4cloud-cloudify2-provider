package alien4cloud.paas.cloudify2;

import java.util.Map;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.component.model.IndexedNodeType;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.Network;
import alien4cloud.paas.exception.ResourceMatchingFailedException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.container.model.NormativeComputeConstants;
import alien4cloud.tosca.container.model.NormativeNetworkConstants;

import com.google.common.collect.Maps;

/**
 * Find a valid template based on the TOSCA node compute properties.
 */
@Slf4j
@Component
@Getter(value = AccessLevel.PROTECTED)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class PaaSResourceMatcher {

    private Map<ComputeTemplate, String> alienTemplateToCloudifyTemplateMapping = Maps.newHashMap();

    private Map<Network, String> alienNetworkToCloudifyNetworkMapping = Maps.newHashMap();

    /**
     * Match a cloudify template based on the compute node.
     *
     * @param computeNode The compute node.
     * @return The template that matches the given compute node.
     */
    public synchronized String getTemplate(ComputeTemplate computeNode) {
        return alienTemplateToCloudifyTemplateMapping.get(computeNode);
    }

    /**
     * Match a cloudify network based on the network.
     *
     * @param network The network.
     * @return The template that matches the given network.
     */
    public synchronized String getNetwork(Network network) {
        return alienNetworkToCloudifyNetworkMapping.get(network);
    }

    public synchronized void configure(CloudResourceMatcherConfig config) {
        alienTemplateToCloudifyTemplateMapping = config.getComputeTemplateMapping();
        alienNetworkToCloudifyNetworkMapping = config.getNetworkMapping();
    }

    /**
     * Validate if a NodeTemplate is a Compute type
     *
     * @param node
     */
    public void verifyNode(PaaSNodeTemplate node) {
        IndexedNodeType indexedNodeType = node.getIndexedNodeType();
        if (!AlienUtils.isFromNodeType(indexedNodeType, NormativeComputeConstants.COMPUTE_TYPE)) {
            throw new ResourceMatchingFailedException("Failed to match type <" + indexedNodeType.getElementId() + "> only <"
                    + NormativeComputeConstants.COMPUTE_TYPE + "> type is supported");
        }
    }

    public void verifyNetworkNode(PaaSNodeTemplate node) {
        IndexedNodeType indexedNodeType = node.getIndexedNodeType();
        if (!AlienUtils.isFromNodeType(indexedNodeType, NormativeNetworkConstants.NETWORK_TYPE)) {
            throw new ResourceMatchingFailedException("Failed to match type <" + indexedNodeType.getElementId() + "> only <"
                    + NormativeNetworkConstants.NETWORK_TYPE + "> type is supported");
        }
    }
}