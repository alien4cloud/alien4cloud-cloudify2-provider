import org.cloudifysource.utilitydomain.context.ServiceContextFactory

def context = ServiceContextFactory.getServiceContext()

def storageTemplateId = args[1]
def device = "/dev/vdb"
println "deletable-createAttach.groovy: Creating a volume using the storage template <${storageTemplateId}>..."
volumeId = context.storage.createVolume(storageTemplateId)
println "deletable-createAttach.groovy: Creating a volume using the storage template <${storageTemplateId}>..."
println "deletable-createAttach.groovy: attaching storage volume <${volumeId}> to <${device}>... "
context.storage.attachVolume(volumeId, device)

return [volumeId: volumeId, device:device]
