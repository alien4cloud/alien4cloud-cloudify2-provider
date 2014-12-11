package alien4cloud.paas.cloudify2.testutils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import com.google.common.collect.Maps;

public class EraseMe {

    private static final String EXECUTE_ASYNC_FORMAT = "CloudifyExecutorUtils.executeAsync(%s, %s)";

    public static void main(String[] args) throws Throwable {

        // Map<String, String> map = new HashedMap<>();
        // Map<String, String> map2 = new HashedMap<>();
        // map.put("ho", "hhoho");
        // map.put("hu", "huhu");
        // map.put("hi", "hihi");
        // map2.put("ha", "haha");
        // StringBuilder builder = new StringBuilder();
        // buildStringParams(null, builder);
        // buildVarParams(null, builder);
        // String formated = String.format(EXECUTE_ASYNC_FORMAT, "Command", builder.toString().trim().isEmpty() ? null : "[" + builder.toString().trim() + "]");
        // System.out.println(formated);
        Map<String, Path> paths = Maps.newHashMap();
        paths.put("lol", Paths.get("hahahah/hohoho"));
        System.out.println(Paths.get("hahahah/hohoho"));

        // System.out.println((new ObjectMapper()).writeValueAsString(Paths.get("hahahah/hohoho")));

    }

}
