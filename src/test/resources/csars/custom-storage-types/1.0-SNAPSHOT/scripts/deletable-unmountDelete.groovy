import org.cloudifysource.utilitydomain.context.ServiceContextFactory

def context = ServiceContextFactory.getServiceContext()

def volumeId = args[2]
def device = args[3]

println "Storage volume: volumeId <${volumeId}>, device <${device}>"
println "deletable-unmountDelete.groovy: unmounting storage volume... "
context.storage.unmount(device)
println "deletable-unmountDelete.groovy: detaching storage volume... "
context.storage.detachVolume(volumeId) 
println "deletable-unmountDelete.groovy: detaching storage volume... "
context.storage.deleteVolume(volumeId) 