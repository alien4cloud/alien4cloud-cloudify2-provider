import org.cloudifysource.dsl.utils.ServiceUtils

println "helloCmd.groovy: Starting..."

def name = args[0]

if(!name || name == "failThis"){
  throw new IllegalStateException(" the name is not correct")
}

return "hello <${name}>, from <${context.serviceName}.${context.instanceId}>"