package alien4cloud.paas.cloudify2.recipeTests;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.exception.TechnicalException;
import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.cloudify2.CloudifyPaaSProvider;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.model.PaaSTopology;

@Component("recipe-gen-provider")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
public class RecipeGenProvider extends CloudifyPaaSProvider {
    @Override
    protected synchronized void doDeploy(String deploymentId, String deploymentPaaSId, Topology topology, PaaSTopology paaSTopology,
            DeploymentSetup deploymentSetup) {
        try {
            recipeGenerator.generateRecipe(deploymentId, deploymentPaaSId, paaSTopology, deploymentSetup);
        } catch (Exception e) {
            log.error("Deployment failed. Status will move to undeployed.", e);
            if (e instanceof TechnicalException) {
                throw (TechnicalException) e;
            }
            throw new PaaSDeploymentException("Deployment failure", e);
        }
    }
}
