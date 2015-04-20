println "tomcat_bolo.groovy: ============================================== B O L O"
def scriptProcess = "src/test/resources/csars/test-types-1.0-SNAPSHOT/scripts/customCommandBashWithOutput.sh".execute(null, null)
//scriptProcess.consumeProcessOutput(System.out, System.out)
def myHello = { println "Hello guys"; } as Appendable
def lastEcho
def myAppendable = [
  append: { String csq ->
    print csq
  }
] as Appendable
scriptProcess.consumeProcessOutputStream(myAppendable)

scriptProcess.waitFor()
def scriptExitValue = scriptProcess.exitValue()
println "Output: ${scriptExitValue}"    
def proceddOutput = scriptProcess.text
println "getText: ${proceddOutput}"       