import org.cloudifysource.dsl.utils.ServiceUtils

def sleepTimeInMiliSeconds = 3300
Thread.sleep(sleepTimeInMiliSeconds)
def config = new ConfigSlurper().parse(new File("${context.serviceDirectory}/scripts/apache-service.properties").toURL())
def currHttpPort = config.port
def passed = ServiceUtils.isPortFree(currHttpPort)
println "apache_stopDetection.groovy: isPortFree http=${currHttpPort} ...${passed}"
return passed
