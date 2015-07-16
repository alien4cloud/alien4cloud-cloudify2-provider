import org.cloudifysource.utilitydomain.context.ServiceContextFactory
import java.util.concurrent.TimeUnit
import org.apache.commons.validator.routines.InetAddressValidator;

def context = ServiceContextFactory.getServiceContext()
def ipValidator = InetAddressValidator.getInstance();

assert NAME : "Empty env var NAME"
log.info "NAME : ${NAME}"
assert CUSTOM_HOSTNAME : "Empty env var CUSTOM_HOSTNAME"
log.info "CUSTOM_HOSTNAME : ${CUSTOM_HOSTNAME}"
assert COMPUTE_IP : "Empty env var COMPUTE_IP"
assert ipValidator.isValidInet4Address(COMPUTE_IP)
log.info "COMPUTE_IP : ${COMPUTE_IP}"
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

def targetsArray = TARGETS.split(",")
def nbTarget = targetsArray.length;
log.info "Nb of targets is ${nbTarget}: ${targetsArray}"

targetsArray.each{
  def name = it+"_COMPUTE_IP"
  assert binding.getVariable(name) && ipValidator.isValidInet4Address(binding.getVariable(name)): "Empty env var ${name}"
  log.info "${name} : ${binding.getVariable(name)}"
}