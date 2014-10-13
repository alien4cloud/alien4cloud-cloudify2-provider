import org.cloudifysource.utilitydomain.context.ServiceContextFactory
import java.util.concurrent.TimeUnit

def context = ServiceContextFactory.getServiceContext()

def instanceId = context.instanceId
def portIncrement = context.isLocalCloud() ? instanceId-1 : 0
def defaultPort = 8080
def currHttpPort = defaultPort + portIncrement

// FIXME make the serviceName dynamic
def serviceName = "computeapachelb"

println "invokeAddNode.groovy: target $serviceName Post-start ..."
def apacheService = context.waitForService(serviceName, 10, TimeUnit.MINUTES)

if(apacheService != null) {
  println "invokeAddNode.groovy: invoking add-node of apacheLB ..."

  def ipAddress = context.privateAddress
  if (ipAddress == null || ipAddress.trim() == "") ipAddress = context.publicAddress

  println "invokeAddNode.groovy: ipAddress is ${ipAddress} ..."

  def contextPath = context.attributes.thisInstance["contextPath"]
  if (contextPath == 'ROOT') contextPath="" // ROOT means "" by convention in Tomcat
  def currURL="http://${ipAddress}:${currHttpPort}/${contextPath}"
  println "invokeAddNode.groovy: About to add ${currURL} to apacheLB ..."
  apacheService.invoke("addNode", currURL as String, instanceId as String)
  println "invokeAddNode.groovy: target Post-start ended"
} else {
  println "No service '$serviceName' found. Couldn't add node to the load balancer"
}