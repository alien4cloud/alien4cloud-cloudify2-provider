import org.cloudifysource.utilitydomain.context.ServiceContextFactory

def context = ServiceContextFactory.getServiceContext()
def builder = new AntBuilder()
log.info "<${SELF}> device is: <${device}>, location is <${location}>"
def ant = new AntBuilder();
ant.delete(dir:location,failonerror:false);
// context.storage.mount(device, location)
builder.sequential {
  chmod(dir:"${context.serviceDirectory}/scripts", perm:"+x", includes:"*.sh")
  mkdir(dir: location)
  echo(message:"mount.groovy: Running ${context.serviceDirectory}/scripts/mountStorage.sh...")
  exec(executable: "${context.serviceDirectory}/scripts/mountStorage.sh",failonerror: "true") {
    arg(value:"${device}")
    arg(value:"${location}")
  }
}

return location