import org.cloudifysource.utilitydomain.context.ServiceContextFactory

def context = ServiceContextFactory.getServiceContext()
def device = args[0]
def path = "/mountTest"
def builder = new AntBuilder()
builder.sequential {
  chmod(dir:"${context.serviceDirectory}/scripts", perm:"+x", includes:"*.sh")
  echo(message:"deletable-formatMount.groovy: Running ${context.serviceDirectory}/scripts/formatStorage.sh...")
  exec(executable: "${context.serviceDirectory}/scripts/formatStorage.sh",failonerror: "true") {
    arg(value:"${device}")			
  }
  echo(message:"deletable-formatMount.groovy: Running ${context.serviceDirectory}/scripts/mountStorage.sh...")
  mkdir(dir: path)
  exec(executable: "${context.serviceDirectory}/scripts/mountStorage.sh",failonerror: "true") {
    arg(value:"${device}")			
    arg(value:"${path}")			
  }
}

return path