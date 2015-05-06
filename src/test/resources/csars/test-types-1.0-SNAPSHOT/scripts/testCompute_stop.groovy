import org.apache.commons.validator.routines.InetAddressValidator;
def ipValidator = InetAddressValidator.getInstance();
assert MY_IP && ipValidator.isValidInet4Address(MY_IP): "Empty or not valid env var MY_IP"
println "MY_IP : ${MY_IP}"