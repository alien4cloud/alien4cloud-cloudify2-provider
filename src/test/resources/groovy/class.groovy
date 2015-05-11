    public class ProcessOutputListener implements Appendable {
    
      private StringWriter outputBufffer = new StringWriter();
      
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
    
      String getLastOutput() {
        def outputString = outputBufffer.toString();
        if (outputString == null || outputString.size() == 0) {
          return null;
        }
        def lineList = outputString.readLines();
        return lineList[lineList.size() -1]
      }
    
    }