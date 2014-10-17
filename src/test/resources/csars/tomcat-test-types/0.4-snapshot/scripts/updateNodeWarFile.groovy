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

println "updateNodeWarFile.groovy: Starting..."

def warNodeId = args[0]
if(!warNodeId) return false;

def config  = new ConfigSlurper().parse(new File("${context.serviceDirectory}/scripts/tomcat-service.properties").toURL())
def instanceId = context.instanceId
def warNodeContext = context.attributes.thisInstance[warNodeId]
if(!warNodeContext || warNodeContext == null) return false;

println "updateNodeWarFile.groovy: warUrl is ${warNodeContext.url}"

if (! warNodeContext.url) return "warUrl is null. So we do nothing."

def catalinaBase = context.attributes.thisInstance["catalinaBase"]
def installDir = context.attributes.thisInstance["installDir"]
def applicationWar = "${installDir}/${new File(warNodeContext.url).name}"

//get the WAR file
new AntBuilder().sequential {
	if ( warNodeContext.url.toLowerCase().startsWith("http") || warNodeContext.url.toLowerCase().startsWith("ftp")) {
		echo(message:"Getting ${warNodeContext.url} to ${applicationWar} ...")
		ServiceUtils.getDownloadUtil().get("${warNodeContext.url}", "${applicationWar}", false)
	}
	else {
		echo(message:"Copying ${warNodeContext.url} to ${applicationWar} ...")
		copy(tofile: "${applicationWar}", file:"${warNodeContext.url}", overwrite:true)
	}
}

//configure its tomcat context
File ctxConf = new File("${catalinaBase}/conf/Catalina/localhost/${warNodeContext.contextPath}.xml")
if (ctxConf.exists()) {
	assert ctxConf.delete()
} else {
	new File(ctxConf.getParent()).mkdirs()
}
assert ctxConf.createNewFile()
ctxConf.append("<Context docBase=\"${applicationWar}\" />")

println "updateNodeWarFile.groovy: End"
return true