import org.cloudifysource.utilitydomain.context.ServiceContextFactory

def context = ServiceContextFactory.getServiceContext()

println "Storage volume: volumeId <${volumeId}>, device <${device}>"
println "deletable-unmountDelete.groovy: unmounting storage volume... "
context.storage.unmount(device)
println "deletable-unmountDelete.groovy: detaching storage volume... "
context.storage.detachVolume(volumeId) 
println "deletable-unmountDelete.groovy: detaching storage volume... "
context.storage.deleteVolume(volumeId) 