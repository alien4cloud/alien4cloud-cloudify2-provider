package alien4cloud.paas.cloudify2;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("PMD.UnusedPrivateField")
public class GeneratedCloudifyComputeTemplate extends CloudifyComputeTemplate {
    private String availabilityZone;

    public GeneratedCloudifyComputeTemplate(String imageId, String hardwareId, String availabilityZone) {
        super(imageId, hardwareId);
        this.availabilityZone = availabilityZone;
    }
}
