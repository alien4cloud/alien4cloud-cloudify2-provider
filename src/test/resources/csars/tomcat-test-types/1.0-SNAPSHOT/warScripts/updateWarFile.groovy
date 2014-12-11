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
import org.cloudifysource.dsl.utils.ServiceUtils
import java.util.concurrent.TimeUnit

if(params == null || params.length < 2){
  throw new IllegalArgumentException("UpdateWarFile command requires a nodeId and an url as arguments.")
}
def nodeId = params[0]
def warUrl=params[1]
def config  = new ConfigSlurper().parse(new File("${context.serviceDirectory}/service.properties").toURL())

def serviceName = context.serviceName
def instanceId = context.instanceId

(if !contextPath ){
  def contextPath = new File(warUrl).name.split("\\.")[0]
}

context.attributes.thisInstance[nodeId] = [url:(warUrl), contextPath:(contextPath)]
println "tomcat-service.groovy(updateWar custom command): warUrl is ${context.attributes.thisInstance[nodeId].url} and contextPath is ${context.attributes.thisInstance[nodeId].contextPath}..."

println "tomcat-service.groovy(updateWar customCommand): invoking updateNodeWar custom command ..."
def service = context.waitForService(context.serviceName, 60, TimeUnit.SECONDS)
def currentInstance = service.getInstances().find{ it.instanceId == context.instanceId }
currentInstance.invoke("updateNodeWar", nodeId)

println "tomcat-service.groovy(updateWar customCommand): End"
return true