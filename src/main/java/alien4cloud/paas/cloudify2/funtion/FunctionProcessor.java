package alien4cloud.paas.cloudify2.funtion;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Resource;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import alien4cloud.common.AlienConstants;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ConcatPropertyValue;
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
import alien4cloud.utils.AlienUtils;

import com.google.common.collect.Lists;

/**
 * Utility class to process functions
 *
 * @author luc boutier
 */
@Component
@Slf4j
@SuppressWarnings("rawtypes")
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
     * @throws IOException
     */
    public IParamEvalResult evaluate(final AbstractPropertyValue param, final IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            final Map<String, PaaSNodeTemplate> builtPaaSTemplates, final String instanceId) throws IOException {

        // if it is a scalar param, just return its value
        if (param instanceof ScalarPropertyValue) {
            return new StringEvalResult(((ScalarPropertyValue) param).getValue());
        }

        // concat function is not supported as operations intputs
        if (param instanceof ConcatPropertyValue) {
            log.warn("function : < " + ((ConcatPropertyValue) param).getFunction_concat() + ">. not supported as operation input parameter.");
            return new StringEvalResult();
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
            result = evaluateGetOperationOutputFunction(functionParam, basePaaSTemplate, builtPaaSTemplates, instanceId);
            return new RuntimeEvalResult(result);
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
     * @throws IOException
     */
    public void processParameters(Map<String, IValue> inputParameters, Map<String, String> stringEvalResults, Map<String, String> runtimeEvalResults,
            final IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate, final Map<String, PaaSNodeTemplate> builtPaaSTemplates, String instanceId)
            throws IOException {
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

    private String evaluateGetAttributeFunction(FunctionPropertyValue functionParam, IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            Map<String, PaaSNodeTemplate> builtPaaSTemplates, String instanceId) {
        List<? extends IPaaSTemplate> paaSTemplates = FunctionEvaluator.getPaaSTemplatesFromKeyword(basePaaSTemplate, functionParam.getTemplateName(),
                builtPaaSTemplates);
        // getting the top hierarchical parent
        String serviceName = CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate((PaaSNodeTemplate) paaSTemplates.get(paaSTemplates.size() - 1));
        return evaluateAttributeName(functionParam.getElementNameToFetch(), serviceName, instanceId);
    }

    private String evaluateGetOperationOutputFunction(FunctionPropertyValue functionParam, IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            Map<String, PaaSNodeTemplate> builtPaaSTemplates, String instanceId) throws IOException {
        List<? extends IPaaSTemplate> paaSTemplates = FunctionEvaluator.getPaaSTemplatesFromKeyword(basePaaSTemplate, functionParam.getTemplateName(),
                builtPaaSTemplates);

        // getting the top hierarchical parent
        IPaaSTemplate paaSTemplate = paaSTemplates.get(paaSTemplates.size() - 1);
        String serviceName = null;

        // might be a PaaSRelationshipTemplate. In this case, there is no host
        if (paaSTemplate instanceof PaaSNodeTemplate) {
            serviceName = CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate((PaaSNodeTemplate) paaSTemplate);
        }

        return evaluateOperationOutputName(functionParam, serviceName, instanceId, paaSTemplates);
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

    private String evaluateOperationOutputName(FunctionPropertyValue function, String serviceName, String instanceId,
            List<? extends IPaaSTemplate> paaSTemplates) throws IOException {

        // NOTE: to be able to search an output through all possibles parents of a node, we should give to the executor a list of eligibles nodes names and a
        // relative qualified name of the output (interfaceName:operationName:output)
        // EX: for two parents A and B, and an output output, we want to look for the outputs name: A:interfaceName:operationName:output and
        // B:interfaceName:operationName:output

        List<String> nodeNames = Lists.newArrayList();
        for (IPaaSTemplate iPaaSTemplate : paaSTemplates) {
            nodeNames.add(iPaaSTemplate.getId());
        }
        // Output relative qualified name ==> interfaceName:operationName:output
        String outputRQN = AlienUtils.prefixWith(AlienConstants.COLON_SEPARATOR, function.getElementNameToFetch(), new String[] { function.getInterfaceName(),
                function.getOperationName() });
        return cloudifyCommandGen.getOperationOutputCommand(outputRQN, nodeNames, serviceName, instanceId);
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