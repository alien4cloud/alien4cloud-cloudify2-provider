
context.storage.unmount(context.attributes.thisInstance.storageDevice)
context.storage.detachVolume(context.attributes.thisInstance.volumeId) 
context.storage.deleteVolume(context.attributes.thisInstance.volumeId);

context.attributes.thisInstance.volumeId = null
context.attributes.thisInstance.storageDevice = null

return true