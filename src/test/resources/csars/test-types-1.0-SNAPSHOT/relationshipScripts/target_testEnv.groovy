import java.util.concurrent.TimeUnit
import org.apache.commons.validator.routines.InetAddressValidator;

def ipValidator = InetAddressValidator.getInstance();

assert MY_HOSTNAME : "Empty env var MY_HOSTNAME"
log.info "MY_HOSTNAME : ${MY_HOSTNAME}"
assert SOURCE_HOSTNAME : "Empty env var SOURCE_HOSTNAME"
log.info "SOURCE_HOSTNAME : ${SOURCE_HOSTNAME}"
assert MY_IP && ipValidator.isValidInet4Address(MY_IP): "Empty or not valid env var MY_IP"
log.info "MY_IP : ${MY_IP}"
assert SOURCE_IP && ipValidator.isValidInet4Address(SOURCE_IP): "Empty or not valid env var SOURCE_IP"
log.info "SOURCE_IP : ${SOURCE_IP}"
assert SOURCE : "Empty env var SOURCE"
log.info "SOURCE : ${SOURCE}"
assert SOURCE_NAME : "Empty env var SOURCE_NAME"
log.info "SOURCE_NAME : ${SOURCE_NAME}"
assert SOURCE_SERVICE_NAME : "Empty env var SOURCE_SERVICE_NAME"
log.info "SOURCE_SERVICE_NAME : ${SOURCE_SERVICE_NAME}"
assert SOURCES : "Empty env var SOURCES"
log.info "SOURCES : ${SOURCES}"
assert TARGET : "Empty env var TARGET"
log.info "TARGET : ${TARGET}"
assert TARGET_NAME : "Empty env var TARGET_NAME"
log.info "TARGET_NAME : ${TARGET_NAME}"
assert TARGET_SERVICE_NAME : "Empty env var TARGET_SERVICE_NAME"
log.info "TARGET_SERVICE_NAME : ${TARGET_SERVICE_NAME}"
assert TARGETS : "Empty env var TARGETS"
log.info "TARGETS : ${TARGETS}"

def sourcesArray = SOURCES.split(",")
def nbSource = sourcesArray.length;
log.info "Nb of sources is ${nbSource}: ${sourcesArray}"

def targetsArray = TARGETS.split(",")
def nbTarget = targetsArray.length;
log.info "Nb of targets is ${nbTarget}: ${targetsArray}"

targetsArray.each{
  def name = it+"_MY_IP"
  assert binding.getVariable(name) : "Empty env var ${name}"
  log.info "${name} : ${binding.getVariable(name)}"
}

return TARGET+"...."+SOURCE;
