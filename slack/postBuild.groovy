// script arguments
def args = []
// channels
try {
    // try using 'channels'
    args << new String(channels)
} catch (IllegalArgumentException ignored) {
    // if 'channels' is undefined, use this value
    args << new String("@joan.roch")
}
// build result
args << new String(manager.getResult())
// environment variables
manager.getEnvVars().each { k, v ->
    args << new String("${k}:${v}")
}
// build log
args << new String("logs:" + manager.build.getLog(10).join("\n"))
// slack!
new GroovyShell().run(new File(manager.envVars["WORKSPACE"] + "/slack/slack.groovy"), args)
