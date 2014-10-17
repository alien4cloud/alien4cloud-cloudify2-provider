/*******************************************************************************
* Copyright (c) 2013 GigaSpaces Technologies Ltd. All rights reserved
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/
import org.cloudifysource.utilitydomain.context.ServiceContextFactory
import org.cloudifysource.dsl.utils.ServiceUtils

def context = ServiceContextFactory.getServiceContext()
def config  = new ConfigSlurper().parse(new File("${context.serviceDirectory}/scripts/tomcat-service.properties").toURL())
def useLoadBalancer = config.useLoadBalancer

def instanceId = context.instanceId
def portIncrement = context.isLocalCloud() ? instanceId-1 : 0
def port = config.port
def currHttpPort = port + portIncrement


def serviceLBName = "ComputeApacheLB"

if ( useLoadBalancer ) {
  println "tomcat-service.groovy: tomcat Post-stop ..."
  try {
    def apacheService = context.waitForService(serviceLBName, 180, TimeUnit.SECONDS)

    if ( apacheService != null ) {
      def ipAddress = context.privateAddress
      if (ipAddress == null || ipAddress.trim() == "") ipAddress = context.publicAddress
      println "tomcat-service.groovy: ipAddress is ${ipAddress} ..."
      def contextPath = context.attributes.thisInstance["contextPath"]
      if (contextPath == 'ROOT') contextPath="" // ROOT means "" by convention in Tomcat
      def currURL="http://${ipAddress}:${currHttpPort}/${contextPath}"
      println "tomcat-service.groovy: About to remove ${currURL} from ${serviceLBName} ..."
      apacheService.invoke("removeNode", currURL as String, instanceId as String)
    }
    else {
      println "tomcat-service.groovy: waitForService ${serviceLBName} returned null"
    }
  }
  catch (all) {
    println "tomcat-service.groovy: Exception in Post-stop: " + all
  }
  println "tomcat-service.groovy: tomcat Post-stop ended"
}