package alien4cloud.paas.cloudify2.funtion;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import alien4cloud.component.model.IndexedToscaElement;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.cloudify2.CloudifyPaaSUtils;
import alien4cloud.paas.cloudify2.generator.CloudifyCommandGenerator;
import alien4cloud.paas.exception.NotSupportedException;
import alien4cloud.paas.exception.PaaSTechnicalException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.tosca.container.ToscaFunctionConstants;
import alien4cloud.tosca.model.AbstractPropertyValue;
import alien4cloud.tosca.model.FunctionPropertyValue;
import alien4cloud.tosca.model.IOperationParameter;
import alien4cloud.tosca.model.ScalarPropertyValue;

/**
 * Utility class to process functions
 *
 * @author luc boutier
 */
@Slf4j
@Component
public class FunctionProcessor {

    @Resource
    private CloudifyCommandGenerator cloudifyCommandGen;

    private static final String IP_ADDRESS = "ip_address";

    /**
     * Evaluate a parameter defined as a function.
     *
     * @param param The parameter object to evaluate
     * @param basePaaSTemplate The base PaaSTemplate in which the parameter is defined. Can be a {@link PaaSRelationshipTemplate} or a {@link PaaSNodeTemplate}.
     * @param builtPaaSTemplates A map < String, {@link PaaSNodeTemplate}> of built nodetemplates of the processed topology. Note that these
     *            {@link PaaSNodeTemplate}s should have been built, thus referencing their related parents and relationships.
     * @return
     *         StringEvalResult if the result of the valuation is a usable string value
     *         RuntimeEvalResult if the result of the evaluation is an expression to evaluate at runtime.
     */
    private IParamEvalResult evaluate(final AbstractPropertyValue param, final IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            final Map<String, PaaSNodeTemplate> builtPaaSTemplates) {
        // if it is a scalar param, just return its value
        if (param instanceof ScalarPropertyValue) {
            return new StringEvalResult(((ScalarPropertyValue) param).getValue());
        }

        // if not, it is a function. evaluate it
        FunctionPropertyValue functionParam = (FunctionPropertyValue) param;

        // first validate the params.
        /* TODO: consider doing this in the tosca yaml parser */
        if (!validParameters(functionParam.getParameters())) {
            return null;
        }

        String result = null;
        switch (functionParam.getFunction()) {
        case ToscaFunctionConstants.GET_PROPERTY:
            result = evaluateGetPropertyFuntion(functionParam, basePaaSTemplate, builtPaaSTemplates); // process getProperty
            return new StringEvalResult(result);
        case ToscaFunctionConstants.GET_ATTRIBUTE:
            result = evaluateGetAttributeFunction(functionParam, basePaaSTemplate, builtPaaSTemplates);
            return new RuntimeEvalResult(result);
        default:
            throw new NotSupportedException("The function <" + functionParam.getFunction() + "> is not supported.");
        }
    }

    /**
     * Evaluate a parameter defined as a function.
     *
     * @param param The parameter object to evaluate
     * @param basePaaSTemplate The base PaaSTemplate in which the parameter is defined. Can be a {@link PaaSRelationshipTemplate} or a {@link PaaSNodeTemplate}.
     * @param builtPaaSTemplates A map < String, {@link PaaSNodeTemplate}> of built nodetemplates of the processed topology. Note that these
     *            {@link PaaSNodeTemplate}s should have been built, thus referencing their related parents and relationships.
     * @return
     *         The String result of the evaluation. Can be a String value, or an another expression to evaluate at the runtime on the recipe side.
     */
    public String evaluateParam(final AbstractPropertyValue param, final IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            final Map<String, PaaSNodeTemplate> builtPaaSTemplates) {
        IParamEvalResult result = evaluate(param, basePaaSTemplate, builtPaaSTemplates);
        return result != null ? result.get() : null;
    }

    /**
     * Process and evaluate operation parameters.
     * Only process when the param is not a propertyDefinition
     *
     * @param inputParameters a Map String -> {@link IOperationParameter} representing operations to process
     * @param stringEvalResults a Map < {@link String}, {@link String}> containing the String type results directly usable
     * @param runtimeEvalResults a Map <{@link String}, {@link String}> containing the String type results to evaluate at runtime
     * @param basePaaSTemplate The base PaaSTemplate in which the parameter is defined. Can be a {@link PaaSRelationshipTemplate} or a {@link PaaSNodeTemplate}.
     * @param builtPaaSTemplates A map < {@link String}, {@link PaaSNodeTemplate}> of built nodetemplates of the processed topology. Note that these
     *            {@link PaaSNodeTemplate}s should have been built, thus referencing their related parents and relationships.
     */
    public void processParameters(Map<String, IOperationParameter> inputParameters, Map<String, String> stringEvalResults,
            Map<String, String> runtimeEvalResults, final IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            final Map<String, PaaSNodeTemplate> builtPaaSTemplates) {
        if (inputParameters == null) {
            return;
        }
        for (Entry<String, IOperationParameter> paramEntry : inputParameters.entrySet()) {
            if (!paramEntry.getValue().isDefinition()) {
                IParamEvalResult evaluatedParam = evaluate((AbstractPropertyValue) paramEntry.getValue(), basePaaSTemplate, builtPaaSTemplates);
                if (evaluatedParam instanceof StringEvalResult) {
                    stringEvalResults.put(paramEntry.getKey(), evaluatedParam.get());
                } else {
                    runtimeEvalResults.put(paramEntry.getKey(), evaluatedParam.get());
                }
            }
        }
    }

    private String evaluateEntityName(String stringToEval, IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate) {
        switch (stringToEval) {
        case ToscaFunctionConstants.HOST:
            return getHostNodeId(basePaaSTemplate);
        case ToscaFunctionConstants.SELF:
            return getSelfNodeId(basePaaSTemplate);
        case ToscaFunctionConstants.SOURCE:
            return getSourceNodeId(basePaaSTemplate);
        case ToscaFunctionConstants.TARGET:
            return getTargetNodeId(basePaaSTemplate);
        default:
            return stringToEval;
        }
    }

    private String getSelfNodeId(IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate) {
        if (basePaaSTemplate instanceof PaaSNodeTemplate) {
            return basePaaSTemplate.getId();
        }
        throw new BadUsageKeywordException("The keyword <" + ToscaFunctionConstants.SELF + "> can only be used on a NodeTemplate level's parameter. Node<"
                + basePaaSTemplate.getId() + ">");
    }

    private String getHostNodeId(IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate) {
        if (!(basePaaSTemplate instanceof PaaSNodeTemplate)) {
            throw new BadUsageKeywordException("The keyword <" + ToscaFunctionConstants.HOST + "> can only be used on a NodeTemplate level's parameter. Node<"
                    + basePaaSTemplate.getId() + ">");
        }
        try {
            return CloudifyPaaSUtils.getHostTemplate((PaaSNodeTemplate) basePaaSTemplate).getId();
        } catch (PaaSTechnicalException e) {
            throw new FunctionProcessorException("Failed to retrieve the root node of <" + basePaaSTemplate.getId() + ">.", e);
        }
    }

    private String getSourceNodeId(IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate) {
        if (basePaaSTemplate instanceof PaaSRelationshipTemplate) {
            return ((PaaSRelationshipTemplate) basePaaSTemplate).getSource();
        }
        throw new BadUsageKeywordException("The keyword <" + ToscaFunctionConstants.SOURCE + "> can only be used on a Relationship level's parameter. Node<"
                + basePaaSTemplate.getId() + ">");
    }

    private String getTargetNodeId(IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate) {
        if (basePaaSTemplate instanceof PaaSRelationshipTemplate) {
            return ((PaaSRelationshipTemplate) basePaaSTemplate).getRelationshipTemplate().getTarget();
        }
        throw new BadUsageKeywordException("The keyword <" + ToscaFunctionConstants.TARGET + "> can only be used on a Relationship level's parameter. Node<"
                + basePaaSTemplate.getId() + ">.");
    }

    private String evaluateGetPropertyFuntion(FunctionPropertyValue functionParam, IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            Map<String, PaaSNodeTemplate> builtPaaSTemplates) {
        PaaSNodeTemplate entity = getPaaSEntity(basePaaSTemplate, functionParam.getParameters(), builtPaaSTemplates);

        return entity.getNodeTemplate().getProperties() == null ? null : entity.getNodeTemplate().getProperties().get(functionParam.getParameters().get(1));
    }

    private PaaSNodeTemplate getPaaSEntity(IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate, List<String> parameters,
            Map<String, PaaSNodeTemplate> builtPaaSTemplates) {
        String entityName = evaluateEntityName(parameters.get(0), basePaaSTemplate);
        // TODO: handle the case basePaaSTemplate is a paaSRelationshipTemplate
        // TODO: handle the case params size greater than 2. That means we have to retrieve the property on a requirement or a capability
        PaaSNodeTemplate entity = getPaaSNodeOrDie(entityName, builtPaaSTemplates);
        return entity;
    }

    private String evaluateGetAttributeFunction(FunctionPropertyValue functionParam, IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            Map<String, PaaSNodeTemplate> builtPaaSTemplates) {
        PaaSNodeTemplate entity = getPaaSEntity(basePaaSTemplate, functionParam.getParameters(), builtPaaSTemplates);
        String serviceName = CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(CloudifyPaaSUtils.getHostTemplate(entity));
        return evaluateAttributeName(functionParam.getParameters().get(1), serviceName);
    }

    /* consider doing this in the tosca yaml parser */
    private boolean validParameters(List<String> parameters) {
        // TODO: throw a parsing error exception on the YAML parser when params size is less than 2.
        return CollectionUtils.isNotEmpty(parameters) && parameters.size() >= 2;
    }

    private String evaluateAttributeName(String attributeName, String serviceName) {
        switch (attributeName) {
        case IP_ADDRESS:
            return cloudifyCommandGen.getIpCommand(serviceName, null);
        default:
            return cloudifyCommandGen.getAttributeCommand(attributeName, serviceName, null);
        }
    }

    private PaaSNodeTemplate getPaaSNodeOrDie(String nodeId, Map<String, PaaSNodeTemplate> builtPaaSTemplates) {
        PaaSNodeTemplate toReturn = builtPaaSTemplates.get(nodeId);
        if (toReturn == null) {
            throw new FunctionProcessorException(" Failled to retrieve the nodeTemplate with name <" + nodeId + ">");
        }
        return toReturn;
    }

    private interface IParamEvalResult {
        /* retrieve the result of the evaluation */
        abstract String get();
    }

    /* the result of the evaluation is a usable String value */
    private class StringEvalResult implements IParamEvalResult {
        private String value;

        public StringEvalResult(String result) {
            value = result;
        }

        @Override
        public String get() {
            return value;
        }
    }

    /* The result of the evaluation is an expression to evaluate at runtime, recipe side */
    private class RuntimeEvalResult implements IParamEvalResult {
        private String value;

        public RuntimeEvalResult(String result) {
            value = result;
        }

        @Override
        public String get() {
            return value;
        }
    }
}