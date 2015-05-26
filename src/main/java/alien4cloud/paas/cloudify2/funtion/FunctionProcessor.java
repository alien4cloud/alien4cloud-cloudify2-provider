package alien4cloud.paas.cloudify2.funtion;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Resource;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.IndexedToscaElement;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.cloudify2.AlienExtentedConstants;
import alien4cloud.paas.cloudify2.generator.CommandGenerator;
import alien4cloud.paas.cloudify2.utils.CloudifyPaaSUtils;
import alien4cloud.paas.exception.NotSupportedException;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.tosca.normative.ToscaFunctionConstants;

/**
 * Utility class to process functions
 *
 * @author luc boutier
 */
@Component
@Slf4j
public class FunctionProcessor {

    @Resource
    private CommandGenerator cloudifyCommandGen;

    /**
     * Evaluate a parameter defined as a function.
     *
     * @param param The parameter object to evaluate
     * @param basePaaSTemplate The base PaaSTemplate in which the parameter is defined. Can be a {@link PaaSRelationshipTemplate} or a {@link PaaSNodeTemplate}.
     * @param builtPaaSTemplates A map < String, {@link PaaSNodeTemplate}> of built nodetemplates of the processed topology. Note that these
     *            {@link PaaSNodeTemplate}s should have been built, thus referencing their related parents and relationships.
     * @param instanceId The Id of the instance for which we are processing the inputs
     * @return
     *         StringEvalResult if the result of the valuation is a usable string value
     *         RuntimeEvalResult if the result of the evaluation is an expression to evaluate at runtime.
     */
    public IParamEvalResult evaluate(final AbstractPropertyValue param, final IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            final Map<String, PaaSNodeTemplate> builtPaaSTemplates, final String instanceId) {
        // if it is a scalar param, just return its value
        if (param instanceof ScalarPropertyValue) {
            return new StringEvalResult(((ScalarPropertyValue) param).getValue());
        }

        // if not, it is a function. evaluate it
        FunctionPropertyValue functionParam = (FunctionPropertyValue) param;

        // first validate the params.
        /* TODO: consider doing this in the tosca yaml parser */
        if (!validParameters(functionParam.getParameters())) {
            log.warn("Invalid parameters definition: < " + functionParam.getFunction() + functionParam.getParameters()
                    + ">. The resulted value will be set to <null>.");
            return new StringEvalResult();
        }

        String result = null;
        switch (functionParam.getFunction()) {
        case ToscaFunctionConstants.GET_PROPERTY:
            result = FunctionEvaluator.evaluateGetPropertyFunction(functionParam, basePaaSTemplate, builtPaaSTemplates);
            return new StringEvalResult(result);
        case ToscaFunctionConstants.GET_ATTRIBUTE:
            result = evaluateGetAttributeFunction(functionParam, basePaaSTemplate, builtPaaSTemplates, instanceId);
            return new RuntimeEvalResult(result);
        case ToscaFunctionConstants.GET_OPERATION_OUTPUT:
            // TODO: evaluate get_operation_output
            return new RuntimeEvalResult();
        default:
            throw new NotSupportedException("The function <" + functionParam.getFunction() + "> is not supported.");
        }
    }

    /**
     * Process and evaluate operation parameters.
     * Only process when the param is not a propertyDefinition
     *
     * @param inputParameters a Map String -> {@link IValue} representing operations to process
     * @param stringEvalResults a Map < {@link String}, {@link String}> containing the String type results directly usable
     * @param runtimeEvalResults a Map <{@link String}, {@link String}> containing the String type results to evaluate at runtime
     * @param basePaaSTemplate The base PaaSTemplate in which the parameter is defined. Can be a {@link PaaSRelationshipTemplate} or a {@link PaaSNodeTemplate}.
     * @param builtPaaSTemplates A map < {@link String}, {@link PaaSNodeTemplate}> of built nodetemplates of the processed topology. Note that these
     *            {@link PaaSNodeTemplate}s should have been built, thus referencing their related parents and relationships.
     * @param instanceId The Id of the instance for which we are processing the inputs
     */
    public void processParameters(Map<String, IValue> inputParameters, Map<String, String> stringEvalResults, Map<String, String> runtimeEvalResults,
            final IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate, final Map<String, PaaSNodeTemplate> builtPaaSTemplates, String instanceId) {
        if (inputParameters == null) {
            return;
        }
        for (Entry<String, IValue> paramEntry : inputParameters.entrySet()) {
            if (!paramEntry.getValue().isDefinition()) {
                IParamEvalResult evaluatedParam = evaluate((AbstractPropertyValue) paramEntry.getValue(), basePaaSTemplate, builtPaaSTemplates, instanceId);
                if (evaluatedParam instanceof StringEvalResult) {
                    stringEvalResults.put(paramEntry.getKey(), evaluatedParam.get());
                } else {
                    runtimeEvalResults.put(paramEntry.getKey(), evaluatedParam.get());
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private String evaluateGetAttributeFunction(FunctionPropertyValue functionParam, IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            Map<String, PaaSNodeTemplate> builtPaaSTemplates, String instanceId) {
        List<? extends IPaaSTemplate> paaSTemplates = FunctionEvaluator.getPaaSTemplatesFromKeyword(basePaaSTemplate, functionParam.getTemplateName(),
                builtPaaSTemplates);
        // getting the top hierarchical parent
        String serviceName = CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate((PaaSNodeTemplate) paaSTemplates.get(paaSTemplates.size() - 1));
        return evaluateAttributeName(functionParam.getElementNameToFetch(), serviceName, instanceId);
    }

    /* consider doing this in the tosca yaml parser */
    private boolean validParameters(List<String> parameters) {
        // TODO: throw a parsing error exception on the YAML parser when params size is less than 2.
        return CollectionUtils.isNotEmpty(parameters) && parameters.size() >= 2;
    }

    private String evaluateAttributeName(String attributeName, String serviceName, String instanceId) {
        switch (attributeName) {
        case AlienExtentedConstants.IP_ADDRESS:
            return cloudifyCommandGen.getIpCommand(serviceName, instanceId);
        default:
            return cloudifyCommandGen.getAttributeCommand(attributeName, serviceName, instanceId);
        }
    }

    public interface IParamEvalResult {
        /* retrieve the result of the evaluation */
        abstract String get();
    }

    /* the result of the evaluation is a usable String value */
    @NoArgsConstructor
    @AllArgsConstructor
    public class StringEvalResult implements IParamEvalResult {
        private String value;

        @Override
        public String get() {
            return value;
        }
    }

    /* The result of the evaluation is an expression to evaluate at runtime, recipe side */
    @NoArgsConstructor
    @AllArgsConstructor
    public class RuntimeEvalResult implements IParamEvalResult {
        private String value;

        @Override
        public String get() {
            return value;
        }
    }
}