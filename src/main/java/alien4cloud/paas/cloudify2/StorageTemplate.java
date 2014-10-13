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
 * Represents a cloudify storage template.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class StorageTemplate implements Comparable<StorageTemplate> {
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.STORAGE_TEMPLATE.ID")
    private String id;
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.STORAGE_TEMPLATE.SIZE")
    private int size;
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.STORAGE_TEMPLATE.FS")
    private String fileSystemType;

    @Override
    public int compareTo(StorageTemplate other) {
        return Integer.compare(this.size, other.size);
    }

    @SneakyThrows({ NoSuchFieldException.class, IllegalAccessException.class })
    public String getValue(String fieldName) {
        Field field = StorageTemplate.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(this);
    }
}