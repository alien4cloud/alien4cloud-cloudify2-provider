
assert SOURCE_IP && SOURCE_IP != "true": "Empty or not valid env var SOURCE_IP"
println "SOURCE_IP : ${SOURCE_IP}"
assert TARGET_IP && TARGET_IP != "true": "Empty or not valid TARGET_IP"
println "TARGET_IP : ${TARGET_IP}"