package alien4cloud.paas.cloudify2.funtion;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class FunctionPropertyEvaluator {
    //
    // public String evalutateInterfaceOperationLevel(DeploymentInfo deploymentInfo, String nodeId, String interfaceName, String operationName, String
    // paramName,
    // IOperationParameter param) {
    // String result = null;
    // //
    // // Topology topology = deploymentInfo.getTopology();
    // // Map<String, PaaSNodeTemplate> paaSNodeTemplates = deploymentInfo.getPaaSNodeTemplates();
    // // PaaSNodeTemplate paaSnodeTemp = paaSNodeTemplates.get(nodeId);
    //
    // return result;
    // }
}
