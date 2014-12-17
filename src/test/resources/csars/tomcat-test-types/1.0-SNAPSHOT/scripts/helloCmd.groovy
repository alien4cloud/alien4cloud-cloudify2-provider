import org.cloudifysource.dsl.utils.ServiceUtils

println "helloCmd.groovy: Starting..."

def name = args[0]
def os_version = args[1]

if(!name || name == "failThis"){
  throw new IllegalStateException(" the name is not correct")
}

return "hello <${name}>, os_version is <${os_version}>, from <${context.serviceName}.${context.instanceId}>"