import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import groovy.lang.Binding
import groovy.transform.Synchronized
import org.cloudifysource.dsl.context.ServiceInstance

import org.cloudifysource.utilitydomain.context.ServiceContextFactory

import java.util.logging.Logger

public class EnvironmentBuilder {

    private static def NAME_VALUE_KEYWORD = "NAME_VALUE_TO_PARSE"
    private static def NAME_VALUE_SEPARATOR = "="
    private static def MAP_TO_ADD_KEYWORD = "MAP_TO_ADD_"
    
    public String[] buildShOrBatchEnvironment(Map argsMap) {
        if(argsMap != null && !argsMap.isEmpty()) {
            //getting a formated env for sh or batch scripts
            return buildEnvForShOrBat(argsMap);
        }else {
            return null
        }
    }
    
    public Binding buildGroovyEnvironment(context, Map argsMap) {
        Binding binding = new Binding()
        //set the context variable
        binding.setVariable("context", context)
        binding.setVariable("logger", Logger.getLogger("${context.serviceName}-stdout"))
        
        if(argsMap != null && !argsMap.isEmpty()) {
            buildEnvForGroovy(argsMap, binding)
        }
        
        return binding;
    }
    
    /**
     * get TOSCA relationships env vars Map, including attributes from get_attribute functions
     * 
     * @param context
     * @param name
     *  SOURCE or TARGET
     * @param baseValue
     * @param serviceName
     * @param inputsAttributes
     * @return
     */
    public static Map getTOSCARelationshipEnvs(context, String name, String baseValue, String serviceName, String instanceId, Map inputsAttributes, List eligibleNodesNames) {
        def envMap = [:]
        def pairContext = [
            envVar:name,
            baseValue:baseValue,
            serviceName:serviceName
        ]
        
        def inputsAttrContext = [
            inputsAttributes:inputsAttributes?:[:],
            eligibleNodesNames:eligibleNodesNames
        ]
        buildTOSCARelationshipEnvVar(context, pairContext, inputsAttrContext, instanceId, envMap)
        return envMap
    }
    
    /**
     * build env vars given a map for sh scripts.
     * @param argsMap
     * @return
     */
    private String[] buildEnvForShOrBat(Map argsMap) {
        def merged = [:]
        merged.putAll(System.getenv())
        
        //parse NAME_VALUE_TO_PARSE env
        parseNameValueIfExist(argsMap)
        //parse MAP_TO_ADD_ env
        parseMapToAddToEnv(argsMap)
        
        merged.putAll(argsMap)
        return merged.collect { k, v -> v=="null"? "$k=''": "$k=$v" }
    }
    
    /**
     * build env vars given a map for groovy scripts.
     * 
     * @param argsMap
     * @param binding
     * @return
     */
    private def buildEnvForGroovy(Map argsMap, Binding binding) {
        //parse NAME_VALUE_TO_PARSE env
        parseNameValueIfExist(argsMap)
        //parse MAP_TO_ADD_ env
        parseMapToAddToEnv(argsMap)
        
        argsMap.each {  k, v ->
            if(v=="null") {
                binding.setVariable(k, null);
            }else {
                binding.setVariable(k, v);
            }
        }
    }
    
    private def parseNameValueIfExist(Map argsMap) {
        def toParse = argsMap.remove(NAME_VALUE_KEYWORD);
        if(toParse) {
            def parsed = [:];
            toParse.each {
                def index = it.indexOf(NAME_VALUE_SEPARATOR)
                if(index > 0) {
                    def key = it.substring(0, index)
                    parsed.put(key, it.substring(key.size()+1))
                }
            }
            argsMap.putAll(parsed)
        }
    }
    
    /**
     * Parse  MAP_TO_ADD_ attributes for a source or a target
     * 
     * @param argsMap
     * @return
     */
    private def parseMapToAddToEnv(Map argsMap) {
        Map attributesToProcess = [:]
        def keySet = argsMap.keySet() as List
        keySet.each { 
            if(it.startsWith(MAP_TO_ADD_KEYWORD)) {
                Map resultToMerge = argsMap.remove(it)
                argsMap.putAll(resultToMerge) 
            }
        }
    }
    
    /**
     * add TOSCA env var SOURCE, TARGET, SOURCES and  TARGETS
     * 
     * if there was get_attribute functions, then add all instances attributes
     * 
     * @param context
     * @param envVar
     *  either SOURCE or TARGET
     * @param baseValue
     * @param serviceName
     * @param attrToProcess
     * @param argsMap
     * @return
     */
    private static def buildTOSCARelationshipEnvVar(context, Map pairContext, Map inputsAttrContext, String instanceId, Map envMap) {
        instanceId = instanceId?:context.instanceId;
        def nbInstances = context.waitForService(pairContext.serviceName, 60, TimeUnit.SECONDS).getNumberOfPlannedInstances();
        StringBuilder values = new StringBuilder();
        
        //put the env: SOURCE or TARGET
        envMap.put(pairContext.envVar, pairContext.baseValue+"_"+instanceId)
        
        //SOURCES or TARGETS
        def pluralEnvVar = pairContext.envVar+"S"
        for (int i = 1; i <= nbInstances; i++) {
            String valueToAdd = pairContext.baseValue+"_"+i;
            if(values.length() > 1) {
                values.append(",");
            }
            values.append(valueToAdd);
            
            //process the attr for this nodeId
            //k -> paramName, v -> attributeName
            inputsAttrContext.inputsAttributes.each {k, v ->
                envMap.put(valueToAdd+"_"+k, CloudifyAttributesUtils.getTheProperAttribute(context, pairContext.serviceName, i, v, inputsAttrContext.eligibleNodesNames))
            }
        }
        envMap.put(pluralEnvVar, values.toString())
    }
    
}