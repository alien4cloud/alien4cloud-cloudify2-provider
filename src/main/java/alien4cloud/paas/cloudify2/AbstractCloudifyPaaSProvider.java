package alien4cloud.paas.cloudify2;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.cloudifysource.dsl.internal.CloudifyConstants;
import org.cloudifysource.dsl.internal.CloudifyConstants.DeploymentState;
import org.cloudifysource.dsl.internal.CloudifyConstants.USMState;
import org.cloudifysource.dsl.rest.request.InstallApplicationRequest;
import org.cloudifysource.dsl.rest.request.InvokeCustomCommandRequest;
import org.cloudifysource.dsl.rest.request.SetServiceInstancesRequest;
import org.cloudifysource.dsl.rest.response.ApplicationDescription;
import org.cloudifysource.dsl.rest.response.DeploymentEvent;
import org.cloudifysource.dsl.rest.response.InstanceDescription;
import org.cloudifysource.dsl.rest.response.InvokeInstanceCommandResponse;
import org.cloudifysource.dsl.rest.response.InvokeServiceCommandResponse;
import org.cloudifysource.dsl.rest.response.ServiceDescription;
import org.cloudifysource.dsl.rest.response.ServiceInstanceDetails;
import org.cloudifysource.dsl.rest.response.UninstallApplicationResponse;
import org.cloudifysource.dsl.rest.response.UploadResponse;
import org.cloudifysource.restclient.RestClient;
import org.cloudifysource.restclient.exceptions.RestClientException;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.exception.TechnicalException;
import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.IOperationParameter;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.ScalingPolicy;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.cloudify2.events.AlienEvent;
import alien4cloud.paas.cloudify2.events.BlockStorageEvent;
import alien4cloud.paas.cloudify2.events.NodeInstanceState;
import alien4cloud.paas.cloudify2.generator.RecipeGenerator;
import alien4cloud.paas.cloudify2.utils.CloudifyPaaSUtils;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.PaaSAlreadyDeployedException;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.exception.PaaSNotYetDeployedException;
import alien4cloud.paas.exception.PaaSTechnicalException;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStateMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStorageMonitorEvent;
import alien4cloud.paas.model.PaaSMessageMonitorEvent;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@Slf4j
public abstract class AbstractCloudifyPaaSProvider implements IConfigurablePaaSProvider<PluginConfigurationBean> {

    @Resource
    @Getter
    protected CloudifyRestClientManager cloudifyRestClientManager;
    @Resource
    @Getter
    protected RecipeGenerator recipeGenerator;
    @Resource(name = "alien-monitor-es-dao")
    private IGenericSearchDAO alienMonitorDao;
    @Resource
    private TopologyTreeBuilderService topologyTreeBuilderService;

    private static final long TIMEOUT_IN_MILLIS = 1000L * 60L * 10L; // 10 minutes
    private static final long MAX_DEPLOYMENT_TIMEOUT_MILLIS = 1000L * 60L * 5L; // 5 minutes
    private static final long DEFAULT_SLEEP_TIME = 5000L;
    private static final boolean DEFAULT_SELF_HEALING = true;

    private static final String INVOCATION_INSTANCE_ID_KEY = "Invocation_Instance_ID";
    private static final String INVOCATION_RESULT_KEY = "Invocation_Result";
    private static final String INVOCATION_SUCCESS_KEY = "Invocation_Success";
    private static final String INVOCATION_SUCCESS = "SUCCESS";
    private static final String INVOCATION_EXCEPTION_KEY = "Invocation_Exception";
    // private static final String INVOCATION_INSTANCE_NAME_KEY = "Invocation_Instance_Name";
    // private static final String INVOCATION_COMMAND_NAME_KEY = "Invocation_Command_Name";

    protected Map<String, DeploymentInfo> statusByDeployments = Maps.newHashMap();

    protected Map<String, InstanceDeploymentInfo> instanceStatusByDeployments = Maps.newHashMap();

    private Queue<AbstractMonitorEvent> monitorEvents = new ConcurrentLinkedQueue<>();

    @PostConstruct
    private void init() {
        // Call a protected method to be able to override it.
        // This is more clearer and avoid implementation errors.
        configureDefault();
    }

    protected abstract PluginConfigurationBean getPluginConfigurationBean();

    /**
     * Method called by @PostConstruct at initialization step.
     *
     */
    protected void configureDefault() {
        log.info("Configuring the paaS provider");
    }

    @Override
    public void deploy(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        doDeploy(deploymentContext.getRecipeId(), deploymentContext.getDeploymentId(), deploymentContext.getTopology(), deploymentContext.getPaaSTopology()
                .getComputes(), deploymentContext.getPaaSTopology().getAllNodes(), deploymentContext.getDeploymentSetup());
    }

    protected synchronized void doDeploy(String deploymentName, String deploymentId, Topology topology, List<PaaSNodeTemplate> roots,
            Map<String, PaaSNodeTemplate> nodeTemplates, DeploymentSetup deploymentSetup) {
        if (statusByDeployments.get(deploymentId) != null && DeploymentStatus.UNDEPLOYED != statusByDeployments.get(deploymentId).deploymentStatus) {
            log.info("Application with deploymentId <" + deploymentId + "> is already deployed");
            throw new PaaSAlreadyDeployedException("Application is already deployed.");
        }
        try {
            DeploymentInfo deploymentInfo = new DeploymentInfo();
            deploymentInfo.topology = topology;
            deploymentInfo.paaSNodeTemplates = nodeTemplates;
            Path cfyZipPath = recipeGenerator.generateRecipe(deploymentName, deploymentId, nodeTemplates, roots, deploymentSetup);
            statusByDeployments.put(deploymentId, deploymentInfo);
            log.info("Deploying application from recipe at <{}>", cfyZipPath);
            this.deployOnCloudify(deploymentId, cfyZipPath, getSelHealingProperty(deploymentSetup));
            registerDeploymentStatus(deploymentId, DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
        } catch (Exception e) {
            log.error("Deployment failed. Status will move to undeployed.", e);
            updateStatusAndRegisterEvent(deploymentId, DeploymentStatus.UNDEPLOYED);
            if (e instanceof TechnicalException) {
                throw (TechnicalException) e;
            }
            throw new PaaSDeploymentException("Deployment failure", e);
        }
    }

    private boolean getSelHealingProperty(DeploymentSetup deploymentSetup) {
        boolean selfHealing = DEFAULT_SELF_HEALING;
        if (MapUtils.isNotEmpty(deploymentSetup.getProviderDeploymentProperties())) {
            String disableSelfHealing = deploymentSetup.getProviderDeploymentProperties().get(DeploymentPropertiesNames.DISABLE_SELF_HEALING);
            if (StringUtils.isNotBlank(disableSelfHealing)) {
                selfHealing = !Boolean.valueOf(disableSelfHealing.trim());
            }
        }
        return selfHealing;
    }

    private boolean updateStatus(String deploymentId, DeploymentStatus status) {
        DeploymentInfo deploymentInfo = statusByDeployments.get(deploymentId);
        if (deploymentInfo == null) {
            deploymentInfo = new DeploymentInfo();
        }
        if (deploymentInfo.topology == null) {
            deploymentInfo.topology = alienMonitorDao.findById(Topology.class, deploymentId);
        }
        if (deploymentInfo.topology != null) {
            deploymentInfo.deploymentStatus = status;
            statusByDeployments.put(deploymentId, deploymentInfo);
            return true;
        } else {
            return false;
        }
    }

    private void updateStatusAndRegisterEvent(String deploymentId, DeploymentStatus status) {
        updateStatus(deploymentId, status);
        registerDeploymentStatus(deploymentId, status);
    }

    protected void deployOnCloudify(String deploymentId, Path applicationZipPath, boolean selfHealing) throws URISyntaxException, IOException {
        try {
            final URI restEventEndpoint = this.cloudifyRestClientManager.getRestEventEndpoint();
            if (restEventEndpoint != null) {
                CloudifyEventsListener listener = new CloudifyEventsListener(restEventEndpoint, deploymentId, "");
                cleanupUnmanagedApplicationInfos(listener, deploymentId, false);
            }

            RestClient restClient = cloudifyRestClientManager.getRestClient();

            UploadResponse uploadResponse = restClient.upload(applicationZipPath.toFile().getName(), applicationZipPath.toFile());

            InstallApplicationRequest request = new InstallApplicationRequest();
            request.setApplcationFileUploadKey(uploadResponse.getUploadKey());
            request.setApplicationName(deploymentId);
            request.setSelfHealing(selfHealing);

            restClient.installApplication(deploymentId, request);
            if (getPluginConfigurationBean().isSynchronousDeployment()) {
                this.waitForApplicationInstallation(restClient, deploymentId);
            }
        } catch (RestClientException e) {
            throw new PaaSDeploymentException("Unable to deploy application '" + deploymentId + "'.\n\t Cause: " + e.getMessageFormattedText(), e);
        }
    }

    private void waitForApplicationInstallation(RestClient restClient, String applicationName) throws PaaSDeploymentException {
        ApplicationDescription applicationDescription = null;
        DeploymentState currentDeploymentState = null;

        long timeout = System.currentTimeMillis() + TIMEOUT_IN_MILLIS;
        try {
            while (System.currentTimeMillis() < timeout) {
                applicationDescription = restClient.getApplicationDescription(applicationName);
                currentDeploymentState = applicationDescription.getApplicationState();

                switch (currentDeploymentState) {
                case STARTED:
                    log.info(String.format("Deployment of application '%s' is finished with success", applicationName));
                    return;
                case FAILED:
                    throw new PaaSDeploymentException(String.format("Failed deploying application '%s'", applicationName));
                default:
                    try {
                        Thread.sleep(DEFAULT_SLEEP_TIME);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Waiting to retrieve application '" + applicationName + "' state interrupted... ", e);
                    }
                }
            }
        } catch (RestClientException e) {
            throw new PaaSDeploymentException("Failed checking application deployment.\n\t Cause: " + e.getMessageFormattedText(), e);
        }

        throw new PaaSDeploymentException("Application '" + applicationName + "' fails to reach started state in time.");
    }

    @Override
    public synchronized void undeploy(PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        String deploymentId = deploymentContext.getDeploymentId();
        try {
            log.info("Undeploying topology " + deploymentId);
            try {
                recipeGenerator.deleteDirectory(deploymentId);
            } catch (IOException e) {
                log.info("Failed to delete deployment recipe directory <" + deploymentId + ">.");
            }
            // say undeployment triggered
            updateStatusAndRegisterEvent(deploymentId, DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS);

            // trigger undeplyment cloudify side
            RestClient restClient = cloudifyRestClientManager.getRestClient();
            UninstallApplicationResponse uninstallApplication = restClient.uninstallApplication(deploymentId, (int) TIMEOUT_IN_MILLIS);

            // if synchronous mode, wait for the real end of undeployment
            if (getPluginConfigurationBean().isSynchronousDeployment()) {
                log.info("Synchronous deployment. Waiting for deployment <" + deploymentId + "> to be totally undeployed");
                String cdfyDeploymentId = uninstallApplication.getDeploymentID();
                this.waitUndeployApplication(cdfyDeploymentId);
            }
        } catch (RestClientException e) {
            throw new PaaSDeploymentException("Couldn't uninstall topology '" + deploymentId + "'. Cause: " + e.getMessageFormattedText(), e);
        }
    }

    private void registerDeploymentStatus(String deploymentId, DeploymentStatus status) {
        PaaSDeploymentStatusMonitorEvent dsMonitorEvent = new PaaSDeploymentStatusMonitorEvent();
        dsMonitorEvent.setDeploymentId(deploymentId);
        dsMonitorEvent.setDeploymentStatus(status);
        dsMonitorEvent.setDate(new Date().getTime());
        monitorEvents.add(dsMonitorEvent);
    }

    private void waitUndeployApplication(String deploymentID) throws RestClientException {
        long timeout = System.currentTimeMillis() + TIMEOUT_IN_MILLIS;
        while (System.currentTimeMillis() < timeout) {
            DeploymentEvent lastEvent = cloudifyRestClientManager.getRestClient().getLastEvent(deploymentID);
            String description = lastEvent.getDescription();
            if (description != null && description.contains(CloudifyConstants.UNDEPLOYED_SUCCESSFULLY_EVENT)) {
                return;
            }
            try {
                Thread.sleep(DEFAULT_SLEEP_TIME);
            } catch (InterruptedException e) {
                log.warn("Waiting undeployment interrupted (deploymenID=" + deploymentID + ")");
                Thread.currentThread().interrupt();
            }
        }

        log.warn("Application '" + deploymentID + "' fails to undeployed in time. You may do it manually.");
    }

    @Override
    public void scale(PaaSDeploymentContext deploymentContext, String nodeTemplateId, int instances, IPaaSCallback<?> callback) {
        String deploymentId = deploymentContext.getDeploymentId();
        String serviceId = CloudifyPaaSUtils.serviceIdFromNodeTemplateId(nodeTemplateId);
        try {
            CloudifyRestClient restClient = this.cloudifyRestClientManager.getRestClient();
            ServiceDescription serviceDescription = restClient.getServiceDescription(deploymentId, serviceId);
            int plannedInstances = serviceDescription.getPlannedInstances() + instances;
            log.info("Change number of instance of {}.{} from {} to {} ", deploymentId, serviceId, serviceDescription.getPlannedInstances(), plannedInstances);
            SetServiceInstancesRequest request = new SetServiceInstancesRequest();
            request.setCount(plannedInstances);
            restClient.setServiceInstances(deploymentId, serviceId, request);
        } catch (Exception e) {
            log.error("Failed to scale {}", e.getMessage());
            throw new PaaSDeploymentException("Unable to scale " + deploymentId + "." + serviceId, e);
        }
    }

    public DeploymentStatus[] getStatuses(String[] deploymentIds) {
        DeploymentStatus[] statuses = new DeploymentStatus[deploymentIds.length];
        for (int i = 0; i < deploymentIds.length; i++) {
            statuses[i] = getStatus(deploymentIds[i]);
        }
        return statuses;
    }

    public DeploymentStatus getStatus(String deploymentId) {
        DeploymentInfo cachedDeploymentInfo = statusByDeployments.get(deploymentId);
        if (cachedDeploymentInfo == null) {
            return DeploymentStatus.UNDEPLOYED;
        }
        return cachedDeploymentInfo.deploymentStatus;
    }

    private Map<String, Map<String, InstanceInformation>> instanceInformationsFromTopology(Topology topology) {
        Map<String, Map<String, InstanceInformation>> instanceInformations = Maps.newHashMap();
        // fill instance informations based on the topology
        for (Entry<String, NodeTemplate> nodeTempalteEntry : topology.getNodeTemplates().entrySet()) {
            Map<String, InstanceInformation> nodeInstanceInfos = Maps.newHashMap();
            // get the current number of instances
            int currentPlannedInstances = getPlannedInstancesCount(nodeTempalteEntry.getKey(), topology);
            for (int i = 1; i <= currentPlannedInstances; i++) {
                Map<String, AbstractPropertyValue> properties = nodeTempalteEntry.getValue().getProperties() == null ? null : Maps.newHashMap(nodeTempalteEntry
                        .getValue().getProperties());
                Map<String, String> attributes = nodeTempalteEntry.getValue().getAttributes() == null ? null : Maps.newHashMap(nodeTempalteEntry.getValue()
                        .getAttributes());
                // Map<String, String> runtimeProperties = Maps.newHashMap();
                InstanceInformation instanceInfo = new InstanceInformation(ToscaNodeLifecycleConstants.INITIAL, InstanceStatus.PROCESSING,
                        FunctionEvaluator.getScalarValues(properties), attributes, null);
                nodeInstanceInfos.put(String.valueOf(i), instanceInfo);
            }
            instanceInformations.put(nodeTempalteEntry.getKey(), nodeInstanceInfos);
        }
        return instanceInformations;
    }

    /**
     * Fill instance states found from the cloudify extention for TOSCA that dispatch instance states.
     *
     * @param deploymentId The id of the deployment/ applicaiton for which to retrieve instance states.
     * @param instanceInformations The current map of instance informations to fill-in with additional states.
     * @param restEventEndpoint The current rest event endpoint.
     * @throws URISyntaxException
     * @throws IOException In case we fail to get events.
     */
    private void fillInstanceStates(final String deploymentId, final Map<String, Map<String, InstanceInformation>> instanceInformations,
            final URI restEventEndpoint) throws URISyntaxException, IOException {
        CloudifyEventsListener listener = new CloudifyEventsListener(restEventEndpoint, deploymentId, "");
        List<NodeInstanceState> instanceStates = listener.getNodeInstanceStates(deploymentId);

        for (NodeInstanceState instanceState : instanceStates) {
            Map<String, InstanceInformation> nodeTemplateInstanceInformations = instanceInformations.get(instanceState.getNodeTemplateId());
            if (nodeTemplateInstanceInformations == null) { // this should never happends but in case...
                nodeTemplateInstanceInformations = Maps.newHashMap();
                instanceInformations.put(instanceState.getNodeTemplateId(), nodeTemplateInstanceInformations);
            }
            String instanceId = instanceState.getInstanceId();

            InstanceStatus instanceStatus = InstanceStatus.PROCESSING;
            if (ToscaNodeLifecycleConstants.STARTED.equals(instanceState.getInstanceState())
                    || ToscaNodeLifecycleConstants.AVAILABLE.equals(instanceState.getInstanceState())) {
                instanceStatus = InstanceStatus.SUCCESS;
            }

            if (!ToscaNodeLifecycleConstants.STOPPED.equals(instanceState.getInstanceState())) {
                InstanceInformation instanceInformation = nodeTemplateInstanceInformations.get(instanceId);
                if (instanceInformation == null) {
                    Map<String, String> runtimeProperties = Maps.newHashMap();
                    Map<String, String> properties = Maps.newHashMap();
                    Map<String, String> attributes = Maps.newHashMap();
                    InstanceInformation newInstanceInformation = new InstanceInformation(instanceState.getInstanceState(), instanceStatus, properties,
                            attributes, runtimeProperties);
                    nodeTemplateInstanceInformations.put(String.valueOf(instanceId), newInstanceInformation);
                } else {
                    instanceInformation.setState(instanceState.getInstanceState());
                    instanceInformation.setInstanceStatus(instanceStatus);
                }
            }
        }
    }

    /*
     * TODO we should replace this by adding new objects in the space to move attributes values.
     */

    /**
     * Fill in runtime public and private ip addresses on the compute nodes of the topology.
     *
     * @param deploymentId The id of the deployment/ applicaiton for which to retrieve instance states.
     * @param instanceInformations The current map of instance informations to fill-in with additional states.
     * @throws RestClientException In case we fail to get data from cloudify rest api.
     */
    private void fillRuntimeInformations(String deploymentId, Map<String, Map<String, InstanceInformation>> instanceInformations) throws RestClientException {
        CloudifyRestClient restClient = this.cloudifyRestClientManager.getRestClient();
        ApplicationDescription applicationDescription = restClient.getApplicationDescription(deploymentId);

        Map<String, ServiceDescription> serviceDescriptions = Maps.newHashMap();

        // we want to get runtime properties from cloudify (as the public Ip and private Ip)
        for (ServiceDescription serviceDescription : applicationDescription.getServicesDescription()) {
            String serviceName = serviceDescription.getServiceName();
            serviceDescriptions.put(serviceName, serviceDescription);
        }

        for (String nodeTemplateKey : instanceInformations.keySet()) {
            Map<String, InstanceInformation> nodeTemplateInstanceInformations = instanceInformations.get(nodeTemplateKey);
            String serviceId = CloudifyPaaSUtils.serviceIdFromNodeTemplateId(nodeTemplateKey);
            ServiceDescription serviceDescription = serviceDescriptions.get(serviceId);
            if (serviceDescription == null) {
                // not a compute node
                continue;
            }
            // if this is a compute node we can fill-in the attributes.
            for (InstanceDescription instanceDescription : serviceDescription.getInstancesDescription()) {
                InstanceInformation instanceInformation = nodeTemplateInstanceInformations.get(String.valueOf(instanceDescription.getInstanceId()));
                if (instanceInformation == null) {
                    continue;
                }

                if (DeploymentStatus.FAILURE.equals(serviceDescription.getServiceState())) {
                    instanceInformation.setInstanceStatus(InstanceStatus.FAILURE);
                }
                ServiceInstanceDetails serviceInstanceDetails = restClient.getServiceInstanceDetails(deploymentId, serviceId,
                        instanceDescription.getInstanceId());

                if (instanceInformation.getAttributes() == null) {
                    instanceInformation.setAttributes(Maps.<String, String> newHashMap());
                }
                instanceInformation.getAttributes().put("ip_address", serviceInstanceDetails.getPrivateIp());
                instanceInformation.getAttributes().put("public_ip_address", serviceInstanceDetails.getPublicIp());

                Map<String, String> runtimeProperties = new HashMap<String, String>();
                for (Entry<String, Object> entry : serviceInstanceDetails.getProcessDetails().entrySet()) {
                    if (entry.getValue() != null) {
                        runtimeProperties.put(entry.getKey(), entry.getValue().toString());
                    }
                }
                instanceInformation.setRuntimeProperties(runtimeProperties);
            }
        }
    }

    private void parseAttributes(Map<String, Map<String, InstanceInformation>> instanceInformations, DeploymentInfo deploymentInfo) {
        Topology topology = deploymentInfo.topology;
        // parse attributes
        for (Entry<String, Map<String, InstanceInformation>> nodeInstanceId : instanceInformations.entrySet()) {

            for (Entry<String, InstanceInformation> nodeInstanceNumber : nodeInstanceId.getValue().entrySet()) {

                if (nodeInstanceNumber.getValue().getAttributes() != null) {
                    for (Entry<String, String> attributeEntry : nodeInstanceNumber.getValue().getAttributes().entrySet()) {

                        PaaSNodeTemplate nodeTemplate = deploymentInfo.paaSNodeTemplates.get(nodeInstanceId.getKey());
                        Map<String, IAttributeValue> nodeTemplateAttributes = nodeTemplate.getIndexedToscaElement().getAttributes();
                        String parsedAttribute = FunctionEvaluator.parseAttribute(attributeEntry.getKey(), nodeTemplateAttributes.get(attributeEntry.getKey()),
                                topology, instanceInformations, nodeInstanceNumber.getKey(), nodeTemplate);
                        attributeEntry.setValue(parsedAttribute);
                    }
                }

            }

        }
    }

    private int getPlannedInstancesCount(String nodeTemplateId, Topology topology) {
        if (topology.getScalingPolicies() != null) {
            ScalingPolicy scalingPolicy = topology.getScalingPolicies().get(nodeTemplateId);
            if (scalingPolicy != null) {
                return scalingPolicy.getInitialInstances();
            }
        }
        return 1;
    }

    public Map<String, Map<String, InstanceInformation>> getInstancesInformation(String deploymentId, Topology topology) {
        Map<String, Map<String, InstanceInformation>> instanceInformations = instanceInformationsFromTopology(topology);

        final URI restEventEndpoint = this.cloudifyRestClientManager.getRestEventEndpoint();
        if (restEventEndpoint == null) {
            return instanceInformations;
        }

        try {
            fillInstanceStates(deploymentId, instanceInformations, restEventEndpoint);
            fillRuntimeInformations(deploymentId, instanceInformations);
            parseAttributes(instanceInformations, topology);
            return instanceInformations;
        } catch (RestClientException e) {
            log.warn("Error getting " + deploymentId + " deployment informations. \n\t Cause: " + e.getMessageFormattedText());
            return Maps.newHashMap();
        } catch (Exception e) {
            throw new PaaSTechnicalException("Error getting " + deploymentId + " deployment informations", e);
        }
    }

    @Override
    public void getStatus(PaaSDeploymentContext deploymentContext, IPaaSCallback<DeploymentStatus> callback) {
        callback.onSuccess(getStatus(deploymentContext.getDeploymentId()));
    }

    @Override
    public void getInstancesInformation(PaaSDeploymentContext deploymentContext, Topology topology,
            IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
        callback.onSuccess(getInstancesInformation(deploymentContext.getDeploymentId(), topology));
    }

    /**
     * Dispatch a message event to ALIEN.
     *
     * @param deploymentId The id of the deployment (application from cloudify point of view).
     * @param message The message to dispatch.
     */
    public void dispatchMessage(String deploymentId, String message) {
        PaaSMessageMonitorEvent messageMonitorEvent = new PaaSMessageMonitorEvent();
        messageMonitorEvent.setDate(new Date().getTime());
        messageMonitorEvent.setDeploymentId(deploymentId);
        messageMonitorEvent.setMessage(message);
        monitorEvents.offer(messageMonitorEvent);
    }

    private PaaSInstanceStateMonitorEvent generateInstanceStateRemovedEvent(String deploymentId, String nodeId, String instanceId) {
        PaaSInstanceStateMonitorEvent event = new PaaSInstanceStateMonitorEvent();
        event.setInstanceId(instanceId);
        event.setNodeTemplateId(nodeId);
        event.setDate(new Date().getTime());
        event.setDeploymentId(deploymentId);
        return event;
    }

    private List<AbstractMonitorEvent> generateDeleteEvents(String deploymentId, InstanceDeploymentInfo existing, InstanceDeploymentInfo current) {
        List<AbstractMonitorEvent> events = Lists.newArrayList();
        // Generate delete events
        if (existing != null) {
            for (Map.Entry<String, Map<String, InstanceInformation>> existingNodeInfo : existing.instanceInformations.entrySet()) {
                for (Map.Entry<String, InstanceInformation> existingInstanceInfo : existingNodeInfo.getValue().entrySet()) {
                    if (current == null || current.instanceInformations == null || !current.instanceInformations.containsKey(existingNodeInfo.getKey())
                            || !current.instanceInformations.get(existingNodeInfo.getKey()).containsKey(existingInstanceInfo.getKey())) {
                        events.add(generateInstanceStateRemovedEvent(deploymentId, existingNodeInfo.getKey(), existingInstanceInfo.getKey()));
                    }
                }
            }
        }
        return events;
    }

    private void generateInstanceStateEvent(PaaSInstanceStateMonitorEvent isMonitorEvent, InstanceDeploymentInfo current, String nodeId, String instanceId) {
        // Generate instance state change events
        if (current == null || current.instanceInformations == null) {
            return;
        }
        Map<String, InstanceInformation> nodeInfo = current.instanceInformations.get(nodeId);
        if (nodeInfo == null) {
            return;
        }
        InstanceInformation instanceInfo = nodeInfo.get(instanceId);
        if (instanceInfo == null) {
            return;
        }

        isMonitorEvent.setInstanceStatus(instanceInfo.getInstanceStatus());
        isMonitorEvent.setProperties(instanceInfo.getProperties());
        isMonitorEvent.setRuntimeProperties(instanceInfo.getRuntimeProperties());
        isMonitorEvent.setAttributes(instanceInfo.getAttributes());
    }

    @Override
    public void getEventsSince(Date date, int maxEvents, IPaaSCallback<AbstractMonitorEvent[]> eventsCallback) {
        final URI restEventEndpoint = this.cloudifyRestClientManager.getRestEventEndpoint();
        if (restEventEndpoint == null) {
            eventsCallback.onSuccess(new AbstractMonitorEvent[0]);
        }

        List<AbstractMonitorEvent> events = Lists.newArrayList();
        try {
            CloudifyEventsListener listener = new CloudifyEventsListener(restEventEndpoint, "", "");
            // get message events
            AbstractMonitorEvent queuedMonitorEvent;
            int count = 0;
            while (count < maxEvents && (queuedMonitorEvent = monitorEvents.poll()) != null) {
                events.add(queuedMonitorEvent);
                count++;
            }

            // get deployment status events
            CloudifyRestClient restClient = this.cloudifyRestClientManager.getRestClient();
            List<ApplicationDescription> applicationDescriptions = restClient.getApplicationDescriptionsList();
            log.debug("applicationDescriptions: " + applicationDescriptions);
            if (applicationDescriptions == null) {
                log.debug("GetEvents: Nothing in applicationDescriptions. Exiting...");
                eventsCallback.onSuccess(events.toArray(new AbstractMonitorEvent[events.size()]));
            }

            List<String> appUnknownStatuses = Lists.newArrayList(statusByDeployments.keySet());
            processUnknownStatuses(events, applicationDescriptions, appUnknownStatuses);

            // cleanup statuses
            if (appUnknownStatuses.size() > 0) {
                cleanupUnknownApplicationsStatuses(listener, appUnknownStatuses);
            }

            // get instance status events
            List<AlienEvent> instanceEvents = listener.getEventsSince(date, maxEvents);

            Set<String> processedDeployments = Sets.newHashSet();
            processEvents(events, instanceEvents, processedDeployments);
        } catch (Exception e) {
            eventsCallback.onFailure(new PaaSTechnicalException("Error while getting deployment events.", e));
        }
        eventsCallback.onSuccess(events.toArray(new AbstractMonitorEvent[events.size()]));
    }

    private void processUnknownStatuses(List<AbstractMonitorEvent> events, List<ApplicationDescription> applicationDescriptions, List<String> appUnknownStatuses) {
        for (ApplicationDescription applicationDescription : applicationDescriptions) {
            log.debug("GetEvents: PROCESSING UNKWON STATUSES...");
            DeploymentStatus status = getStatusFromApplicationDescription(applicationDescription);

            DeploymentInfo info = this.statusByDeployments.get(applicationDescription.getApplicationName());
            DeploymentStatus oldStatus = info == null ? null : info.deploymentStatus;
            if (!isUndeploymentTriggered(oldStatus) && !status.equals(oldStatus)) {
                boolean found = updateStatus(applicationDescription.getApplicationName(), status);
                if (found) {
                    PaaSDeploymentStatusMonitorEvent dsMonitorEvent = new PaaSDeploymentStatusMonitorEvent();
                    dsMonitorEvent.setDeploymentId(applicationDescription.getApplicationName());
                    dsMonitorEvent.setDeploymentStatus(status);
                    dsMonitorEvent.setDate(new Date().getTime());

                    // update the local status.
                    log.debug("{} has changed status {}", applicationDescription.getApplicationName(), status);
                    events.add(dsMonitorEvent);
                }
            }
            appUnknownStatuses.remove(applicationDescription.getApplicationName());
        }
    }

    private void processEvents(List<AbstractMonitorEvent> events, List<AlienEvent> instanceEvents, Set<String> processedDeployments) {
        for (AlienEvent alienEvent : instanceEvents) {
            if (!statusByDeployments.containsKey(alienEvent.getApplicationName())) {
                continue;
            }
            InstanceDeploymentInfo currentInstanceDeploymentInfo;
            if (processedDeployments.add(alienEvent.getApplicationName())) {
                currentInstanceDeploymentInfo = new InstanceDeploymentInfo();
                DeploymentInfo deploymentInfo = statusByDeployments.get(alienEvent.getApplicationName());
                // application is undeployed but we can still get events as polling them is Async
                currentInstanceDeploymentInfo.instanceInformations = getInstancesInformation(alienEvent.getApplicationName(), deploymentInfo.topology);
                generateDeleteEvents(alienEvent.getApplicationName(), instanceStatusByDeployments.get(alienEvent.getApplicationName()),
                        currentInstanceDeploymentInfo);
                instanceStatusByDeployments.put(alienEvent.getApplicationName(), currentInstanceDeploymentInfo);
            } else {
                currentInstanceDeploymentInfo = instanceStatusByDeployments.get(alienEvent.getApplicationName());
            }
            PaaSInstanceStateMonitorEvent monitorEvent;
            if (alienEvent instanceof BlockStorageEvent) {
                monitorEvent = new PaaSInstanceStorageMonitorEvent(((BlockStorageEvent) alienEvent).getVolumeId());
            } else {
                monitorEvent = new PaaSInstanceStateMonitorEvent();
            }

            monitorEvent.setDeploymentId(alienEvent.getApplicationName());
            monitorEvent.setNodeTemplateId(alienEvent.getServiceName());
            monitorEvent.setInstanceId(alienEvent.getInstanceId());
            monitorEvent.setDate(alienEvent.getDateTimestamp().getTime());
            monitorEvent.setInstanceState(alienEvent.getEvent());

            generateInstanceStateEvent(monitorEvent, currentInstanceDeploymentInfo, alienEvent.getServiceName(), alienEvent.getInstanceId());

            events.add(monitorEvent);
        }
    }

    private boolean isUndeploymentTriggered(DeploymentStatus status) {
        return DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS.equals(status) || DeploymentStatus.UNDEPLOYED.equals(status);
    }

    private synchronized void cleanupUnknownApplicationsStatuses(CloudifyEventsListener listener, List<String> appUnknownStatuses) throws URISyntaxException,
            IOException {
        for (String appUnknownStatus : appUnknownStatuses) {
            if (DeploymentStatus.DEPLOYMENT_IN_PROGRESS.equals(statusByDeployments.get(appUnknownStatus).deploymentStatus)) {
                DeploymentInfo deploymentInfo = statusByDeployments.get(appUnknownStatus);
                if (deploymentInfo.deploymentDate + MAX_DEPLOYMENT_TIMEOUT_MILLIS < new Date().getTime()) {
                    log.info("Deployment has timed out... setting as undeployed...");
                    registerDeploymentStatus(appUnknownStatus, DeploymentStatus.UNDEPLOYED);
                    cleanupUnmanagedApplicationInfos(listener, appUnknownStatus, true);
                }
            } else {
                registerDeploymentStatus(appUnknownStatus, DeploymentStatus.UNDEPLOYED);
                cleanupUnmanagedApplicationInfos(listener, appUnknownStatus, true);
            }

        }
    }

    private void cleanupUnmanagedApplicationInfos(CloudifyEventsListener listener, String deploymentId, boolean deleteRecipe) throws URISyntaxException,
            IOException {
        if (deleteRecipe) {
            log.info("Cleanup unmanaged application <" + deploymentId + ">.");
            statusByDeployments.remove(deploymentId);
            instanceStatusByDeployments.remove(deploymentId);
            try {
                recipeGenerator.deleteDirectory(deploymentId);
            } catch (IOException e) {
                log.info("Failed to delete deployment recipe directory <" + deploymentId + ">.");
            }
        }
        // call the rest service to remove permanent status
        listener.deleteNodeInstanceStates(deploymentId);
    }

    private DeploymentStatus getStatusFromApplicationDescription(ApplicationDescription applicationDescription) {
        DeploymentStatus status = statusFromState(applicationDescription.getApplicationState());
        if (status == null) {
            status = statusFromInstances(applicationDescription.getServicesDescription());
        }
        return status;
    }

    private DeploymentStatus statusFromState(DeploymentState deploymentState) {
        switch (deploymentState) {
        case FAILED:
            return DeploymentStatus.FAILURE;
        case IN_PROGRESS:
            return DeploymentStatus.DEPLOYMENT_IN_PROGRESS;
        case STARTED:
            return null;
        }
        return null;
    }

    private DeploymentStatus statusFromInstances(List<ServiceDescription> serviceDescriptions) {
        DeploymentStatus deploymentStatus = null;
        for (ServiceDescription serviceDescription : serviceDescriptions) {
            for (InstanceDescription instanceDescription : serviceDescription.getInstancesDescription()) {
                DeploymentStatus status = fromCloudifyInstanceStatus(instanceDescription.getInstanceStatus());
                if (DeploymentStatus.FAILURE.equals(status)) {
                    return status;
                } else if (DeploymentStatus.WARNING.equals(status)) {
                    deploymentStatus = status;
                }
            }
        }
        return deploymentStatus == null ? DeploymentStatus.DEPLOYED : deploymentStatus;
    }

    private DeploymentStatus fromCloudifyInstanceStatus(String instanceStatus) {
        try {
            USMState state = USMState.valueOf(instanceStatus);
            switch (state) {
            case INITIALIZING:
                return DeploymentStatus.DEPLOYMENT_IN_PROGRESS;
            case LAUNCHING:
                return DeploymentStatus.DEPLOYMENT_IN_PROGRESS;
            case RUNNING:
                return DeploymentStatus.DEPLOYED;
            case SHUTTING_DOWN:
                return DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS;
            case ERROR:
                return DeploymentStatus.FAILURE;
            }
            return DeploymentStatus.WARNING;
        } catch (IllegalArgumentException e) {
            // NA is a warning state.
            return DeploymentStatus.WARNING;
        }
    }

    protected static class DeploymentInfo {
        private Topology topology;
        private DeploymentStatus deploymentStatus;
        private Map<String, PaaSNodeTemplate> paaSNodeTemplates;
        private long deploymentDate;

        public DeploymentInfo() {
            deploymentStatus = DeploymentStatus.DEPLOYMENT_IN_PROGRESS;
            deploymentDate = System.currentTimeMillis();
        }
    }

    private static class InstanceDeploymentInfo {
        private Map<String, Map<String, InstanceInformation>> instanceInformations;
    }

    @Override
    public void executeOperation(PaaSDeploymentContext deploymentContext, NodeOperationExecRequest request, IPaaSCallback<Map<String, String>> callback)
            throws OperationExecutionException {
        String deploymentId = deploymentContext.getDeploymentId();
        Map<String, String> operationResponse = Maps.newHashMap();
        String serviceName = retrieveServiceName(deploymentId, request.getNodeTemplateName());
        InvokeCustomCommandRequest invokeRequest = new InvokeCustomCommandRequest();
        invokeRequest.setCommandName(CloudifyPaaSUtils.prefixWith(request.getOperationName(), request.getNodeTemplateName()));
        buildParameters(deploymentId, request, invokeRequest);
        String operationFQN = operationFQN(serviceName, request, invokeRequest);
        try {
            RestClient restClient = cloudifyRestClientManager.getRestClient();
            log.info("Trigerring operation <" + operationFQN + ">.");
            // case execute on an instance
            if (StringUtils.isNotBlank(request.getInstanceId())) {
                String instanceId = request.getInstanceId();
                InvokeInstanceCommandResponse response = restClient.invokeInstanceCommand(deploymentId, serviceName, Integer.parseInt(instanceId),
                        invokeRequest);
                log.debug("RAW result is: \n" + response.getInvocationResult());
                parseInstanceInvokeResponse(operationResponse, response.getInvocationResult());
            } else { // case execute on all instances (on the service level)
                InvokeServiceCommandResponse response = restClient.invokeServiceCommand(deploymentId, serviceName, invokeRequest);
                log.debug("RAW result is: \n" + response.getInvocationResultPerInstance());
                parseServiceInvokeResponse(operationResponse, response.getInvocationResultPerInstance());
            }

        } catch (RestClientException e) {
            callback.onFailure(new PaaSTechnicalException("Unable to execute the operation <" + operationFQN + ">.\n\t Cause: " + e.getMessageFormattedText(),
                    e));
        }

        log.debug("Result is: \n" + operationResponse);
        callback.onSuccess(operationResponse);
    }

    private void buildParameters(String deploymentId, NodeOperationExecRequest request, InvokeCustomCommandRequest invokeRequest) {
        invokeRequest.setParameters(Lists.<String> newArrayList());
        if (MapUtils.isNotEmpty(request.getParameters())) {
            for (Entry<String, String> entry : request.getParameters().entrySet()) {
                invokeRequest.getParameters().add(entry.toString());
            }
        }

        // if some params are missing, add them with null value
        IndexedNodeType nodeType = statusByDeployments.get(deploymentId).paaSNodeTemplates.get(request.getNodeTemplateName()).getIndexedToscaElement();
        Map<String, IOperationParameter> params = nodeType.getInterfaces().get(request.getInterfaceName()).getOperations().get(request.getOperationName())
                .getInputParameters();
        Map<String, String> requestParams = request.getParameters() == null ? Maps.<String, String> newHashMap() : request.getParameters();
        for (Entry<String, IOperationParameter> param : params.entrySet()) {
            if (param.getValue().isDefinition() && !requestParams.containsKey(param.getKey())) {
                invokeRequest.getParameters().add(param.getKey().concat("=").concat("null"));
            }
        }
    }

    private void parseServiceInvokeResponse(Map<String, String> operationResponse, Map<String, Map<String, String>> invocationResultPerInstance)
            throws OperationExecutionException {
        if (MapUtils.isEmpty(invocationResultPerInstance)) {
            return;
        }

        for (Map<String, String> invocationResult : invocationResultPerInstance.values()) {
            parseInstanceInvokeResponse(operationResponse, invocationResult);
        }
    }

    private void parseInstanceInvokeResponse(Map<String, String> operationResponse, Map<String, String> invocationResult) throws OperationExecutionException {
        if (MapUtils.isEmpty(invocationResult)) {
            return;
        }
        if (INVOCATION_SUCCESS.equals(invocationResult.get(INVOCATION_SUCCESS_KEY))) {
            operationResponse.put(invocationResult.get(INVOCATION_INSTANCE_ID_KEY), invocationResult.get(INVOCATION_RESULT_KEY));
            return;
        }

        // if not success, throw an exception
        throw new OperationExecutionException("InstanceId: " + invocationResult.get(INVOCATION_INSTANCE_ID_KEY) + "\n\t"
                + invocationResult.get(INVOCATION_EXCEPTION_KEY));
    }

    private String retrieveServiceName(String deploymentId, String nodeTemplateName) {
        DeploymentInfo deploymentInfo = statusByDeployments.get(deploymentId);
        if (deploymentInfo == null) {
            throw new PaaSNotYetDeployedException("Application <" + deploymentId + "> is not deloyed!");
        }
        if (deploymentInfo.paaSNodeTemplates == null) {
            deploymentInfo.paaSNodeTemplates = topologyTreeBuilderService.buildPaaSTopology(deploymentInfo.topology).getAllNodes();
        }
        PaaSNodeTemplate nodeTemplate = deploymentInfo.paaSNodeTemplates.get(nodeTemplateName);
        return CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(nodeTemplate);
    }

    private String operationFQN(String serviceName, NodeOperationExecRequest request, InvokeCustomCommandRequest invokeRequest) {
        StringBuilder fqnBuilder = new StringBuilder(serviceName);
        fqnBuilder.append(".").append(request.getNodeTemplateName());
        if (StringUtils.isNoneBlank(request.getInstanceId())) {
            fqnBuilder.append("[" + request.getInstanceId() + "]");
        }
        fqnBuilder.append(".").append(request.getInterfaceName()).append(".").append(request.getOperationName());
        fqnBuilder.append("(");
        if (CollectionUtils.isNotEmpty(invokeRequest.getParameters())) {
            fqnBuilder.append(invokeRequest.getParameters().toString());
        }
        fqnBuilder.append(")");
        return fqnBuilder.toString();
    }
}
