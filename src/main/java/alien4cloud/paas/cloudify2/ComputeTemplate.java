package alien4cloud.paas.cloudify2;

import java.lang.reflect.Field;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.ToString;
import alien4cloud.ui.form.annotation.FormLabel;

/**
 * Represents a cloudify compute template.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ComputeTemplate implements Comparable<ComputeTemplate> {
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.COMPUTE_TEMPLATE.ID")
    private String id;
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.COMPUTE_TEMPLATE.NUM_CPU")
    private int numCpus;
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.COMPUTE_TEMPLATE.DISK_SIZE")
    private int diskSize;
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.COMPUTE_TEMPLATE.MEM_SIZE")
    private int memSize;
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.COMPUTE_TEMPLATE.OS_ARCH")
    private String osArch;
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.COMPUTE_TEMPLATE.OS_TYPE")
    private String osType;
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.COMPUTE_TEMPLATE.OS_DISTRIBUTION")
    private String osDistribution;
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.COMPUTE_TEMPLATE.OS_VERSION")
    private String osVersion;
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.COMPUTE_TEMPLATE.IP_ADDRESS")
    private String ipAddress;

    @Override
    public int compareTo(ComputeTemplate other) {
        if (this.memSize == other.memSize) {
            if (this.numCpus == other.numCpus) {
                return Integer.compare(this.diskSize, other.diskSize);
            }
            return Integer.compare(this.numCpus, other.numCpus);
        }
        return Integer.compare(this.memSize, other.memSize);
    }

    @SneakyThrows({ NoSuchFieldException.class, IllegalAccessException.class })
    public String getValue(String fieldName) {
        Field field = ComputeTemplate.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(this);
    }
}