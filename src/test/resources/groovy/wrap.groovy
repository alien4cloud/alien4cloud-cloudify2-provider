 import java.util.regex.*
 
    public class ScriptWrapper {
    
    private static def SCRIPT_WRAPPER_PATH = "scriptWrapper.sh"
    private static def SCRIPT_WRAPPER = "/home/developer/checkout/alien4cloud/branches/ALIEN-843-948/alien4cloud-cloudify2-provider/src/test/resources/data/scriptWrapper.sh"
    
      public void wrap(String scriptPath, Map argsMap, List expectedOutputs) {
        def fileName = "toto.sh"
        def fileExtension = fileName.substring(fileName.lastIndexOf('.'))
        if (fileExtension == ".bat" || fileExtension == ".cmd") {
          println "${fileName} seems to be a windows .bat"
        } else {
          println "${fileName} seems to be a *nix .bash"
        }
        
        def expectedOutputsList = "";
        
        def serviceDirectory = "toto/titi/"
        def path = "${SCRIPT_WRAPPER_PATH}"
        println "${path}"
        expectedOutputs.each { expectedOutputsList = expectedOutputsList.length() >  0 ? "${expectedOutputsList};$it" : "$it" }
        argsMap.put("EXPECTED_OUTPUTS", expectedOutputsList)
      
        String[] environment = buildEnvForShOrBat(argsMap);
        def command = "${SCRIPT_WRAPPER} ${scriptPath}"
        println "Executing command ${command}\n environment is: ${environment}"
        def scriptProcess = "${command}".execute(environment, null)
        //scriptProcess.consumeProcessOutput(System.out, System.out)
        def myOutputListener = new ProcessOutputListener()
        
        scriptProcess.consumeProcessOutputStream(myOutputListener)
        scriptProcess.consumeProcessErrorStream(System.out)
        
        scriptProcess.waitFor()
        def scriptExitValue = scriptProcess.exitValue()
        
        def lastOutput = myOutputListener.getLastOutput()
        println "lastOutput is: ${lastOutput}"
        def processResult = myOutputListener.getResult(expectedOutputs)
        println "result is: ${processResult.result}"
        println "result is: ${processResult.outputs}"
        
      }
    
    
    public void wrapGroovy() {
        def outputValue = null
        println "outputValue:${outputValue}"
        def script = "/home/developer/checkout/alien4cloud/branches/ALIEN-843-948/alien4cloud-cloudify2-provider/src/test/resources/groovy/testOutputs.groovy"
        Binding binding = new Binding()
        def shell = new GroovyShell(ScriptWrapper.class.classLoader,binding)
        println shell.evaluate(new File(script))    
        println binding.getVariable("OUTPUT1")
        println binding.hasVariable("OUTPUT2")
        println binding.getVariable("OUTPUT2")
    }
    
    
      private String[] buildEnvForShOrBat(Map argsMap) {
        def merged = [:]
        merged.putAll(System.getenv())
        merged.putAll(argsMap)
        return merged.collect { k, v -> v=="null"? "$k=''": "$k=$v" }
      }
    
    static class ProcessOutputListener implements java.lang.Appendable {
    
      private StringWriter outputBufffer = new StringWriter()
      // assume that the output names contains only word chars
      private Pattern outputDetectionRegex = ~/EXPECTED_OUTPUT_(\w+)=(.*)/
      
      Appendable append(char c) throws IOException {
        System.out.append(">")
        System.out.append(c)
        outputBufffer.append(c);
        return this
      }
      
      Appendable append(CharSequence csq, int start, int end) throws IOException {
        System.out.append(">")
        System.out.append(csq, start, end)
        outputBufffer.append(csq, start, end)
        return this
      }
      
      Appendable append(CharSequence csq) throws IOException {
        System.out.append(">")
        System.out.append(csq)
        outputBufffer.append(csq)
        return this
      }
    
      String getLastOutput() {
        outputBufffer.flush()
        def outputString = outputBufffer.toString();
        if (outputString == null || outputString.size() == 0) {
          return null;
        }
        def lineList = outputString.readLines();
        return lineList[lineList.size() -1]
      }
      
      ProcessOutputResult getResult(List expectedOutputs) {
        outputBufffer.flush()
        def outputString = outputBufffer.toString();
        if (outputString == null || outputString.size() == 0) {
        System.out.append("size:" + outputString.size())
          println "outputString: ${outputString}"
          return null;
        }
        def outputs = [:]
        def lineList = outputString.readLines()
        def lineIterator = lineList.iterator()
        def i = 0;
        while(lineIterator.hasNext()) {
          def line = lineIterator.next();
          def ouputMatcher = outputDetectionRegex.matcher(line)
          if (ouputMatcher.matches()) {
            def detectedOuputName = ouputMatcher.group(1)
            if (expectedOutputs.contains(detectedOuputName)) {
              // add the output value in the map 
              outputs.put(detectedOuputName, ouputMatcher.group(2));
              // remove the iterator 
              lineIterator.remove();
            }
          } 
          i++;         
        }
        // the outputs have been removed, so the last line is now the result of the exec
        def result = lineList.size() > 0 ? lineList[lineList.size() -1] : null
        def oo = [:]
        outputs.each { k, v -> oo.put("titi:$k", v) }
        println "$oo"
        return new ProcessOutputResult(result: result, outputs: outputs)     
      }
    
    }
    
    static class ProcessOutputResult {
      String result
      Map outputs
    }
   
}