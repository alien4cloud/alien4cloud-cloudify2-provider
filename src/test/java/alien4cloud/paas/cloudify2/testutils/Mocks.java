package alien4cloud.paas.cloudify2.testutils;

import org.mockito.Answers;
import org.mockito.Mockito;

import alien4cloud.paas.cloudify2.CloudifyRestClientManager;

// Cannot use @Configuration : https://jira.spring.io/browse/SPR-9567
// @Configuration
public class Mocks {

    // @Bean
    // @Primary
    public static CloudifyRestClientManager createCloudifyRestClientManager() {
        return Mockito.mock(CloudifyRestClientManager.class, Answers.RETURNS_DEEP_STUBS.get());
    }

}
