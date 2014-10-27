package alien4cloud.paas.cloudify2;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

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
import alien4cloud.paas.cloudify2.exception.A4CCloudifyDriverITException;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.tosca.container.archive.CsarUploadService;
import alien4cloud.tosca.container.exception.CSARParsingException;
import alien4cloud.tosca.container.exception.CSARValidationException;
import alien4cloud.tosca.container.model.topology.Topology;
import alien4cloud.utils.FileUtil;
import alien4cloud.utils.YamlParserUtil;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class GenericTestCase {
    protected static final int HTTP_CODE_OK = 200;
    protected static final String DEFAULT_TOMCAT_PORT = "8080";

    protected static final String CSAR_SOURCE_PATH = "src/test/resources/csars/";
    private static final String TOPOLOGIES_PATH = "src/test/resources/topologies/";

    @Resource
    protected CsarUploadService csarUploadService;

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider cloudifyPaaSPovider;
    @Resource
    protected ElasticSearchDAO alienDAO;
    @Resource
    protected CsarFileRepository archiveRepositry;
    protected CloudifyRestClientManager cloudifyRestClientManager;
    @Resource
    private ApplicationService applicationService;
    protected List<String> deployedCloudifyAppIds = new ArrayList<>();

    @BeforeClass
    public static void beforeClass() {
        log.info("In Before Class");
        cleanAlienFiles();
    }

    @Before
    public void before() throws PluginConfigurationException, CSARParsingException, CSARVersionAlreadyExistsException, CSARValidationException, IOException {

        cleanAlienFiles();

        String cloudifyURL = System.getenv("CLOUDIFY_URL");
        // String cloudifyURL = null;
        cloudifyURL = cloudifyURL == null ? "http://129.185.67.86:8100/" : cloudifyURL;
        PluginConfigurationBean pluginConfigurationBean = cloudifyPaaSPovider.getPluginConfigurationBean();
        pluginConfigurationBean.getCloudifyConnectionConfiguration().setCloudifyURL(cloudifyURL);
        pluginConfigurationBean.setSynchronousDeployment(true);
        pluginConfigurationBean.getCloudifyConnectionConfiguration().setVersion("2.7.1");
        cloudifyPaaSPovider.setConfiguration(pluginConfigurationBean);
        cloudifyRestClientManager = cloudifyPaaSPovider.getCloudifyRestClientManager();

        // cloudifyPaaSPovider.setCloudifyRestClientManager(new CloudifyRestClientManager());
    }

    @After
    public void after() {
        try {
            undeployAllApplications();
        } catch (RestClientException | IOException e) {
            log.warn("error in after:", e);
        }
        cleanAlienFiles();
    }

    public static void cleanAlienFiles() {
        FileUtils.deleteQuietly(new File("target/alien"));
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
                // cloudifyPaaSPovider.undeploy(topology.getId());
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

    public void initElasticSearch(String[] csarNames, String[] versions) throws IOException, CSARParsingException, CSARVersionAlreadyExistsException,
            CSARValidationException {
        log.info("Initializing ALIEN repository.");

        for (int i = 0; i < csarNames.length; i++) {
            uploadCsar(csarNames[i], versions[i]);
        }

        // uploadCsar("tosca-base-types", "1.0");
        // uploadCsar("fastconnect-base-types", "0.1");
        // uploadCsar("apache-types", "0.1");
        // uploadCsar("apache-lb-types", "0.2");
        // uploadCsar("tomcat-types", "0.2");
        // uploadCsar("tomcatGroovy-types", "0.1");

        log.info("Types have been added to the repository.");
    }

    public void uploadCsar(String name, String version) throws IOException, CSARParsingException, CSARVersionAlreadyExistsException, CSARValidationException {
        Path inputPath = Paths.get(CSAR_SOURCE_PATH + name + "/" + version);
        Path zipPath = Files.createTempFile("csar", ".zip");
        FileUtil.zip(inputPath, zipPath);
        csarUploadService.uploadCsar(zipPath);
    }

    public void waitForServiceToStarts(final String applicationId, final String serviceName, final long timeoutInMillis) throws RestClientException {
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

    public void assertHttpCodeEquals(String applicationId, String serviceName, String port, String path, int expectedCode, Integer timeoutInMillis)
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

    public int getResponseCode(String urlString) throws IOException {
        URL u = new URL(urlString);
        HttpURLConnection huc = (HttpURLConnection) u.openConnection();
        huc.setRequestMethod("GET");
        huc.connect();
        huc.getResponseCode();
        return huc.getResponseCode();
    }

    public String deployTopology(String topologyFileName, boolean isYamlTopologyFile) throws IOException, JsonParseException, JsonMappingException,
            CSARParsingException, CSARVersionAlreadyExistsException, CSARValidationException {
        String appName = "CloudifyPaaSProvider-IT " + topologyFileName;
        Topology topology = this.createAlienApplication(appName, topologyFileName, isYamlTopologyFile);
        log.info("TESTS: Deploying topology <{}>. Deployment id is <{}>", topologyFileName, topology.getId());
        deployedCloudifyAppIds.add(topology.getId());
        cloudifyPaaSPovider.deploy(appName, topology.getId(), topology, null);
        return topology.getId();
    }

    private Topology createAlienApplication(String applicationName, String topologyFileName, boolean isYamlTopologyFile) throws IOException,
            JsonParseException, JsonMappingException, CSARParsingException, CSARVersionAlreadyExistsException, CSARValidationException {

        Topology topology = isYamlTopologyFile ? parseYamlTopology(topologyFileName) : parseJsonTopology(topologyFileName);

        String applicationId = applicationService.create("alien", applicationName, null, null);
        topology.setDelegateId(applicationId);
        topology.setDelegateType(Application.class.getSimpleName().toLowerCase());
        alienDAO.save(topology);

        log.info("topology.getDelegateId()=" + topology.getDelegateId());
        log.info("topology.getId()=" + topology.getId());

        return topology;
    }

    private Topology parseYamlTopology(String topologyFileName) throws IOException {
        Topology topology = YamlParserUtil.parseFromUTF8File(Paths.get(TOPOLOGIES_PATH.concat("yaml/") + topologyFileName + ".yml"), Topology.class);
        topology.setId(UUID.randomUUID().toString());
        return topology;
    }

    private Topology parseJsonTopology(String topologyFileName) throws IOException {
        String responseAsString = new String(Files.readAllBytes(Paths.get(TOPOLOGIES_PATH + topologyFileName + "-topology.json")));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
        Topology topology = objectMapper.readValue(responseAsString, Topology.class);
        topology.setId(UUID.randomUUID().toString());
        return topology;
    }

    public void assertApplicationIsInstalled(String applicationId) throws RestClientException {
        log.info("Asserting aplication <" + applicationId + "> installed...");
        RestClient restClient = cloudifyRestClientManager.getRestClient();
        ApplicationDescription appliDesc = restClient.getApplicationDescription(applicationId);
        List<ServiceDescription> servicesDescription = appliDesc.getServicesDescription();
        for (ServiceDescription service : servicesDescription) {
            Assert.assertEquals("Service " + service.getServiceName() + " is not in STARTED state", DeploymentState.STARTED, service.getServiceState());
        }
    }
}
