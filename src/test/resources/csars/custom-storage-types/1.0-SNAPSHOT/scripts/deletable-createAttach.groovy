import org.cloudifysource.utilitydomain.context.ServiceContextFactory

def context = ServiceContextFactory.getServiceContext()

//accessing and parsing the properties file
def config  = new ConfigSlurper().parse(new File("${context.serviceDirectory}/service.properties").toURL())

//we need the nodeTemplateId to access its properties
def nodeTemplateId =  args[0]

//getting a properties
def nodeTemplateProps = config[nodeTemplateId]
def prop1 = config[nodeTemplateId]["size"]  //or def prop1 = nodeTeplatesProps.prop1

println "The size provided is ${prop1}";

def storageTemplateId = args[3]
def device = "/dev/vdb"
println "deletable-createAttach.groovy: Creating a volume using the storage template <${storageTemplateId}>..."
volumeId = context.storage.createVolume(storageTemplateId)
println "deletable-createAttach.groovy: Creating a volume using the storage template <${storageTemplateId}>..."
println "deletable-createAttach.groovy: attaching storage volume <${volumeId}> to <${device}>... "
context.storage.attachVolume(volumeId, device)

return [volumeId: volumeId, device:device]
