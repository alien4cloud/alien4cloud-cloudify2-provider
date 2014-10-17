package alien4cloud.paas.cloudify2;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.apache.commons.io.FileUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

/**
 * A velocity Util class
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VelocityUtil {
    private static final Properties VELOCITY_PROPS;
    static {
        VELOCITY_PROPS = new Properties();
        VELOCITY_PROPS.put("resource.loader", "file");
        VELOCITY_PROPS.put("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
        VELOCITY_PROPS.put("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogSystem");
        VELOCITY_PROPS.put("file.resource.loader.cache", "false");
    }

    /**
     * Writes a generated file after injecting properties by velocity engine
     * 
     * @param velocityTemplateFile template file
     * @param outpuFilePath generated file destination
     * @param properties properties to inject in the template
     * @throws IOException when the resource was not found
     */
    public static void writeToOutputFile(Path velocityTemplateFile, Path outpuFilePath, Map<String, ? extends Object> properties) throws IOException {
        String string = writeToString(velocityTemplateFile, properties);
        File generatedFile = outpuFilePath.toFile();
        FileUtils.writeStringToFile(generatedFile, string);
    }

    public static String writeToString(Path velocityTemplateFile, Map<String, ? extends Object> properties) throws IOException {
        VelocityEngine ve = new VelocityEngine();

        // reading template file
        File templateFile = velocityTemplateFile.toFile();
        ve.setProperty("file.resource.loader.path", templateFile.getParent());
        // initialize engine
        ve.init(VELOCITY_PROPS);

        Template t = ve.getTemplate(templateFile.getName());
        VelocityContext context = new VelocityContext();

        for (Entry<String, ? extends Object> entries : properties.entrySet()) {
            context.put(entries.getKey(), entries.getValue());
        }

        StringWriter writer = new StringWriter();
        try {
            t.merge(context, writer);
            return writer.toString();
        } finally {
            writer.close();
        }
    }
}