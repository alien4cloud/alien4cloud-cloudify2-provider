package alien4cloud.paas.cloudify2;

import java.util.Collections;
import java.util.List;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.component.model.IndexedNodeType;
import alien4cloud.paas.exception.ResourceMatchingFailedException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.container.model.NormativeComputeConstants;

import com.google.common.collect.Lists;

/**
 * Find a valid template based on the TOSCA node compute properties.
 */
@Slf4j
@Component
@Getter(value = AccessLevel.PROTECTED)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ComputeTemplateMatcher {

    /** List of compute templates sorted by memory, cpu and disk. */
    private List<ComputeTemplate> computeTemplates;

    /**
     * Match a cloudify template based on the compute node.
     * 
     * @param computeNode The compute node.
     * @return The template that matches the given compute node.
     */
    public String getTemplate(PaaSNodeTemplate computeNode) {
        log.info("Matching template for root node [{}]", computeNode.getId());
        if (computeTemplates == null || computeTemplates.size() == 0) {
            throw new ResourceMatchingFailedException("Failed to find a compute that matches the expressed requirements for node <" + computeNode.getId()
                    + ">. No templates are configured.");
        }

        this.verifyNode(computeNode);

        // if ip is defined we should try to match based on ip as this is the configuration for BYON clouds.
        String ip = computeNode.getNodeTemplate().getProperties().get(NormativeComputeConstants.IP_ADDRESS);
        if (ip != null) {
            // try to find a ComputeTemplate matching the given ip.
            // TODO Ajout dynamique du template dans cloudify

            for (ComputeTemplate computeTemplate : computeTemplates) {
                if (ip.equals(computeTemplate.getIpAddress())) {
                    return computeTemplate.getId();
                }
            }
            throw new ResourceMatchingFailedException("Failed to find a compute that matches the expressed requirements ("
                    + NormativeComputeConstants.IP_ADDRESS + " = " + ip + ") for node <" + computeNode.getId() + ">");
        }
        log.info("Matching template / No IP - filtering template candidates");
        // if the matching is not ip-based we should find a node that matches the expressed requirements.
        List<ComputeTemplate> candidates = Lists.newArrayList(computeTemplates);
        filterProperty(computeNode.getNodeTemplate().getProperties().get(NormativeComputeConstants.OS_TYPE), "osType", candidates);
        filterProperty(computeNode.getNodeTemplate().getProperties().get(NormativeComputeConstants.OS_ARCH), "osArch", candidates);
        filterProperty(computeNode.getNodeTemplate().getProperties().get(NormativeComputeConstants.OS_DISTRIBUTION), "osDistribution", candidates);
        filterProperty(computeNode.getNodeTemplate().getProperties().get(NormativeComputeConstants.OS_VERSION), "osVersion", candidates);
        // finally based on memory, cpu and disk
        return filterOnIntegerPropertiesOrDie(computeNode, candidates);
    }

    private String filterOnIntegerPropertiesOrDie(PaaSNodeTemplate computeNode, List<ComputeTemplate> candidates) {
        if (candidates.size() > 0) {
            log.info("Sorting candidates and filtering on resources.");
            Collections.sort(candidates);
            filterOnIntegerProp(candidates, NormativeComputeConstants.MEM_SIZE, computeNode, new PropGetter() {
                @Override
                public int getPropertyValue(ComputeTemplate computeTemplate) {
                    return computeTemplate.getMemSize();
                }
            });
            filterOnIntegerProp(candidates, NormativeComputeConstants.NUM_CPUS, computeNode, new PropGetter() {
                @Override
                public int getPropertyValue(ComputeTemplate computeTemplate) {
                    return computeTemplate.getNumCpus();
                }
            });
            filterOnIntegerProp(candidates, NormativeComputeConstants.DISK_SIZE, computeNode, new PropGetter() {
                @Override
                public int getPropertyValue(ComputeTemplate computeTemplate) {
                    return computeTemplate.getDiskSize();
                }
            });
            if (candidates.size() == 0) {
                log.info("No more candidates.");
                throw new ResourceMatchingFailedException(
                        "Failed to find a compute that matches the expressed requirements (memory, cpu, disk size) for node <" + computeNode.getId() + ">");

            }
            log.info("Valid candidate is [{}].", candidates.get(0).getId());
            return candidates.get(0).getId();
        }

        throw new ResourceMatchingFailedException("Failed to find a compute that matches the expressed requirements for node <" + computeNode.getId() + ">");
    }

    private void verifyNode(PaaSNodeTemplate computeNode) {
        IndexedNodeType indexedNodeType = computeNode.getIndexedNodeType();
        if (!AlienUtils.isFromNodeType(indexedNodeType, NormativeComputeConstants.COMPUTE_TYPE)) {
            throw new ResourceMatchingFailedException("Failed to match type <" + indexedNodeType.getElementId() + "> only <"
                    + NormativeComputeConstants.COMPUTE_TYPE + "> type is supported");
        }
    }

    private void filterOnIntegerProp(List<ComputeTemplate> candidates, String propKey, PaaSNodeTemplate computeNode, PropGetter propGetter) {
        String propStrValue = computeNode.getNodeTemplate().getProperties().get(propKey);
        if (StringUtils.isNotBlank(propStrValue)) {
            int propValue = Integer.valueOf(propStrValue);
            while (candidates.size() > 0 && propGetter.getPropertyValue(candidates.get(0)) < propValue) {
                candidates.remove(0);
            }
        }
    }

    private interface PropGetter {
        int getPropertyValue(ComputeTemplate computeTemplate);
    }

    private void filterProperty(String expectedValue, String fieldName, List<ComputeTemplate> candidates) {
        if (expectedValue == null) {
            return;
        }
        log.info("Filter expects from template the value <{}> on field <{}>", expectedValue, fieldName);
        int i = 0;
        while (i < candidates.size()) {
            ComputeTemplate compute = candidates.get(i);
            log.info("[{}]: Try to filter expected value <{}> on field <{}>. current is <{}>", compute.getId(), expectedValue, fieldName, compute.getValue(fieldName));
            if (!expectedValue.equals(compute.getValue(fieldName))) {
                candidates.remove(i);
                log.info("Filtered.");
            } else {
                i++;
                log.info("Not filtered.");
            }
        }
    }

    /**
     * Configure the template matcher with some compute templates.
     * 
     * @param computeTemplates The list of compute templates to be used in the matcher.
     */
    public void configure(List<ComputeTemplate> computeTemplates) {
        this.computeTemplates = computeTemplates;
    }
}