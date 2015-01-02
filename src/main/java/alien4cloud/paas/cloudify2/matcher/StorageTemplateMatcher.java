package alien4cloud.paas.cloudify2.matcher;

import java.util.Collections;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.paas.cloudify2.StorageTemplate;
import alien4cloud.paas.exception.ResourceMatchingFailedException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;

import com.google.common.collect.Lists;

/**
 * Find a valid storage template based on the TOSCA node BlockStorage properties.
 */
@Slf4j
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Getter
public class StorageTemplateMatcher {

    /** List of storage templates sorted by size. */
    private List<StorageTemplate> storageTemplates;

    /**
     * Match a cloudify storage template based on the blockstorage node.
     *
     * @param blockStorageNode The blockstorage node.
     * @return The template that matches the given compute node.
     */
    public String getTemplate(PaaSNodeTemplate blockStorageNode) {
        log.info("Matching template for storage node [{}]", blockStorageNode.getId());
        if (CollectionUtils.isEmpty(storageTemplates)) {
            throw new ResourceMatchingFailedException("Failed to find a storage that matches the expressed requirements for node <" + blockStorageNode.getId()
                    + ">. No storage templates are configured.");
        }

        this.verifyNode(blockStorageNode);

        List<StorageTemplate> candidates = Lists.newArrayList(storageTemplates);
        // filter based on size

        return filterOnIntegerPropertiesOrDie(blockStorageNode, candidates);
    }

    /**
     * Get the default storage template. It is the template with the lowest size
     *
     * @return The defaul template Id
     */
    public String getDefaultTemplate() {
        if (CollectionUtils.isEmpty(storageTemplates)) {
            throw new ResourceMatchingFailedException("Failed to get the a defaut storage. No storage templates are configured.");
        }
        List<StorageTemplate> candidates = Lists.newArrayList(storageTemplates);
        Collections.sort(candidates);
        return candidates.get(0).getId();
    }

    private String filterOnIntegerPropertiesOrDie(PaaSNodeTemplate blockstorageNode, List<StorageTemplate> candidates) {
        if (candidates.size() > 0) {
            log.info("Sorting candidates and filtering on resources.");
            Collections.sort(candidates);
            filterOnIntegerProp(candidates, NormativeBlockStorageConstants.SIZE, blockstorageNode, new PropGetter() {
                @Override
                public int getPropertyValue(StorageTemplate storageTemplate) {
                    return storageTemplate.getSize();
                }
            });
            if (candidates.size() == 0) {
                log.info("No more candidates.");
                throw new ResourceMatchingFailedException("Failed to find a storage that matches the expressed requirements (size) for node <"
                        + blockstorageNode.getId() + ">");
            }
            log.info("Valid candidate is [{}].", candidates.get(0).getId());
            return candidates.get(0).getId();
        }

        throw new ResourceMatchingFailedException("Failed to find a storage that matches the expressed requirements for node <" + blockstorageNode.getId()
                + ">");
    }

    private void verifyNode(PaaSNodeTemplate computeNode) {
        IndexedNodeType indexedNodeType = computeNode.getIndexedNodeType();
        if (!ToscaUtils.isFromType(NormativeBlockStorageConstants.BLOCKSTORAGE_TYPE, indexedNodeType)) {
            throw new ResourceMatchingFailedException("Failed to match type <" + indexedNodeType.getElementId() + "> only <"
                    + NormativeBlockStorageConstants.BLOCKSTORAGE_TYPE + "> type is supported");
        }
    }

    private void filterOnIntegerProp(List<StorageTemplate> candidates, String propKey, PaaSNodeTemplate computeNode, PropGetter propGetter) {
        String propStrValue = computeNode.getNodeTemplate().getProperties().get(propKey);
        if (StringUtils.isNotBlank(propStrValue)) {
            int propValue = Integer.valueOf(propStrValue);
            while (candidates.size() > 0 && propGetter.getPropertyValue(candidates.get(0)) < propValue) {
                candidates.remove(0);
            }
        }
    }

    private interface PropGetter {
        int getPropertyValue(StorageTemplate storageTemplate);
    }

    /**
     * Configure the template matcher with some templates.
     *
     * @param storageTemplates The list of storage templates to be used in the matcher.
     */
    public void configure(List<StorageTemplate> storageTemplates) {
        this.storageTemplates = storageTemplates;
    }
}