import org.cloudifysource.utilitydomain.context.ServiceContextFactory

def context = ServiceContextFactory.getServiceContext()
def device = "/dev/vdb"
println "attaching storage volume <${volumeId}> to <${device}>... "
context.storage.attachVolume(volumeId, device)

return [volumeId: volumeId, device: device]
