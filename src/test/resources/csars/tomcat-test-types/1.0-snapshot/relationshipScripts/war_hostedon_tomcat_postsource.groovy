import org.cloudifysource.utilitydomain.context.ServiceContextFactory
import java.util.concurrent.TimeUnit

def context = ServiceContextFactory.getServiceContext()

def getArtifactFrom(artifactName, String... artifacts) {
  for( it in artifacts){
    def split = it.split("=")
    if(split.length > 1 && split[0].equals(artifactName)){
       return split[1]   
    }
  }
}

println "war hosted on tomcat post_configure_source start"

//getting the war file path (warRelativePath)
def serviceName = context.serviceName
def instanceId = context.instanceId
def nodeId =  args[0]
def warRelativePath
def warUrl
def artifacts
def warArtifactName = "war_file"
if(args.length > 5){
  artifacts = Arrays.copyOfRange(args, 4, args.length);
}
if(!artifacts) return "missing arguments.. we do nothing"
warRelativePath = getArtifactFrom(warArtifactName, artifacts)
if (! warRelativePath) return "warUrl is null. So we do nothing."
warUrl = "${context.serviceDirectory}/../${warRelativePath}"
def config  = new ConfigSlurper().parse(new File("${context.serviceDirectory}/service.properties").toURL())
def contextPath = (!config[nodeId] || !config[nodeId].contextPath ) ? new File(warRelativePath).name.split("\\.")[0] : config[nodeId].contextPath 
context.attributes.thisInstance[nodeId] = [url:(warUrl), contextPath:(contextPath)]
println "tomcat-service.groovy(updateWar custom command): warUrl is ${context.attributes.thisInstance[nodeId].url} and contextPath is ${context.attributes.thisInstance[nodeId].contextPath}..."

println "war hosted on tomcat post_configure_source invoking updateNodeWar custom command..."
def service = context.waitForService(serviceName, 60, TimeUnit.SECONDS)
def currentInstance = service.getInstances().find{ it.instanceId == context.instanceId }
currentInstance.invoke("updateNodeWar", nodeId)
println "war hosted on tomcat pre_configure_target end"

return true