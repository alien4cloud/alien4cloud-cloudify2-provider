import java.util.regex.Pattern

public class ProcessOutputListener implements Appendable {

  private StringWriter outputBufffer = new StringWriter();
  
  // assume that the output names contains only word chars
  private Pattern outputDetectionRegex = ~/EXPECTED_OUTPUT_(\w+)=(.*)/
  
  Appendable append(char c) throws IOException {
    System.out.append(c)
    outputBufffer.append(c);
    return this
  }
  
  Appendable append(CharSequence csq, int start, int end) throws IOException {
    System.out.append(csq, start, end)
    outputBufffer.append(csq, start, end)
    return this
  }
  
  Appendable append(CharSequence csq) throws IOException {
    System.out.append(csq)
    outputBufffer.append(csq)
    return this
  }

  ProcessOutputResult getResult(Collection expectedOutputs) {
    outputBufffer.flush()
    def outputString = outputBufffer.toString();
    if (outputString == null || outputString.size() == 0) {
    System.out.append("size:" + outputString.size())
      return null;
    }
    def lineList = outputString.readLines()
    def outputs = [:]
    if(expectedOutputs && !expectedOutputs.isEmpty()) {
        def lineIterator = lineList.iterator()
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
        }
    }
    // the outputs have been removed, so the last line is now the result of the exec
    def result = lineList.size() > 0 ? lineList[lineList.size() -1] : null
    return new ProcessOutputResult(result: result, outputs: outputs)
  }

}

// data structure for script result
public class ProcessOutputResult {
  // the reult = the last line of the output
  String result
  // the expected output values
  Map outputs
}