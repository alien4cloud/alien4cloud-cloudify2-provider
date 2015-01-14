package alien4cloud.paas.cloudify2.funtion;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Resource;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IOperationParameter;
import alien4cloud.model.components.IndexedToscaElement;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.cloudify2.generator.CommandGenerator;
import alien4cloud.paas.cloudify2.utils.CloudifyPaaSUtils;
import alien4cloud.paas.exception.NotSupportedException;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.ToscaFunctionConstants;

/**
 * Utility class to process functions
 *
 * @author luc boutier
 */
@Component
public class FunctionProcessor {

    @Resource
    private CommandGenerator cloudifyCommandGen;

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
    public IParamEvalResult evaluate(final AbstractPropertyValue param, final IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
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
                result = FunctionEvaluator.evaluateGetPropertyFuntion(functionParam, basePaaSTemplate, builtPaaSTemplates); // process getProperty
                return new StringEvalResult(result);
            case ToscaFunctionConstants.GET_ATTRIBUTE:
                result = evaluateGetAttributeFunction(functionParam, basePaaSTemplate, builtPaaSTemplates);
                return new RuntimeEvalResult(result);
            default:
                throw new NotSupportedException("The function <" + functionParam.getFunction() + "> is not supported.");
        }
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

    private String evaluateGetAttributeFunction(FunctionPropertyValue functionParam, IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            Map<String, PaaSNodeTemplate> builtPaaSTemplates) {
        PaaSNodeTemplate entity = FunctionEvaluator.getPaaSEntity(basePaaSTemplate, functionParam.getParameters().get(0), builtPaaSTemplates);
        String serviceName = CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(ToscaUtils.getHostTemplate(entity));
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

    public interface IParamEvalResult {
        /* retrieve the result of the evaluation */
        abstract String get();
    }

    /* the result of the evaluation is a usable String value */
    public class StringEvalResult implements IParamEvalResult {
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
    public class RuntimeEvalResult implements IParamEvalResult {
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