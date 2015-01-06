import org.cloudifysource.dsl.utils.ServiceUtils

println "helloCmd.groovy: Starting..."

assert yourName && yourName!= "failThis", 'the name is not correct'

return "hello <${yourName}>, os_version is <${os_version}>, from <${context.serviceName}.${context.instanceId}>"