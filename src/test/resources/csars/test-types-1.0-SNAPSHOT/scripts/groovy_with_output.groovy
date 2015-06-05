def localhostname = "dontKnow";
println "Setting old_hostnane output...<${localhostname}>"
setProperty("old_hostnane", localhostname)
def randomOutPut = 1234567890
println "Setting output_from_create output...<${randomOutPut}>"
output_from_create = randomOutPut

def testForConcat = "thisIsATestForConcat"
logger.log("Setting output test_for_concat output...<${testForConcat}> ")
test_for_concat = testForConcat

return "done"

