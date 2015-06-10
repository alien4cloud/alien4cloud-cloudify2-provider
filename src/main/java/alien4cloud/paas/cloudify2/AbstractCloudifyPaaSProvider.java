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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.cloudifysource.dsl.internal.CloudifyConstants.DeploymentState;
import org.cloudifysource.dsl.internal.CloudifyConstants.USMState;
import org.cloudifysource.dsl.rest.request.InstallApplicationRequest;
import org.cloudifysource.dsl.rest.request.InvokeCustomCommandRequest;
import org.cloudifysource.dsl.rest.request.SetServiceInstancesRequest;
import org.cloudifysource.dsl.rest.response.ApplicationDescription;
import org.cloudifysource.dsl.rest.response.InstanceDescription;
import org.cloudifysource.dsl.rest.response.InvokeInstanceCommandResponse;
import org.cloudifysource.dsl.rest.response.InvokeServiceCommandResponse;
import org.cloudifysource.dsl.rest.response.ServiceDescription;
import org.cloudifysource.dsl.rest.response.ServiceInstanceDetails;
import org.cloudifysource.dsl.rest.response.UploadResponse;
import org.cloudifysource.restclient.RestClient;
import org.cloudifysource.restclient.exceptions.RestClientException;
import org.cloudifysource.restclient.exceptions.RestClientIOException;

import alien4cloud.cloud.DeploymentService;
import alien4cloud.common.AlienConstants;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.exception.TechnicalException;
import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.topology.Capability;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.ITemplateManagedPaaSProvider;
import alien4cloud.paas.cloudify2.events.AlienEvent;
import alien4cloud.paas.cloudify2.events.BlockStorageEvent;
import alien4cloud.paas.cloudify2.events.NodeInstanceState;
import alien4cloud.paas.cloudify2.generator.RecipeGenerator;
import alien4cloud.paas.cloudify2.rest.CloudifyEventsListener;
import alien4cloud.paas.cloudify2.rest.CloudifyRestClient;
import alien4cloud.paas.cloudify2.rest.CloudifyRestClientManager;
import alien4cloud.paas.cloudify2.utils.CloudifyPaaSUtils;
import alien4cloud.paas.exception.MaintenanceModeException;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.PaaSAlreadyDeployedException;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.exception.PaaSDeploymentIOException;
import alien4cloud.paas.exception.PaaSNotYetDeployedException;
import alien4cloud.paas.exception.PaaSTechnicalException;
import alien4cloud.paas.exception.PluginConfigurationException;
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
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.topology.TopologyUtils;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.NormativeComputeConstants;
import alien4cloud.utils.AlienUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@Slf4j
public abstract class AbstractCloudifyPaaSProvider implements IConfigurablePaaSProvider<PluginConfigurationBean>, ITemplateManagedPaaSProvider {

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
    @Resource
    private DeploymentService deploymentService;

    private static final long TIMEOUT_IN_MILLIS = 1000L * 60L * 10L; // 10 minutes
    private static final long MAX_DEPLOYMENT_TIMEOUT_MILLIS = 1000L * 60L * 5L; // 5 minutes
    private static final boolean DEFAULT_SELF_HEALING = true;

    private static final String INVOCATION_INSTANCE_ID_KEY = "Invocation_Instance_ID";
    private static final String INVOCATION_RESULT_KEY = "Invocation_Result";
    private static final String INVOCATION_SUCCESS_KEY = "Invocation_Success";
    private static final String INVOCATION_SUCCESS = "SUCCESS";
    private static final String INVOCATION_EXCEPTION_KEY = "Invocation_Exception";

    private static final String START_MAINTENANCE_COMMAND_NAME = "cloudify:start-maintenance-mode";
    private static final String STOP_MAINTENANCE_COMMAND_NAME = "cloudify:stop-maintenance-mode";
    private static final Long DEFAULT_MAINENANCE_TIME_MIN = 1000L * 60L * 60L * 24L * 365L; // about one year

    // private static final String INVOCATION_INSTANCE_NAME_KEY = "Invocation_Instance_Name";
    // private static final String INVOCATION_COMMAND_NAME_KEY = "Invocation_Command_Name";

    protected Map<String, DeploymentInfo> statusByDeployments = Maps.newHashMap();

    protected Map<String, NodesDeploymentInfo> instanceStatusByDeployments = Maps.newHashMap();

    private Queue<AbstractMonitorEvent> monitorEvents = new ConcurrentLinkedQueue<>();

    @PostConstruct
    private void initBean() {
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
    public void init(Map<String, PaaSTopologyDeploymentContext> activeDeployments) {
        if (activeDeployments == null) {
            return;
        }
        for (Map.Entry<String, PaaSTopologyDeploymentContext> activeDeployment : activeDeployments.entrySet()) {
            DeploymentInfo deploymentInfo = new DeploymentInfo();
            deploymentInfo.deploymentId = activeDeployment.getValue().getDeploymentId();
            deploymentInfo.topology = activeDeployment.getValue().getTopology();
            statusByDeployments.put(activeDeployment.getKey(), deploymentInfo);
        }
    }

    @Override
    public void deploy(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        doDeploy(deploymentContext.getDeploymentId(), deploymentContext.getDeploymentPaaSId(), deploymentContext.getTopology(),
                deploymentContext.getPaaSTopology(), deploymentContext.getDeploymentSetup());
    }

    protected synchronized void doDeploy(String deploymentId, String deploymentPaaSId, Topology topology, PaaSTopology paaSTopology,
            DeploymentSetup deploymentSetup) {
        if (statusByDeployments.get(deploymentPaaSId) != null && DeploymentStatus.UNDEPLOYED != statusByDeployments.get(deploymentPaaSId).deploymentStatus) {
            log.info("Application with deploymentId <" + deploymentPaaSId + "> is already deployed");
            throw new PaaSAlreadyDeployedException("Application is already deployed.");
        }
        try {
            DeploymentInfo deploymentInfo = new DeploymentInfo();
            deploymentInfo.deploymentId = deploymentId;
            deploymentInfo.topology = topology;
            deploymentInfo.paaSTopology = paaSTopology;
            Path cfyZipPath = recipeGenerator.generateRecipe(deploymentId, deploymentPaaSId, paaSTopology, deploymentSetup);
            statusByDeployments.put(deploymentPaaSId, deploymentInfo);
            log.info("Deploying application from recipe at <{}>", cfyZipPath);
            this.deployOnCloudify(deploymentPaaSId, cfyZipPath, getSelHealingProperty(deploymentSetup));
            registerDeploymentStatus(deploymentId, DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
        } catch (PaaSDeploymentIOException e) {
            log.warn("IO exception while trying to reach cloudify. Status will move to unknown.", e);
            updateStatusAndRegisterEvent(deploymentId, deploymentPaaSId, DeploymentStatus.UNKNOWN);
            throw e;
        } catch (Exception e) {
            log.error("Deployment failed. Status will move to undeployed.", e);
            updateStatusAndRegisterEvent(deploymentId, deploymentPaaSId, DeploymentStatus.UNDEPLOYED);
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

    private boolean updateStatus(String deploymentId, String deploymentPaaSId, DeploymentStatus status) {
        DeploymentInfo deploymentInfo = statusByDeployments.get(deploymentId);
        if (deploymentInfo == null) {
            deploymentInfo = new DeploymentInfo();
        }
        checkAndFillTopology(deploymentId, deploymentInfo);
        if (deploymentInfo.topology != null) {
            deploymentInfo.deploymentId = deploymentId;
            deploymentInfo.deploymentStatus = status;
            statusByDeployments.put(deploymentPaaSId, deploymentInfo);
            return true;
        }

        return false;
    }

    private void checkAndFillTopology(String deploymentId, DeploymentInfo deploymentInfo) {
        if (deploymentInfo.topology == null && deploymentId != null) {
            deploymentInfo.topology = alienMonitorDao.findById(Topology.class, deploymentId);
            if (deploymentInfo.topology != null) {
                deploymentInfo.paaSTopology = topologyTreeBuilderService.buildPaaSTopology(deploymentInfo.topology);
            }
        }
    }

    private void updateStatusAndRegisterEvent(String deploymentId, String deploymentPaaSId, DeploymentStatus status) {
        updateStatus(deploymentId, deploymentPaaSId, status);
        registerDeploymentStatus(deploymentId, status);
    }

    protected void deployOnCloudify(String deploymentPaaSId, Path applicationZipPath, boolean selfHealing) throws URISyntaxException, IOException {
        try {
            final URI restEventEndpoint = this.cloudifyRestClientManager.getRestEventEndpoint();
            if (restEventEndpoint != null) {
                CloudifyEventsListener listener = new CloudifyEventsListener(restEventEndpoint, statusByDeployments.get(deploymentPaaSId).deploymentId, "");
                cleanupUnmanagedApplicationInfos(listener, deploymentPaaSId, false);
            }

            RestClient restClient = cloudifyRestClientManager.getRestClient();

            UploadResponse uploadResponse = restClient.upload(applicationZipPath.toFile().getName(), applicationZipPath.toFile());

            InstallApplicationRequest request = new InstallApplicationRequest();
            request.setApplcationFileUploadKey(uploadResponse.getUploadKey());
            request.setApplicationName(deploymentPaaSId);
            request.setSelfHealing(selfHealing);
            restClient.installApplication(deploymentPaaSId, request);
        } catch (RestClientIOException e) {
            throw new PaaSDeploymentIOException("IO exception while trying to reach cloudify. \n\tCause:" + e.getMessageFormattedText(), e);
        } catch (RestClientException | PluginConfigurationException e) {
            throwPaaSDeploymentException("Unable to deploy application '" + deploymentPaaSId + "'.", e);
        }
    }

    @Override
    public synchronized void undeploy(PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        String deploymentPaaSId = deploymentContext.getDeploymentPaaSId();
        try {
            log.info("Undeploying topology " + deploymentPaaSId);
            try {
                recipeGenerator.deleteDirectory(deploymentPaaSId);
            } catch (IOException e) {
                log.info("Failed to delete deployment recipe directory <" + deploymentPaaSId + ">.");
            }
            // say undeployment triggered
            updateStatusAndRegisterEvent(deploymentContext.getDeploymentId(), deploymentPaaSId, DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS);

            // trigger undeplyment cloudify side
            RestClient restClient = cloudifyRestClientManager.getRestClient();
            restClient.uninstallApplication(deploymentPaaSId, (int) TIMEOUT_IN_MILLIS);

        } catch (RestClientException | PluginConfigurationException e) {
            throwPaaSDeploymentException("Couldn't uninstall topology '" + deploymentPaaSId + "'.", e);
        }
    }

    private void throwPaaSDeploymentException(String message, Exception e) {
        String cause = e instanceof RestClientException ? ((RestClientException) e).getMessageFormattedText() : e.getMessage();
        throw new PaaSDeploymentException(message + "\n\tCause: " + cause, e);
    }

    private void registerDeploymentStatus(String deploymentId, DeploymentStatus status) {
        PaaSDeploymentStatusMonitorEvent dsMonitorEvent = new PaaSDeploymentStatusMonitorEvent();
        dsMonitorEvent.setDeploymentId(deploymentId);
        dsMonitorEvent.setDeploymentStatus(status);
        dsMonitorEvent.setDate(new Date().getTime());
        monitorEvents.add(dsMonitorEvent);
    }

    @Override
    public void scale(PaaSDeploymentContext deploymentContext, String nodeTemplateId, int instances, IPaaSCallback<?> callback) {
        String deploymentPaaSId = deploymentContext.getDeploymentPaaSId();
        String serviceId = CloudifyPaaSUtils.serviceIdFromNodeTemplateId(nodeTemplateId);
        try {
            CloudifyRestClient restClient = this.cloudifyRestClientManager.getRestClient();
            ServiceDescription serviceDescription = restClient.getServiceDescription(deploymentPaaSId, serviceId);
            int plannedInstances = serviceDescription.getPlannedInstances() + instances;
            Topology topology = statusByDeployments.get(deploymentPaaSId).topology;
            Capability capability = TopologyUtils.getScalableCapability(topology, nodeTemplateId, true);
            TopologyUtils.setScalingProperty(NormativeComputeConstants.SCALABLE_DEFAULT_INSTANCES, plannedInstances, capability);
            log.info("Change number of instance of {}.{} from {} to {} ", deploymentPaaSId, serviceId, serviceDescription.getPlannedInstances(),
                    plannedInstances);
            SetServiceInstancesRequest request = new SetServiceInstancesRequest();
            request.setCount(plannedInstances);
            restClient.setServiceInstances(deploymentPaaSId, serviceId, request);
        } catch (Exception e) {
            log.error("Failed to scale {}", e.getMessage());
            throw new PaaSDeploymentException("Unable to scale " + deploymentPaaSId + "." + serviceId, e);
        }
    }

    public DeploymentStatus[] getStatuses(String[] deploymentPaaSIds) {
        DeploymentStatus[] statuses = new DeploymentStatus[deploymentPaaSIds.length];
        for (int i = 0; i < deploymentPaaSIds.length; i++) {
            statuses[i] = getStatus(deploymentPaaSIds[i]);
        }
        return statuses;
    }

    public DeploymentStatus getStatus(String deploymentPaaSId) {
        DeploymentInfo cachedDeploymentInfo = statusByDeployments.get(deploymentPaaSId);
        if (cachedDeploymentInfo == null) {
            return DeploymentStatus.UNDEPLOYED;
        }
        return cachedDeploymentInfo.deploymentStatus;
    }

    private Map<String, Map<String, InstanceInformation>> buildInstancesInformations(DeploymentInfo deploymentInfo) {
        Map<String, Map<String, InstanceInformation>> instanceInformations = Maps.newHashMap();
        // fill instance informations based on the topology
        for (Entry<String, NodeTemplate> nodeTempalteEntry : deploymentInfo.topology.getNodeTemplates().entrySet()) {
            Map<String, InstanceInformation> nodeInstanceInfos = Maps.newHashMap();
            // get the current number of instances
            int currentPlannedInstances = getPlannedInstancesCount(deploymentInfo.paaSTopology.getAllNodes().get(nodeTempalteEntry.getKey()),
                    deploymentInfo.topology);
            for (int i = 1; i <= currentPlannedInstances; i++) {
                Map<String, String> attributes = nodeTempalteEntry.getValue().getAttributes() == null ? null : Maps.newHashMap(nodeTempalteEntry.getValue()
                        .getAttributes());
                InstanceInformation instanceInfo = new InstanceInformation(ToscaNodeLifecycleConstants.INITIAL, InstanceStatus.PROCESSING, attributes, null,
                        Maps.<String, String> newHashMap());
                nodeInstanceInfos.put(String.valueOf(i), instanceInfo);
            }
            instanceInformations.put(nodeTempalteEntry.getKey(), nodeInstanceInfos);
        }
        return instanceInformations;
    }

    /**
     * Fill instance states found from the cloudify extention for TOSCA that dispatch instance states.
     *
     * @param deploymentId The id of the deployment for which to retrieve instance states.
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
            } else if (InstanceStatus.MAINTENANCE.toString().toLowerCase().equals(instanceState.getInstanceState())) {
                instanceStatus = InstanceStatus.MAINTENANCE;
            }

            if (!(ToscaNodeLifecycleConstants.DELETED.equals(instanceState.getInstanceState()))) {
                InstanceInformation instanceInformation = nodeTemplateInstanceInformations.get(instanceId);
                if (instanceInformation == null) {
                    Map<String, String> runtimeProperties = Maps.newHashMap();
                    Map<String, String> operationsOutputs = Maps.newHashMap();
                    Map<String, String> attributes = Maps.newHashMap();
                    InstanceInformation newInstanceInformation = new InstanceInformation(instanceState.getInstanceState(), instanceStatus, attributes,
                            runtimeProperties, operationsOutputs);
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
     * Fill in runtime informations on the compute nodes of the topology.
     *
     * @param instanceInformations The current map of instance informations to fill-in with additional states.
     * @param applicationDescription TODO
     * @param restClient TODO
     *
     * @throws RestClientException In case we fail to get data from cloudify rest api.
     * @throws PluginConfigurationException
     */
    private void fillRuntimeInformations(Map<String, Map<String, InstanceInformation>> instanceInformations, ApplicationDescription applicationDescription,
            CloudifyRestClient restClient) throws PluginConfigurationException, RestClientException {

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
                ServiceInstanceDetails serviceInstanceDetails = restClient.getServiceInstanceDetails(serviceDescription.getApplicationName(), serviceId,
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

    private void fillOperationsOutputs(Map<String, Map<String, InstanceInformation>> instanceInformations, ApplicationDescription applicationDescription,
            CloudifyRestClient restClient) throws PluginConfigurationException, RestClientException, URISyntaxException, IOException {

        // serviceId --> Map < instanceId, Map< formatedOutputName, outputValue > >
        Map<String, Map<String, Map<String, String>>> outputMaps = Maps.newHashMap();

        // we want to get operation outputs for all cloudify services
        for (ServiceDescription serviceDescription : applicationDescription.getServicesDescription()) {
            outputMaps.put(serviceDescription.getServiceName(),
                    restClient.getAllInstancesOperationsOutputs(serviceDescription.getApplicationName(), serviceDescription.getServiceName()));
        }

        // register every outputs where it belongs
        for (Map<String, Map<String, String>> instanceOutputs : outputMaps.values()) {
            for (Entry<String, Map<String, String>> entry : instanceOutputs.entrySet()) {
                String instanceId = entry.getKey();
                Map<String, String> nodesOutputs = entry.getValue();
                for (String formatedOutputName : nodesOutputs.keySet()) {
                    // 0 = nodeId, 1 = interfaceName, 2 = operationName, 3 = outputName
                    String[] parts = formatedOutputName.split(AlienConstants.OPERATION_NAME_SEPARATOR);
                    Map<String, InstanceInformation> nodeTemplateInstanceInformations = instanceInformations.get(parts[0]);
                    InstanceInformation instanceInformation = nodeTemplateInstanceInformations == null ? null : nodeTemplateInstanceInformations
                            .get(instanceId);
                    if (instanceInformation == null) {
                        continue;
                    }
                    instanceInformation.getOperationsOutputs().put(formatedOutputName, nodesOutputs.get(formatedOutputName));
                }
            }
        }
    }

    private int getPlannedInstancesCount(PaaSNodeTemplate paaSNodeTemplate, Topology topology) {
        String computeNodeId = ToscaUtils.getMandatoryHostTemplate(paaSNodeTemplate).getId();
        Capability scalableCapability = TopologyUtils.getScalableCapability(topology, computeNodeId, true);
        return TopologyUtils.getScalingProperty(NormativeComputeConstants.SCALABLE_DEFAULT_INSTANCES, scalableCapability);
    }

    public Map<String, Map<String, InstanceInformation>> getInstancesInformation(String deploymentPaaSId) {

        DeploymentInfo deploymentInfo = statusByDeployments.get(deploymentPaaSId);
        if (deploymentInfo == null) {
            return null;
        }
        Map<String, Map<String, InstanceInformation>> instanceInformations = buildInstancesInformations(deploymentInfo);
        final URI restEventEndpoint = this.cloudifyRestClientManager.getRestEventEndpoint();
        if (restEventEndpoint == null) {
            return instanceInformations;
        }

        try {
            CloudifyRestClient restClient = cloudifyRestClientManager.getRestClient();
            ApplicationDescription applicationDescription = restClient.getApplicationDescription(deploymentPaaSId);
            fillInstanceStates(deploymentInfo.deploymentId, instanceInformations, restEventEndpoint);
            fillRuntimeInformations(instanceInformations, applicationDescription, restClient);
            fillOperationsOutputs(instanceInformations, applicationDescription, restClient);
            FunctionEvaluator.postProcessInstanceInformation(instanceInformations, deploymentInfo.topology, deploymentInfo.paaSTopology);
            instanceStatusByDeployments.put(deploymentPaaSId, new NodesDeploymentInfo(instanceInformations));
            return instanceInformations;
        } catch (RestClientException e) {
            log.warn("Error getting " + deploymentPaaSId + " deployment informations. \n\t Cause: " + e.getMessageFormattedText());
            return Maps.newHashMap();
        } catch (Exception e) {
            throw new PaaSTechnicalException("Error getting " + deploymentPaaSId + " deployment informations", e);
        }
    }

    @Override
    public void getStatus(PaaSDeploymentContext deploymentContext, IPaaSCallback<DeploymentStatus> callback) {
        callback.onSuccess(getStatus(deploymentContext.getDeploymentPaaSId()));
    }

    @Override
    public void getInstancesInformation(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
        callback.onSuccess(getInstancesInformation(deploymentContext.getDeploymentPaaSId()));
    }

    private void fillInstanceStateEvent(PaaSInstanceStateMonitorEvent monitorEvent, NodesDeploymentInfo current, String nodeId, String instanceId) {
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

        monitorEvent.setInstanceStatus(instanceInfo.getInstanceStatus());
        monitorEvent.setRuntimeProperties(instanceInfo.getRuntimeProperties());
        monitorEvent.setAttributes(instanceInfo.getAttributes());
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

            // get instance status events
            List<AlienEvent> instanceEvents = listener.getEventsSince(date, maxEvents);

            Set<String> processedDeployments = Sets.newHashSet();
            processEvents(events, instanceEvents, processedDeployments);

            // cleanup statuses
            if (appUnknownStatuses.size() > 0) {
                cleanupUnknownApplicationsStatuses(listener, appUnknownStatuses);
            }
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
            String deploymentId = info == null ? null : info.deploymentId;
            if (!isUndeploymentTriggered(oldStatus) && !status.equals(oldStatus)) {
                boolean found = updateStatus(deploymentId, applicationDescription.getApplicationName(), status);
                if (found) {
                    PaaSDeploymentStatusMonitorEvent dsMonitorEvent = new PaaSDeploymentStatusMonitorEvent();
                    dsMonitorEvent.setDeploymentId(deploymentId);
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

            NodesDeploymentInfo currentNodesDeploymentInfo;
            if (processedDeployments.add(alienEvent.getApplicationName())) {
                currentNodesDeploymentInfo = new NodesDeploymentInfo();
                currentNodesDeploymentInfo.instanceInformations = getInstancesInformation(alienEvent.getApplicationName());
            } else {
                currentNodesDeploymentInfo = instanceStatusByDeployments.get(alienEvent.getApplicationName());
            }
            PaaSInstanceStateMonitorEvent monitorEvent;
            if (alienEvent instanceof BlockStorageEvent) {
                String volumeId = ((BlockStorageEvent) alienEvent).getVolumeId();
                Deployment deployment = deploymentService.getDeploymentByPaaSId(alienEvent.getApplicationName());
                String isDeletable = deployment.getDeploymentSetup().getProviderDeploymentProperties().get(DeploymentPropertiesNames.DELETABLE_BLOCKSTORAGE);
                monitorEvent = new PaaSInstanceStorageMonitorEvent(volumeId, Boolean.parseBoolean(isDeletable));
            } else {
                monitorEvent = new PaaSInstanceStateMonitorEvent();
            }

            monitorEvent.setDeploymentId(alienEvent.getDeploymentId());
            monitorEvent.setNodeTemplateId(alienEvent.getServiceName());
            monitorEvent.setInstanceId(alienEvent.getInstanceId());
            monitorEvent.setDate(alienEvent.getDateTimestamp().getTime());
            monitorEvent.setInstanceState(alienEvent.getEvent());

            fillInstanceStateEvent(monitorEvent, currentNodesDeploymentInfo, alienEvent.getServiceName(), alienEvent.getInstanceId());
            events.add(monitorEvent);
        }

    }

    private boolean isUndeploymentTriggered(DeploymentStatus status) {
        return DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS.equals(status) || DeploymentStatus.UNDEPLOYED.equals(status);
    }

    private synchronized void cleanupUnknownApplicationsStatuses(CloudifyEventsListener listener, List<String> appUnknownStatuses) throws URISyntaxException,
            IOException {
        for (String appUnknownStatus : appUnknownStatuses) {
            DeploymentInfo deploymentInfo = statusByDeployments.get(appUnknownStatus);
            if (DeploymentStatus.DEPLOYMENT_IN_PROGRESS.equals(deploymentInfo.deploymentStatus)
                    || DeploymentStatus.UNKNOWN.equals(deploymentInfo.deploymentStatus)) {
                if (deploymentInfo.deploymentDate + MAX_DEPLOYMENT_TIMEOUT_MILLIS < new Date().getTime()) {
                    log.info("Deployment has timed out... setting as undeployed...");
                    registerDeploymentStatus(deploymentInfo.deploymentId, DeploymentStatus.UNDEPLOYED);
                    cleanupUnmanagedApplicationInfos(listener, appUnknownStatus, true);
                }
            } else {
                registerDeploymentStatus(deploymentInfo.deploymentId, DeploymentStatus.UNDEPLOYED);
                cleanupUnmanagedApplicationInfos(listener, appUnknownStatus, true);
            }
        }
    }

    private void cleanupUnmanagedApplicationInfos(CloudifyEventsListener listener, String deploymentPaaSId, boolean deleteRecipe) throws URISyntaxException,
            IOException {
        String deploymentId = statusByDeployments.get(deploymentPaaSId).deploymentId;
        if (deleteRecipe) {
            log.info("Cleanup unmanaged application <" + deploymentPaaSId + ">.");
            statusByDeployments.remove(deploymentPaaSId);
            instanceStatusByDeployments.remove(deploymentPaaSId);
            try {
                recipeGenerator.deleteDirectory(deploymentPaaSId);
            } catch (IOException e) {
                log.info("Failed to delete deployment recipe directory <" + deploymentPaaSId + ">.");
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

    @Override
    public void executeOperation(PaaSTopologyDeploymentContext deploymentContext, NodeOperationExecRequest request, IPaaSCallback<Map<String, String>> callback)
            throws OperationExecutionException {
        String deploymentPaaSId = deploymentContext.getDeploymentPaaSId();
        Map<String, String> operationResponse = Maps.newHashMap();
        String serviceName = retrieveServiceName(deploymentPaaSId, request.getNodeTemplateName());
        InvokeCustomCommandRequest invokeRequest = new InvokeCustomCommandRequest();
        invokeRequest.setCommandName(AlienUtils.prefixWithDefaultSeparator(request.getOperationName(), request.getNodeTemplateName(),
                request.getInterfaceName()));
        buildParameters(deploymentPaaSId, request, invokeRequest);
        String operationFQN = operationFQN(serviceName, request, invokeRequest);
        try {
            log.info("Trigerring operation <" + operationFQN + ">.");
            invoke(deploymentPaaSId, serviceName, request.getInstanceId(), invokeRequest, operationResponse);
        } catch (RestClientException e) {
            callback.onFailure(new PaaSTechnicalException("Unable to execute the operation <" + operationFQN + ">.\n\t Cause: " + e.getMessageFormattedText(),
                    e));
        } catch (PluginConfigurationException e) {
            callback.onFailure(new PaaSTechnicalException("Unable to execute the operation <" + operationFQN + ">.\n\t Cause: " + e.getMessage(), e));
        }

        log.debug("Result is: \n" + operationResponse);
        callback.onSuccess(operationResponse);
    }

    private void invoke(String deploymentPaaSId, String serviceName, String instanceId, InvokeCustomCommandRequest invokeRequest,
            Map<String, String> responseMap) throws RestClientException, PluginConfigurationException {
        RestClient restClient = cloudifyRestClientManager.getRestClient();
        // case execute on an instance
        if (StringUtils.isNotBlank(instanceId)) {
            InvokeInstanceCommandResponse response = restClient.invokeInstanceCommand(deploymentPaaSId, serviceName, Integer.parseInt(instanceId),
                    invokeRequest);
            log.debug("RAW result is: \n" + response.getInvocationResult());
            parseInstanceInvokeResponse(responseMap, response.getInvocationResult());
        } else { // case execute on all instances (on the service level)
            InvokeServiceCommandResponse response = restClient.invokeServiceCommand(deploymentPaaSId, serviceName, invokeRequest);
            log.debug("RAW result is: \n" + response.getInvocationResultPerInstance());
            parseServiceInvokeResponse(responseMap, response.getInvocationResultPerInstance());
        }
    }

    @Override
    public void switchInstanceMaintenanceMode(PaaSDeploymentContext deploymentContext, String nodeId, String instanceId, boolean maintenanceModeOn)
            throws MaintenanceModeException {
        switchMaintenanceModeOnNode(deploymentContext.getDeploymentPaaSId(), nodeId, instanceId, maintenanceModeOn);
    }

    @Override
    public void switchMaintenanceMode(PaaSDeploymentContext deploymentContext, boolean maintenanceModeOn) throws MaintenanceModeException {
        List<PaaSNodeTemplate> computes = statusByDeployments.get(deploymentContext.getDeploymentPaaSId()).paaSTopology.getComputes();
        for (PaaSNodeTemplate compute : computes) {
            switchMaintenanceModeOnNode(deploymentContext.getDeploymentPaaSId(), compute.getId(), null, maintenanceModeOn);
        }
    }

    private Map<String, String> switchMaintenanceModeOnNode(String deploymentPaaSId, String nodeId, String instanceId, boolean maintenanceModeOn)
            throws MaintenanceModeException {

        Map<String, String> responseMap = Maps.newHashMap();
        String serviceName = retrieveServiceName(deploymentPaaSId, nodeId);
        String nodeFQN = serviceName + (StringUtils.isNotBlank(instanceId) ? "[" + instanceId + "]" : "");
        InvokeCustomCommandRequest invokeRequest = new InvokeCustomCommandRequest();
        InstanceStateContext stateContext = new InstanceStateContext();
        StringBuilder logMsgeBuilder = new StringBuilder();

        // build proper status, state, log message and invoke command based on maintenance mode
        buildMaintenanceContext(invokeRequest, logMsgeBuilder, stateContext, maintenanceModeOn);
        String logMsge = String.format(logMsgeBuilder.toString(), nodeFQN);
        log.info(logMsge);

        try {
            invoke(deploymentPaaSId, serviceName, instanceId, invokeRequest, responseMap);
            updateInstanceStates(deploymentPaaSId, nodeId, stateContext, responseMap.keySet());
        } catch (RestClientException e) {
            log.error("failed " + logMsge + ".\n\t Cause: " + e.getMessageFormattedText(), e);
            throw new MaintenanceModeException("failed " + logMsge + ".\n\t Cause: " + e.getMessageFormattedText(), e);
        } catch (Exception e) {
            log.error("failed " + logMsge + ".\n\t Cause: " + e.getMessage(), e);
            throw new MaintenanceModeException("failed " + logMsge + ".\n\t Cause: " + e.getMessage(), e);
        }

        log.debug("Result is: \n" + responseMap);
        return responseMap;
    }

    private void buildMaintenanceContext(InvokeCustomCommandRequest invokeRequest, StringBuilder logMsgeBuilder, InstanceStateContext stateContext,
            boolean maintenanceModeOn) {
        if (maintenanceModeOn) {
            invokeRequest.setCommandName(START_MAINTENANCE_COMMAND_NAME);
            invokeRequest.setParameters(Lists.newArrayList(String.valueOf(DEFAULT_MAINENANCE_TIME_MIN)));
            stateContext.status = InstanceStatus.MAINTENANCE;
            stateContext.state = InstanceStatus.MAINTENANCE.toString().toLowerCase();
            logMsgeBuilder.append("Starting maintenance mode for compute <%s>, for " + DEFAULT_MAINENANCE_TIME_MIN + " minutes");
        } else {
            invokeRequest.setCommandName(STOP_MAINTENANCE_COMMAND_NAME);
            stateContext.status = InstanceStatus.SUCCESS;
            stateContext.state = ToscaNodeLifecycleConstants.AVAILABLE;
            logMsgeBuilder.append("Stopping maintenance mode for compute <%s>");
        }

    }

    /**
     *
     * @param deploymentPaaSId
     * @param nodeId
     * @param stateContext
     * @param instanceIds
     * @throws URISyntaxException
     * @throws IOException
     */
    private void updateInstanceStates(String deploymentPaaSId, String nodeId, InstanceStateContext stateContext, Set<String> instanceIds)
            throws URISyntaxException, IOException {
        List<NodeInstanceState> nodeInstancesStatesToUpdate = Lists.newArrayList();

        // update states in local cache
        PaaSNodeTemplate paaSNodeTemplate = statusByDeployments.get(deploymentPaaSId).paaSTopology.getAllNodes().get(nodeId);
        updateInstanceAndChildrenStates(deploymentPaaSId, paaSNodeTemplate, stateContext, nodeInstancesStatesToUpdate, instanceIds);

        // write new states in cloudify space
        final URI restEventEndpoint = this.cloudifyRestClientManager.getRestEventEndpoint();
        CloudifyEventsListener listener = new CloudifyEventsListener(restEventEndpoint, "", "");
        listener.putNodeInstanceStates(nodeInstancesStatesToUpdate);
    }

    private void updateInstanceAndChildrenStates(String deploymentPaaSId, PaaSNodeTemplate paaSNodeTemplate, InstanceStateContext stateContext,
            List<NodeInstanceState> nodeInstanceStates, Set<String> instanceIds) {
        NodesDeploymentInfo deploymentInfo;
        if (instanceStatusByDeployments.isEmpty()) {
            deploymentInfo = new NodesDeploymentInfo(getInstancesInformation(deploymentPaaSId));
        } else {
            deploymentInfo = instanceStatusByDeployments.get(deploymentPaaSId);
        }
        Map<String, InstanceInformation> instancesInfo = deploymentInfo.instanceInformations.get(paaSNodeTemplate.getId());
        for (String instanceId : instanceIds) {
            NodeInstanceState instanceState = new NodeInstanceState();
            instanceState.setInstanceId(instanceId);
            instanceState.setDeploymentId(statusByDeployments.get(deploymentPaaSId).deploymentId);
            instanceState.setNodeTemplateId(paaSNodeTemplate.getId());
            instanceState.setApplicationName(deploymentPaaSId);
            instanceState.setInstanceState(stateContext.state);
            nodeInstanceStates.add(instanceState);
            // change it in the local cash
            instancesInfo.get(instanceId).setInstanceStatus(stateContext.status);
            registerStateChangeEvent(deploymentPaaSId, paaSNodeTemplate.getId(), instanceId, stateContext.state);
        }

        // update for all childs
        for (PaaSNodeTemplate template : paaSNodeTemplate.getChildren()) {
            updateInstanceAndChildrenStates(deploymentPaaSId, template, stateContext, nodeInstanceStates, instanceIds);
        }
    }

    private void registerStateChangeEvent(String deploymentPaaSId, String id, String instanceId, String state) {
        NodesDeploymentInfo nodesDeploymentInfo = instanceStatusByDeployments.get(deploymentPaaSId);
        DeploymentInfo deploymentInfo = statusByDeployments.get(deploymentPaaSId);
        if (deploymentInfo == null) {
            return;
        }
        PaaSInstanceStateMonitorEvent monitorEvent = new PaaSInstanceStateMonitorEvent();
        monitorEvent.setDeploymentId(deploymentInfo.deploymentId);
        monitorEvent.setNodeTemplateId(id);
        monitorEvent.setInstanceId(instanceId);
        monitorEvent.setDate(new Date().getTime());
        monitorEvent.setInstanceState(state);

        fillInstanceStateEvent(monitorEvent, nodesDeploymentInfo, id, instanceId);
        monitorEvents.add(monitorEvent);
    }

    private void buildParameters(String deploymentPaaSId, NodeOperationExecRequest request, InvokeCustomCommandRequest invokeRequest) {
        invokeRequest.setParameters(Lists.<String> newArrayList());
        if (MapUtils.isNotEmpty(request.getParameters())) {
            for (Entry<String, String> entry : request.getParameters().entrySet()) {
                invokeRequest.getParameters().add(entry.toString());
            }
        }

        // if some params are missing, add them with null value
        IndexedNodeType nodeType = statusByDeployments.get(deploymentPaaSId).paaSTopology.getAllNodes().get(request.getNodeTemplateName())
                .getIndexedToscaElement();
        Map<String, IValue> params = nodeType.getInterfaces().get(request.getInterfaceName()).getOperations().get(request.getOperationName())
                .getInputParameters();
        Map<String, String> requestParams = request.getParameters() == null ? Maps.<String, String> newHashMap() : request.getParameters();
        if (params != null) {
            for (Entry<String, IValue> param : params.entrySet()) {
                if (param.getValue().isDefinition() && !requestParams.containsKey(param.getKey())) {
                    invokeRequest.getParameters().add(param.getKey().concat("=").concat("null"));
                }
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

    private String retrieveServiceName(String deploymentPaaSId, String nodeTemplateName) {
        DeploymentInfo deploymentInfo = statusByDeployments.get(deploymentPaaSId);
        if (deploymentInfo == null) {
            throw new PaaSNotYetDeployedException("Application <" + deploymentPaaSId + "> is not deloyed!");
        }
        if (deploymentInfo.paaSTopology == null) {
            deploymentInfo.paaSTopology = topologyTreeBuilderService.buildPaaSTopology(deploymentInfo.topology);
        }
        PaaSNodeTemplate nodeTemplate = deploymentInfo.paaSTopology.getAllNodes().get(nodeTemplateName);
        return CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(nodeTemplate);
    }

    private String operationFQN(String serviceName, NodeOperationExecRequest request, InvokeCustomCommandRequest invokeRequest) {
        StringBuilder fqnBuilder = new StringBuilder(serviceName);
        fqnBuilder.append(".").append(request.getNodeTemplateName());
        if (StringUtils.isNotBlank(request.getInstanceId())) {
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

    /** ****************************************************** **/
    /** *** *** *** *** *** INTERNAL CLASSES *** *** *** *** **/
    /** ***************************************************** **/

    protected static class DeploymentInfo {
        private String deploymentId;
        private Topology topology;
        private DeploymentStatus deploymentStatus;
        private PaaSTopology paaSTopology;
        private long deploymentDate;

        public DeploymentInfo() {
            deploymentStatus = DeploymentStatus.DEPLOYMENT_IN_PROGRESS;
            deploymentDate = System.currentTimeMillis();
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    private static class NodesDeploymentInfo {
        private Map<String, Map<String, InstanceInformation>> instanceInformations;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    private class InstanceStateContext {
        InstanceStatus status;
        String state;
    }

}
