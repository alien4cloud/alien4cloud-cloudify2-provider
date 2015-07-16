import org.apache.commons.validator.routines.InetAddressValidator;
def ipValidator = InetAddressValidator.getInstance();

assert SOURCE_IP && ipValidator.isValidInet4Address(SOURCE_IP): errorEnVar("SOURCE_IP")
printEnv("SOURCE_IP",SOURCE_IP)
assert TARGET_IP && ipValidator.isValidInet4Address(TARGET_IP): errorEnVar("TARGET_IP")
printEnv("TARGET_IP",TARGET_IP)

assert SOURCE_CREATE_OUTPUT && SOURCE_CREATE_OUTPUT != "" : errorEnVar("SOURCE_CREATE_OUTPUT")
printEnv("SOURCE_CREATE_OUTPUT", SOURCE_CREATE_OUTPUT) 

assert SOURCE_CONFIGURE_OUTPUT && SOURCE_CONFIGURE_OUTPUT !="" : errorEnVar("SOURCE_CONFIGURE_OUTPUT")
printEnv("SOURCE_CONFIGURE_OUTPUT",SOURCE_CONFIGURE_OUTPUT)

printEnv("TARGET_CREATE_OUTPUT", TARGET_CREATE_OUTPUT)
printEnv("TARGET_CONFIGURE_OUTPUT", TARGET_CONFIGURE_OUTPUT)

assert !RANDOM_ATTR
printEnv("RANDOM_ATTR",RANDOM_ATTR)

assert TARGET_OLD_HOSTNAME_ATTR && TARGET_OLD_HOSTNAME_ATTR != "" : errorEnVar("TARGET_OLD_HOSTNAME_ATTR")
printEnv("TARGET_OLD_HOSTNAME_ATTR", TARGET_OLD_HOSTNAME_ATTR)

assert TARGET_OLD_HOSTNAME_ATTR == TARGET_CREATE_OUTPUT : "TARGET_OLD_HOSTNAME_ATTR and TARGET_CREATE_OUTPUT should be the same"

setProperty("post_conf_output", "FromPostConf")

private def errorEnVar(def envVar){
  return "Empty or not valid env var ${envVar}"
}

private def printEnv(def name, def value) {
  log.info "${name} : ${value}"
}