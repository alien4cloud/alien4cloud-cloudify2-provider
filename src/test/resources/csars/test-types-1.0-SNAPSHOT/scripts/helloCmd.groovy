import org.cloudifysource.dsl.utils.ServiceUtils

log.info "helloCmd.groovy: Starting..."
log.info "receive Var yourName = ${yourName} "
log.debug " THIS IS FOR DEBUG PURPOSE"

assert yourName && yourName!= "failThis", 'the name is not correct'

return "hello <${yourName}>, customHostName is <${customHostName}>, from <${context.serviceName}.${context.instanceId}>"