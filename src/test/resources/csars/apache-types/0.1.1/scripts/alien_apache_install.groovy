import org.cloudifysource.utilitydomain.context.ServiceContextFactory

context = ServiceContextFactory.getServiceContext()

evaluate(new File("${context.serviceDirectory}/scripts/chmod-init.groovy"))
evaluate(new File("${context.serviceDirectory}/scripts/apache_install.groovy"))
evaluate(new File("${context.serviceDirectory}/scripts/apache_postInstall.groovy"))