import org.cloudifysource.utilitydomain.context.ServiceContextFactory

context = ServiceContextFactory.getServiceContext()

evaluate(new File("${context.serviceDirectory}/scripts/tomcat_stop.groovy"))
evaluate(new File("${context.serviceDirectory}/scripts/tomcat_postStop.groovy"))