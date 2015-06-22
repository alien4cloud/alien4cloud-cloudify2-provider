import org.apache.commons.validator.routines.InetAddressValidator;
def ipValidator = InetAddressValidator.getInstance();

assert SOURCE_IP && ipValidator.isValidInet4Address(SOURCE_IP): "Empty or not valid env var SOURCE_IP"
log.info "SOURCE_IP : ${SOURCE_IP}"
assert TARGET_IP && ipValidator.isValidInet4Address(TARGET_IP): "Empty or not valid TARGET_IP"
log.info "TARGET_IP : ${TARGET_IP}"