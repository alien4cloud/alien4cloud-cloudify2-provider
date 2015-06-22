package alien4cloud.paas.cloudify2;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DefaultValues {
    public static final int DEFAULT_START_DETECTION_TIMEOUT = 600;
    public static final double DEFAULT_EVENTS_LEASE_IN_HOUR = 2.0;
}
