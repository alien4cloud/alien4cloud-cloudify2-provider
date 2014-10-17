service {
  name "event"

  elastic true
  numInstances 1
  maxAllowedInstances 10

  compute {
    template "LINUX"
  }

  lifecycle {

    startDetectionTimeoutSecs 900
    startDetection {
      println "startDetection"
      true
    }

    init "event-init.groovy"
    preInstall "fireEvent.groovy PRE_INSTALL hello.sh"
    install "fireEvent.groovy INSTALL"
    postInstall "fireEvent.groovy POST_INSTALL"
    preStart "fireEvent.groovy PRE_START"
    start "fireEvent.groovy START"
    postStart "fireEvent.groovy POST_START"
    preStop "fireEvent.groovy PRE_STOP"
    stop "fireEvent.groovy STOP"
    postStop "fireEvent.groovy POST_STOP"
    shutdown "fireEvent.groovy SHUTDOWN"
    preServiceStart "fireEvent.groovy PRE_SERVICE_START"
    preServiceStop "fireEvent.groovy PRE_SERVICE_STOP"

    stopDetection {
      println "stopDetection"
      false
    }
    locator {
      println "locator"
      NO_PROCESS_LOCATORS
    }
  }

  customCommands ([
    "getInstanceId" : "return context.getInstanceId"
  ])
}

