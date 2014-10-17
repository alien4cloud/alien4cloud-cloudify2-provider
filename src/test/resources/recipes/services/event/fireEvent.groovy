import org.openspaces.core.cluster.ClusterInfo
import org.cloudifysource.utilitydomain.context.ServiceContextFactory

context = ServiceContextFactory.getServiceContext()

// Fire event
def env = System.getenv()
def locators = env['LUS_IP_ADDRESS'] == null ? "localhost:4174" : env['LUS_IP_ADDRESS']
def managementIp = locators.split(":")[0]
def restEventsPort = 8081
def application = context.getApplicationName()
def service = context.getServiceName()
def instanceId = context.getInstanceId()
def event = args[0]

def restBaseUrl = "http://${managementIp}:${restEventsPort}/events"
def curlCommand = """
curl -w %{http_code} -s --output /dev/null ${restBaseUrl}/putEvent?application=${application}&service=${service}&instanceId=${instanceId}&event=${event}
"""

def curlProcess = "$curlCommand".execute()
def curlErr = new StringBuffer()
def curlOut = new StringBuffer()
curlProcess.consumeProcessOutput(curlOut, curlErr)

curlExitValue = curlProcess.waitFor()

if(curlErr) {
  print """
  ---------- curl:stderr ----------
  Return Code : $curlExitValue
  $curlErr
  ---------------------------------
  """
}
if(curlOut) {
  print """
  ---------- curl:stdout ----------
  Http Code: $curlOut
  ---------------------------------
  """
}


curlExitValue = curlProcess.exitValue()
if(curlExitValue) {
  throw new RuntimeException("Couldn't update fire ${event} event (return code: $curlExitValue)")
}

// Execute bash script
def serviceDirectory = context.getServiceDirectory()
def bashScript = args.length > 1 ? args[1] : null

if(bashScript!=null) {
  def scriptProcess = "${serviceDirectory}/$bashScript".execute()
  def scriptErr = new StringBuffer()
  def scriptOut = new StringBuffer()
  scriptProcess.consumeProcessOutput(scriptOut, scriptErr)

  scriptExitValue = scriptProcess.waitFor()

  if(scriptErr) {
    print """
    ---------- bash:stderr ----------
    Return Code : $scriptExitValue
    $scriptErr
    ---------------------------------
    """
  }
  if(scriptOut) {
    print """
    ---------- bash:stdout ----------
    $scriptOut
    ---------------------------------
    """
  }

  scriptExitValue = scriptProcess.exitValue()
  if(scriptExitValue) {
    throw new RuntimeException("Error executing the script ${bashScript} (return code: $scriptExitValue)")
  }
}