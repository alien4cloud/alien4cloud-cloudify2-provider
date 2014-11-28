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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.mapping.ElasticSearchClient;
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
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.MatchedComputeTemplate;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.paas.cloudify2.exception.A4CCloudifyDriverITException;
import alien4cloud.plugin.PluginConfiguration;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.container.model.topology.Topology;
import alien4cloud.tosca.model.Csar;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.utils.FileUtil;
import alien4cloud.utils.YamlParserUtil;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class GenericTestCase {
    protected static final int HTTP_CODE_OK = 200;
    protected static final String DEFAULT_TOMCAT_PORT = "8080";

    protected static final String CSAR_SOURCE_PATH = "src/test/resources/csars/";
    private static final String TOPOLOGIES_PATH = "src/test/resources/topologies/";
    private static final String DEFAULT_COMPUTE_TEMPLATE_ID = "MEDIUM_LINUX";

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
    private ElasticSearchClient esClient;

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
        cleanAlienTargetDir();
    }

    @Before
    public void before() throws Throwable {
        log.info("In beforeTest");
        cleanESFiles();

        uploadCsar("tosca-normative-types", "1.0.0.wd03-SNAPSHOT");
        uploadCsar("fastconnect-base-types", "1.0");

        String cloudifyURL = System.getenv("CLOUDIFY_URL");
        // String cloudifyURL = null;
        cloudifyURL = cloudifyURL == null ? "http://129.185.67.64:8100/" : cloudifyURL;
        PluginConfigurationBean pluginConfigurationBean = cloudifyPaaSPovider.getPluginConfigurationBean();
        pluginConfigurationBean.getCloudifyConnectionConfiguration().setCloudifyURL(cloudifyURL);
        pluginConfigurationBean.setSynchronousDeployment(true);
        pluginConfigurationBean.getCloudifyConnectionConfiguration().setVersion("2.7.1");
        cloudifyPaaSPovider.setConfiguration(pluginConfigurationBean);
        cloudifyRestClientManager = cloudifyPaaSPovider.getCloudifyRestClientManager();
        CloudResourceMatcherConfig matcherConf = new CloudResourceMatcherConfig();
        matcherConf.setMatchedComputeTemplates(Lists.newArrayList(new MatchedComputeTemplate(new ComputeTemplate(null, DEFAULT_COMPUTE_TEMPLATE_ID),
                DEFAULT_COMPUTE_TEMPLATE_ID)));
        cloudifyPaaSPovider.updateMatcherConfig(matcherConf);
    }

    @After
    public void after() {
        log.info("In afterTest");
        try {
            undeployAllApplications();
        } catch (RestClientException | IOException e) {
            log.warn("error in after:", e);
        }
    }

    public static void cleanAlienTargetDir() {
        FileUtils.deleteQuietly(new File("target/alien"));
    }

    public void cleanESFiles() throws Throwable {
        log.info("Cleaning repositories files");
        if (Files.exists(Paths.get("target/tmp/"))) {
            FileUtil.delete(Paths.get("target/tmp/"));
        }
        Path csarrepo = Paths.get("target/alien/csar");
        if (Files.exists(csarrepo)) {
            FileUtil.delete(csarrepo);
        }

        Files.createDirectories(csarrepo);

        // Clean elastic search cluster
        for (Class<?> indiceClass : IndiceClassesToClean) {
            esClient.getClient().prepareDeleteByQuery(new String[] { indiceClass.getSimpleName().toLowerCase() }).setQuery(QueryBuilders.matchAllQuery())
                    .execute().get();
        }
        esClient.getClient().prepareDeleteByQuery(new String[] { ElasticSearchDAO.TOSCA_ELEMENT_INDEX }).setQuery(QueryBuilders.matchAllQuery()).execute()
                .get();
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

    protected void initElasticSearch(String[] csarNames, String[] versions) throws IOException, CSARVersionAlreadyExistsException, ParsingException {
        log.info("Initializing ALIEN repository.");

        for (int i = 0; i < csarNames.length; i++) {
            uploadCsar(csarNames[i], versions[i]);
        }
        log.info("Types have been added to the repository.");
    }

    protected void uploadCsar(String name, String version) throws IOException, CSARVersionAlreadyExistsException, ParsingException {
        Path inputPath = Paths.get(CSAR_SOURCE_PATH + name + "/" + version);
        Path zipPath = Files.createTempFile("csar", ".zip");
        FileUtil.zip(inputPath, zipPath);
        archiveUploadService.upload(zipPath);
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

    protected String deployTopology(String topologyFileName, String[] computesId) throws IOException, JsonParseException, JsonMappingException,
            CSARVersionAlreadyExistsException, ParsingException {
        Topology topology = this.createAlienApplication(topologyFileName, topologyFileName);
        return deployTopology(computesId, topology, topologyFileName);
    }

    protected String deployTopology(String[] computesId, Topology topology, String topologyFileName) {
        DeploymentSetup setup = new DeploymentSetup();
        setup.setCloudResourcesMapping(Maps.<String, ComputeTemplate> newHashMap());
        if (computesId != null) {
            for (String string : computesId) {
                setup.getCloudResourcesMapping().put(string, new ComputeTemplate(null, DEFAULT_COMPUTE_TEMPLATE_ID));
            }
        }
        log.info("\n\n TESTS: Deploying topology <{}>. Deployment id is <{}>. \n", topologyFileName, topology.getId());
        deployedCloudifyAppIds.add(topology.getId());
        cloudifyPaaSPovider.deploy(topologyFileName, topology.getId(), topology, setup);
        return topology.getId();
    }

    protected Topology createAlienApplication(String applicationName, String topologyFileName) throws IOException, JsonParseException, JsonMappingException,
            ParsingException, CSARVersionAlreadyExistsException {

        Topology topology = parseYamlTopology(topologyFileName);

        String applicationId = applicationService.create("alien", applicationName, null, null);
        topology.setDelegateId(applicationId);
        topology.setDelegateType(Application.class.getSimpleName().toLowerCase());
        alienDAO.save(topology);

        log.info("topology.getDelegateId()=" + topology.getDelegateId());
        log.info("topology.getId()=" + topology.getId());

        return topology;
    }

    private Topology parseYamlTopology(String topologyFileName) throws IOException {
        Topology topology = YamlParserUtil.parseFromUTF8File(Paths.get(TOPOLOGIES_PATH + topologyFileName + ".yml"), Topology.class);
        topology.setId(UUID.randomUUID().toString());
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

    protected Set<String> getMissingEvents(Set<String> expectedEvents, Set<String> currentEvents) {
        Set<String> missing = new HashSet<>();
        for (String event : expectedEvents) {
            if (!currentEvents.contains(event)) {
                missing.add(event);
            }
        }
        return missing;
    }
}
