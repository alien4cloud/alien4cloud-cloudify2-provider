package alien4cloud.paas.cloudify2;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.FileUtils;
import org.cloudifysource.dsl.internal.CloudifyConstants.DeploymentState;
import org.cloudifysource.dsl.rest.response.ApplicationDescription;
import org.cloudifysource.dsl.rest.response.GetMachinesDumpFileResponse;
import org.cloudifysource.dsl.rest.response.ServiceDescription;
import org.cloudifysource.dsl.rest.response.ServiceInstanceDetails;
import org.cloudifysource.restclient.RestClient;
import org.cloudifysource.restclient.exceptions.RestClientException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.test.context.ContextConfiguration;

import alien4cloud.application.ApplicationService;
import alien4cloud.component.repository.CsarFileRepository;
import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.dao.ElasticSearchDAO;
import alien4cloud.model.application.Application;
import alien4cloud.model.application.ApplicationEnvironment;
import alien4cloud.model.application.ApplicationVersion;
import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.cloud.CloudImage;
import alien4cloud.model.cloud.CloudImageFlavor;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.model.components.Csar;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.cloudify2.events.AlienEvent;
import alien4cloud.paas.cloudify2.exception.A4CCloudifyDriverITException;
import alien4cloud.paas.cloudify2.testutils.TestsUtils;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.plugin.PluginConfiguration;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.parser.ParsingException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class GenericTestCase {

    protected static final int HTTP_CODE_OK = 200;

    protected static final String DEFAULT_TOMCAT_PORT = "8080";

    protected static final String ALIEN_WINDOWS_IMAGE = "alienWindowsImage";

    protected static final String ALIEN_LINUX_IMAGE = "alienLinuxImage";

    protected static final String ALIEN_FLAVOR = "alienFlavor";

    public static final String IAAS_IMAGE_ID = "RegionOne/2b4475df-b6d6-49b7-a062-a3a20d45ab7c";

    public static final String EXTENDED_TYPES_REPO = "alien-extended-types";

    public static final String EXTENDED_STORAGE_TYPES = "alien-extended-storage-types-1.0-SNAPSHOT";
    public static final String IAAS_FLAVOR_ID = "RegionOne/2";
    public static final String ALIEN_STORAGE = "alienStorage";
    public static final String ALIEN_STORAGE_DEVICE = "/dev/vdb";
    public static final String IAAS_BLOCK_STORAGE_ID = "SMALL_BLOCK";

    @Resource
    protected ArchiveUploadService archiveUploadService;

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider cloudifyPaaSPovider;

    @Resource
    protected ElasticSearchDAO alienDAO;

    @Resource
    protected CsarFileRepository archiveRepositry;

    protected CloudifyRestClientManager cloudifyRestClientManager;

    @Resource
    private ApplicationService applicationService;

    @Resource
    protected TestsUtils testsUtils;

    @Resource
    private TopologyTreeBuilderService topologyTreeBuilderService;

    protected List<String> deployedCloudifyAppIds = new ArrayList<>();

    private List<Class<?>> IndiceClassesToClean;

    public GenericTestCase() {
        IndiceClassesToClean = Lists.newArrayList();
        IndiceClassesToClean.add(ApplicationEnvironment.class);
        IndiceClassesToClean.add(ApplicationVersion.class);
        IndiceClassesToClean.add(DeploymentSetup.class);
        IndiceClassesToClean.add(Application.class);
        IndiceClassesToClean.add(Csar.class);
        IndiceClassesToClean.add(Topology.class);
        IndiceClassesToClean.add(PluginConfiguration.class);
        IndiceClassesToClean.add(Deployment.class);
    }

    @BeforeClass
    public static void beforeClass() {
        TestsUtils.cleanAlienTargetDir();
    }

    @Before
    public void before() throws Throwable {
        log.info("In beforeTest");
        testsUtils.cleanESFiles(IndiceClassesToClean);
        testsUtils.uploadGitArchive("tosca-normative-types-1.0.0.wd03", "");
        testsUtils.uploadGitArchive("alien-extended-types", "alien-base-types-1.0-SNAPSHOT");

        String cloudifyURL = System.getenv("CLOUDIFY_URL");
        cloudifyURL = cloudifyURL == null ? "http://129.185.67.109:8100/" : cloudifyURL;
        PluginConfigurationBean pluginConfigurationBean = cloudifyPaaSPovider.getPluginConfigurationBean();
        pluginConfigurationBean.getCloudifyConnectionConfiguration().setCloudifyURL(cloudifyURL);
        pluginConfigurationBean.setSynchronousDeployment(true);
        pluginConfigurationBean.getCloudifyConnectionConfiguration().setVersion("2.7.1");
        cloudifyPaaSPovider.setConfiguration(pluginConfigurationBean);
        cloudifyRestClientManager = cloudifyPaaSPovider.getCloudifyRestClientManager();
        CloudResourceMatcherConfig matcherConf = new CloudResourceMatcherConfig();

        Map<CloudImage, String> imageMapping = Maps.newHashMap();
        CloudImage cloudImage = new CloudImage();
        cloudImage.setId(ALIEN_LINUX_IMAGE);
        imageMapping.put(cloudImage, IAAS_IMAGE_ID);
        cloudImage = new CloudImage();
        cloudImage.setId(ALIEN_WINDOWS_IMAGE);
        imageMapping.put(cloudImage, IAAS_IMAGE_ID);
        matcherConf.setImageMapping(imageMapping);

        Map<CloudImageFlavor, String> flavorMapping = Maps.newHashMap();
        flavorMapping.put(new CloudImageFlavor(ALIEN_FLAVOR, 1, 1L, 1L), IAAS_FLAVOR_ID);
        matcherConf.setFlavorMapping(flavorMapping);

        Map<StorageTemplate, String> storageMapping = Maps.newHashMap();
        storageMapping.put(new StorageTemplate(ALIEN_STORAGE, 1L, ALIEN_STORAGE_DEVICE), IAAS_BLOCK_STORAGE_ID);
        matcherConf.setStorageMapping(storageMapping);
        cloudifyPaaSPovider.updateMatcherConfig(matcherConf);
    }

    @After
    public void after() {
        log.info("In afterTest");
        try {
            undeployAllApplications();
        } catch (RestClientException | IOException e) {
            String msge = "";
            if (e instanceof RestClientException) {
                msge = ((RestClientException) e).getMessageFormattedText();
            }
            log.warn("error in after: " + msge, e);
        }
    }

    private void undeployAllApplications() throws RestClientException, IOException {

        RestClient restClient = cloudifyRestClientManager.getRestClient();
        if (restClient == null) {
            return;
        }
        dumpMachinesLogs();
        for (String id : deployedCloudifyAppIds) {
            try {
                log.info("trying to undeploy aplication <" + id + ">.");
                restClient.uninstallApplication(id, (int) (1000L * 60L * 10L));
                log.info("cloudify app <" + id + "> undeployed.");
            } catch (Throwable t) {
                log.error("failed to undeploy application <" + id + ">.");
            }
        }
        deployedCloudifyAppIds.clear();
    }

    private void dumpMachinesLogs() throws RestClientException, IOException {
        RestClient restClient = cloudifyRestClientManager.getRestClient();
        GetMachinesDumpFileResponse machinesDumpFile = restClient.getMachinesDumpFile(null, 0);
        Map<String, byte[]> dumpBytesPerIP = machinesDumpFile.getDumpBytesPerIP();
        Map<String, File> dumpFilesPerIP = new HashMap<String, File>(dumpBytesPerIP.size());
        for (Entry<String, byte[]> entry : dumpBytesPerIP.entrySet()) {
            File file = File.createTempFile("dump", ".zip");
            file.deleteOnExit();
            FileUtils.writeByteArrayToFile(file, entry.getValue());
            dumpFilesPerIP.put(entry.getKey(), file);
        }
        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss-SSS");
        for (Entry<String, File> entry : dumpFilesPerIP.entrySet()) {
            Date date = new Date();
            File zipFile = new File("./testit-logs", dateFormat.format(date) + "_ip" + entry.getKey() + "_dump.zip");
            FileUtils.moveFile(entry.getValue(), zipFile);
            log.info("> Logs: " + zipFile.getAbsolutePath() + "\n");
        }
    }

    protected void uploadTestArchives(String... csarNames) throws IOException, CSARVersionAlreadyExistsException, ParsingException {
        log.info("Initializing ALIEN repository.");
        for (String name : csarNames) {
            testsUtils.uploadArchive(name);
        }
        log.info("Types have been added to the repository.");
    }

    protected void uploadGitArchive(String repository, String archiveDirectoryName) throws Exception {
        testsUtils.uploadGitArchive(repository, archiveDirectoryName);
    }

    protected void waitForServiceToStarts(final String applicationId, final String serviceName, final long timeoutInMillis) throws RestClientException {
        CloudifyRestClient restClient = this.cloudifyRestClientManager.getRestClient();
        DeploymentState serviceState = null;
        long startTime = System.currentTimeMillis();
        boolean timeout = false;
        while (DeploymentState.STARTED != serviceState && !timeout) {
            try {
                Thread.sleep(10000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ServiceDescription serviceDescription = restClient.getServiceDescription(applicationId, serviceName);
            serviceState = serviceDescription.getServiceState();
            timeout = System.currentTimeMillis() - startTime > timeoutInMillis;
        }

        if (timeout) {
            throw new A4CCloudifyDriverITException("Expected application to be started but timeout of <" + timeoutInMillis + "> was reached.");
        }
    }

    protected void assertHttpCodeEquals(String applicationId, String serviceName, String port, String path, int expectedCode, Integer timeoutInMillis)
            throws RestClientException, IOException, InterruptedException {
        log.info("About to check path <:" + port.concat("/").concat(path) + ">");
        CloudifyRestClient restClient = this.cloudifyRestClientManager.getRestClient();
        ServiceInstanceDetails instanceDetails = restClient.getServiceInstanceDetails(applicationId, serviceName, 1);
        String instancePublicIp = instanceDetails.getPublicIp();
        String urlString = "http://" + instancePublicIp + ":" + port + "/" + path;
        log.info("Full URL is: " + urlString);
        int httpResponseCode = 0;
        if (expectedCode == 404) {
            httpResponseCode = this.getResponseCode(urlString);
        } else {
            long now = System.currentTimeMillis();
            long finalTimeout = timeoutInMillis != null ? timeoutInMillis : 0L;
            do {
                Thread.sleep(1000L);
                httpResponseCode = this.getResponseCode(urlString);
            } while (System.currentTimeMillis() - now < finalTimeout && httpResponseCode == 404);
        }
        Assert.assertEquals("Expected Response code " + expectedCode + " got " + httpResponseCode, expectedCode, httpResponseCode);
    }

    protected int getResponseCode(String urlString) throws IOException {
        URL u = new URL(urlString);
        HttpURLConnection huc = (HttpURLConnection) u.openConnection();
        huc.setRequestMethod("GET");
        huc.connect();
        huc.getResponseCode();
        return huc.getResponseCode();
    }

    protected String deployTopology(String topologyFileName, String[] computesId, Map<String, ComputeTemplate> computesMatching) throws IOException,
            JsonParseException, JsonMappingException, CSARVersionAlreadyExistsException, ParsingException {
        Topology topology = this.createAlienApplication(topologyFileName, topologyFileName);
        return deployTopology(computesId, topology, topologyFileName, computesMatching);
    }

    protected String deployTopology(String[] computesId, Topology topology, String topologyFileName, Map<String, ComputeTemplate> computesMatching) {
        DeploymentSetup setup = new DeploymentSetup();
        setup.setCloudResourcesMapping(Maps.<String, ComputeTemplate> newHashMap());
        if (computesId != null) {
            for (String string : computesId) {
                setup.getCloudResourcesMapping().put(string, new ComputeTemplate(ALIEN_LINUX_IMAGE, ALIEN_FLAVOR));
            }
        }
        if (computesMatching != null) {
            setup.getCloudResourcesMapping().putAll(computesMatching);
        }
        setup.setStorageMapping(Maps.<String, StorageTemplate> newHashMap());
        log.info("\n\n TESTS: Deploying topology <{}>. Deployment id is <{}>. \n", topologyFileName, topology.getId());
        deployedCloudifyAppIds.add(topology.getId());
        PaaSTopologyDeploymentContext deploymentContext = new PaaSTopologyDeploymentContext();
        deploymentContext.setDeploymentSetup(setup);
        deploymentContext.setTopology(topology);
        deploymentContext.setRecipeId(topologyFileName);
        deploymentContext.setDeploymentId(topology.getId());
        Map<String, PaaSNodeTemplate> nodes = topologyTreeBuilderService.buildPaaSNodeTemplate(topology);
        PaaSTopology paaSTopology = topologyTreeBuilderService.buildPaaSTopology(nodes);
        deploymentContext.setPaaSTopology(paaSTopology);
        for (PaaSNodeTemplate volume : paaSTopology.getVolumes()) {
            setup.getStorageMapping().put(volume.getId(), new StorageTemplate(ALIEN_STORAGE, 1L, ALIEN_STORAGE_DEVICE));
        }
        cloudifyPaaSPovider.deploy(deploymentContext, null);
        return topology.getId();
    }

    protected Topology createAlienApplication(String applicationName, String topologyFileName) throws IOException, JsonParseException, JsonMappingException,
            ParsingException, CSARVersionAlreadyExistsException {

        Topology topology = testsUtils.parseYamlTopology(topologyFileName);

        String applicationId = applicationService.create("alien", applicationName, null, null);
        topology.setDelegateId(applicationId);
        topology.setDelegateType(Application.class.getSimpleName().toLowerCase());
        alienDAO.save(topology);

        log.info("topology.getDelegateId()=" + topology.getDelegateId());
        log.info("topology.getId()=" + topology.getId());

        return topology;
    }

    protected void assertApplicationIsInstalled(String applicationId) throws RestClientException {
        log.info("Asserting aplication <" + applicationId + "> installed...");
        RestClient restClient = cloudifyRestClientManager.getRestClient();
        ApplicationDescription appliDesc = restClient.getApplicationDescription(applicationId);
        List<ServiceDescription> servicesDescription = appliDesc.getServicesDescription();
        for (ServiceDescription service : servicesDescription) {
            Assert.assertEquals("Service " + service.getServiceName() + " is not in STARTED state", DeploymentState.STARTED, service.getServiceState());
        }
    }

    protected void testEvents(String applicationId, String[] nodeTemplateNames, long timeoutInMillis, String... expectedEvents) throws Exception {
        ApplicationDescription applicationDescription = cloudifyRestClientManager.getRestClient().getApplicationDescription(applicationId);
        for (String nodeName : nodeTemplateNames) {
            this.assertFiredEvents(nodeName, new HashSet<String>(Arrays.asList(expectedEvents)), applicationDescription, timeoutInMillis);
        }
    }

    protected void assertFiredEvents(String nodeName, Set<String> expectedEvents, ApplicationDescription applicationDescription, long timeoutInMillis)
            throws Exception {
        Set<String> currentEvents = new HashSet<>();
        String serviceName = nodeName;
        for (ServiceDescription service : applicationDescription.getServicesDescription()) {
            long timeout = System.currentTimeMillis() + timeoutInMillis;
            boolean passed = false;
            String applicationName = service.getApplicationName();
            CloudifyEventsListener listener = new CloudifyEventsListener(cloudifyRestClientManager.getRestEventEndpoint(), applicationName, serviceName);
            do {
                currentEvents.clear();
                List<AlienEvent> allServiceEvents = listener.getEvents();
                for (AlienEvent alienEvent : allServiceEvents) {
                    currentEvents.add(alienEvent.getEvent());
                }
                passed = currentEvents.containsAll(expectedEvents);
            } while (System.currentTimeMillis() < timeout && !passed);
            log.info("Application: " + applicationName + "." + serviceName + " got events : " + currentEvents);
            Assert.assertTrue("Missing events for node <" + serviceName + ">: " + getMissingEvents(expectedEvents, currentEvents), passed);
        }
    }

    protected Set<String> getMissingEvents(Set<String> expectedEvents, Set<String> currentEvents) {
        Set<String> missing = new HashSet<>();
        for (String event : expectedEvents) {
            if (!currentEvents.contains(event)) {
                missing.add(event);
            }
        }
        return missing;
    }

    protected void testCustomCommandFail(String applicationId, String nodeName, Integer instanceId, String command, Map<String, String> params) {
        try {
            executeCustomCommand(applicationId, nodeName, instanceId, command, params, new IPaaSCallback<Map<String, String>>() {
                @Override
                public void onSuccess(Map<String, String> data) {
                    Assert.fail();
                }

                @Override
                public void onFailure(Throwable throwable) {

                }
            });
        } catch (OperationExecutionException e) {
        }
    }

    protected void testCustomCommandSuccess(String cloudifyAppId, String nodeName, Integer instanceId, String command, Map<String, String> params,
            final String expectedResultSnippet) {
        executeCustomCommand(cloudifyAppId, nodeName, instanceId, command, params, new IPaaSCallback<Map<String, String>>() {
            @Override
            public void onSuccess(Map<String, String> result) {

                if (expectedResultSnippet != null) {
                    for (String opReslt : result.values()) {
                        Assert.assertTrue("Command result is <" + opReslt.toLowerCase() + ">. It should have contain <" + expectedResultSnippet + ">", opReslt
                                .toLowerCase().contains(expectedResultSnippet.toLowerCase()));
                    }
                }
            }

            @Override
            public void onFailure(Throwable throwable) {

            }
        });
    }

    protected void executeCustomCommand(String cloudifyAppId, String nodeName, Integer instanceId, String command, Map<String, String> params,
            IPaaSCallback<Map<String, String>> callback) {
        if (!deployedCloudifyAppIds.contains(cloudifyAppId)) {
            Assert.fail("Topology not found in deployments");
        }
        NodeOperationExecRequest request = new NodeOperationExecRequest();
        request.setInterfaceName("custom");
        request.setOperationName(command);
        request.setNodeTemplateName(nodeName);

        if (instanceId != null) {
            request.setInstanceId(instanceId.toString());
        }
        request.setParameters(params);
        PaaSDeploymentContext deploymentContext = new PaaSDeploymentContext();
        deploymentContext.setDeploymentId(cloudifyAppId);
        cloudifyPaaSPovider.executeOperation(deploymentContext, request, callback);
    }
}
