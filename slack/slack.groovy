/**
 * some common values
 */
JIRA_URL = "https://appdirect.jira.com/browse"
SLACK_URL = "https://hooks.slack.com/services/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"
NA = "N/A"
MUTE = "adaeaf"
INFO = "64a62e"
WARN = "eeae3f"
FAIL = "d00000"
NOW = (new Date()).toTimestamp().getTime()/1000

/**
 * send a notification, using slack
 */
def sendNotification(String action) {
    // some preprocessing
    def channels = getValue("channels").tokenize(', ')
    def adVersion = getValue("appdirectVersion").allWhitespace ? NA : getValue("appdirectVersion")
    def jbVersion = getValue("billingVersion").allWhitespace ? NA : getValue("billingVersion")
    def versions = []
    if (adVersion != NA) versions << "AppDirect " + adVersion
    if (jbVersion != NA) versions << "JBilling " + jbVersion
    def reason = getValue("reason")
    def issue = getValue("issue").toUpperCase()
    def customers = getValue("customers").tokenize(",")
    def steps = getValue("steps").tokenize(",")
    def logs = getValue("logs")

    // now notify (but only under certain conditions, as we do not want to send uninformative messages)
    if (customers.size() > 0 && steps.contains("Prod") && (adVersion != NA || jbVersion != NA)) {
        switch (action.toLowerCase()) {
            case "start":
                // notifies that a deployment has just been launched
                sendDeployNotification(channels, MUTE, adVersion, jbVersion, reason, issue, customers, steps, "", "rocket",
                        "Deployment of ${formatList(versions, false)} " +
                                "to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} launched.",
                        "The following marketplace${customers.size > 1 ? "s" : ""} " +
                                "will be upgraded: ${formatList(customers, true)}.")
                break
            case "success":
                // notifies that a deployment has just been completed successfully
                sendDeployNotification(channels, INFO, adVersion, jbVersion, reason, issue, customers, steps, "", "checkered_flag",
                        "Deployment of ${formatList(versions, false)} " +
                                "to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} completed.",
                        "The following marketplace${customers.size > 1 ? "s" : ""} " +
                                "ha${customers.size > 1 ? "ve" : "s"} been upgraded: ${formatList(customers, true)}.")
                break
            case "failure":
                // notifies that a deployment has just failed
                sendDeployNotification(channels, FAIL, adVersion, jbVersion, reason, issue, customers, steps, logs, "bomb",
                        "Deployment of ${formatList(versions, false)} " +
                            "to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} failed.",
                        "The following marketplace${customers.size > 1 ? "s" : ""} " +
                                "may not have been upgraded: ${formatList(customers, true)}.")
                break
            default:
                // notifies that a deployment has just ended... but in a weird fashion
                sendDeployNotification(channels, WARN, adVersion, jbVersion, reason, issue, customers, steps, logs, "warning",
                        "Deployment of ${formatList(versions, false)} " +
                                "to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} ended.",
                        "The following marketplace${customers.size > 1 ? "s" : ""} " +
                                "may not have been upgraded: ${formatList(customers, true)}.")
        }
    }
}

/**
 * formats an issue into a link
 */
String formatIssue(String issue) {
    return issue.allWhitespace ? "N/A" : "<${JIRA_URL}/${issue}|${issue}>"
}

/**
 * formats a list of items
 */
String formatList(ArrayList items, boolean bold) {
    def text = ""
    for (int i = 0; i < items.size; i++) {
        text += (bold ? "*" : "") + items[i] + (bold ? "*" : "")
        if (i < items.size - 2) text += ", "
        if (i == items.size - 2) text += " & "
    }
    return text
}

/**
 * posts a deployment notification to the specified slack channels
 */
def sendDeployNotification(ArrayList channels, String color, String adVersion, String jbVersion,
        String reason, String issue, ArrayList customers, ArrayList steps, String logs, String emoji,
        String fallback, String desc) {
    def attachment = """
      {
          "mrkdwn_in": ["pretext", "text", "fields"],
          "color": "#${color}",
          "fallback": "${fallback}",
          "title": "${reason}",
          "title_link": "${getValue("BUILD_URL")}",
          "fields": [
              {
                  "title": "AppDirect",
                  "value": "${adVersion}",
                  "short": true
              },
              {
                  "title": "JBilling",
                  "value": "${jbVersion}",
                  "short": true
              },
              {
                  "title": "JIRA Issue",
                  "value": "${formatIssue(issue)}",
                  "short": true
              },
              {
                  "title": "Step${steps.size > 1 ? "s" : ""}",
                  "value": "${formatList(steps, false)}",
                  "short": true
              },
              {
                  "title": "Description",
                  "value": "${desc}",
                  "short": false
              }
    """
    if (!logs.allWhitespace) {
        attachment += """,
               {
                    "title": "Logs",
                    "value": "```${logs}```",
                    "short": false
               }
        """
    }
    attachment += """
          ],
          "footer": "${emoji.allWhitespace ?: ":$emoji: "}Build ${getValue("BUILD_NUMBER")} started by ${getValue("BUILD_USER")}",
          "ts": ${NOW}
      }
    """
    sendAttachment(channels, attachment)
}

/**
 * posts an attachment to the specified slack channels
 */
def sendAttachment(ArrayList channels, String attachment) {
    channels.each { channel ->
        // assemble payload
        def payload = """
        {
          "channel": "${channel}",
          "attachments": [
            ${attachment}
          ]
        }
        """

        // post payload
        postPayload(payload)
    }
}

/**
 * posts a simple text message to the specified slack channels
 */
def sendText(ArrayList channels, String text) {
    channels.each { channel ->
        // assemble payload
        def payload = """
        {
          "channel": "${channel}",
          "text": "${text}"
        }
        """

        // post payload
        postPayload(payload)
    }
}

/**
 * post a payload to slack
 */
def postPayload(String payload) {
    def connection = SLACK_URL.toURL().openConnection()
    connection.addRequestProperty("Content-Type", "application/json")

    // posting payload to slack
    // println(payload)
    connection.setRequestMethod("POST")
    connection.doOutput = true
    connection.outputStream.withWriter {
        it.write(payload)
        it.flush()
    }
    connection.connect()

    try {
        connection.content.text
    } catch (IOException e) {
        try {
            println(((HttpURLConnection)connection).errorStream.text)
        } catch (Exception ignored) {
        } finally {
            throw e
        }
    }
}

/**
 * looks up for a value in the arguments (which get precedence), then the environment variables
 */
String getValue(String name) {
    def val
    args.each {
        if (it.startsWith(name + ":")) {
            val = it.substring(1 + it.indexOf(":"))
        }
    }
    if (!val) {
        val = System.getenv()[name]
    }
    return (val ?: "")
}

/**
 * Swing away Merrill!
 */
if (args.size() > 0) {
    def action = args[0]
    sendNotification(action)
}
