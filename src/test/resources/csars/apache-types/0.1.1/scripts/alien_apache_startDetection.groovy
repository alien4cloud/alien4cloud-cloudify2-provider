import org.cloudifysource.dsl.utils.ServiceUtils

def sleepTimeInMiliSeconds = 3300
Thread.sleep(sleepTimeInMiliSeconds)
def config = new ConfigSlurper().parse(new File("${context.serviceDirectory}/scripts/apache-service.properties").toURL())
def currHttpPort = config.port
def passed = ServiceUtils.isPortOccupied(currHttpPort)
println "apache_startDetection.groovy: isPortOccupied http=${currHttpPort} ...${passed}"
return passed
