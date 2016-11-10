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
    def customer = getValue("customer")
    def steps = getValue("steps").tokenize(",")
    def logs = getValue("logs")

    // debug
    println("----------")
    println("action: '${action}'")
    println("channels: ${channels}")
    println("adVersion: '${adVersion}'")
    println("jbVersion: '${jbVersion}'")
    println("reason: '${reason}'")
    println("issue: '${issue}'")
    println("customers: ${customers}")
    println("customer: '${customer}'")
    println("steps: ${steps}")
    println("logs: '${logs}'")
    println("----------")


    // now notify (but only under certain conditions, as we do not want to send uninformative messages)
    if ((customers.size() > 0 || !customer.allWhitespace) && steps.contains("Prod") && (adVersion != NA || jbVersion != NA)) {
        switch (action.toUpperCase()) {
            case "START":
                // notifies that a deployment has just been launched
                if (customers.size() > 0) {
                    if (customer.allWhitespace) {
                        // sent only when upgrading multiple marketplaces
                        sendDeployNotification(action, channels, MUTE, adVersion, jbVersion, reason, issue, customers, steps, "", "rocket",
                                "Deployment of ${formatList(versions, false)} " +
                                        "to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} launched.",
                                "The following marketplace${customers.size > 1 ? "s" : ""} " +
                                        "will be upgraded: ${formatList(customers, true)}.")
                    }
                } else if (!customer.allWhitespace) {
                    // sent only when upgrading a single marketplace
                    sendDeployNotification(action, channels, MUTE, adVersion, jbVersion, reason, issue, [customer], steps, "", "rocket",
                            "Deployment of ${formatList(versions, false)} to ${customer} launched.",
                            "The following marketplace will be upgraded: *${customer}*.")
                }
                break
            case "SUCCESS":
                // notifies that a deployment has just been completed successfully
                if (customers.size() > 0) {
                    // sent only when upgrading multiple marketplaces
                    sendDeployNotification(action, channels, INFO, adVersion, jbVersion, reason, issue, customers, steps, "", "checkered_flag",
                            "Deployment of ${formatList(versions, false)} " +
                                    "to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} completed.",
                            "The following marketplace${customers.size > 1 ? "s" : ""} " +
                                    "ha${customers.size > 1 ? "ve" : "s"} been upgraded: ${formatList(customers, true)}.")
                }
                if (!customer.allWhitespace) {
                    // sent for every upgraded marketplace
                    sendDeployNotification(action, channels, INFO, adVersion, jbVersion, reason, issue, [customer], steps, "", "checkered_flag",
                            "Deployment of ${formatList(versions, false)} to ${customer} completed.",
                            "The following marketplace has been upgraded: *${customer}*.")
                }
                break
            case "FAILURE":
                // notifies that a deployment has just failed
                if (customers.size() > 0) {
                    // sent only when upgrading multiple marketplaces
                    sendDeployNotification(action, channels, FAIL, adVersion, jbVersion, reason, issue, customers, steps, logs, "bomb",
                            "Deployment of ${formatList(versions, false)} " +
                                    "to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} failed.",
                            "The following marketplace${customers.size > 1 ? "s" : ""} " +
                                    "may not have been upgraded: ${formatList(customers, true)}.")
                }
                if (!customer.allWhitespace) {
                    // sent for every upgraded marketplace
                    sendDeployNotification(action, channels, FAIL, adVersion, jbVersion, reason, issue, [customer], steps, logs, "bomb",
                            "Deployment of ${formatList(versions, false)} to ${customer} failed.",
                            "The following marketplace has not been upgraded: *${customer}*.")
                }
                break
            default:
                // notifies that a deployment has just ended... but in a weird fashion
                if (customers.size() > 0) {
                    // sent only when upgrading multiple marketplaces
                    sendDeployNotification(action, channels, WARN, adVersion, jbVersion, reason, issue, customers, steps, logs, "warning",
                            "Deployment of ${formatList(versions, false)} " +
                                    "to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} ended.",
                            "The following marketplace${customers.size > 1 ? "s" : ""} " +
                                    "may not have been upgraded: ${formatList(customers, true)}.")
                }
                if (!customer.allWhitespace) {
                    // sent for every upgraded marketplace
                    sendDeployNotification(action, channels, WARN, adVersion, jbVersion, reason, issue, [customer], steps, logs, "warning",
                            "Deployment of ${formatList(versions, false)} to ${customer} ended.",
                            "The following marketplace may not have been upgraded: *${customer}*.")
                }
        }
    }
}

/**
 * escape the provided string to make it json-safe
 */
String escapeJson(String str) {
    // escape backslashes, then double quotes
    return str.replaceAll("\\\\", "\\\\\\\\").replaceAll("\"", "\\\\\"")
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
def sendDeployNotification(String action, ArrayList channels, String color, String adVersion, String jbVersion,
        String reason, String issue, ArrayList customers, ArrayList steps, String logs, String emoji,
        String fallback, String desc) {
    def attachment = """
      {
          "mrkdwn_in": ["pretext", "text", "fields"],
          "color": "#${color}",
          "fallback": "[${action}] ${escapeJson(fallback)}",
          "title": "[${action}] ${escapeJson(reason)}",
          "title_link": "${escapeJson(getValue("BUILD_URL"))}",
          "fields": [
              {
                  "title": "AppDirect",
                  "value": "${escapeJson(adVersion)}",
                  "short": true
              },
              {
                  "title": "JBilling",
                  "value": "${escapeJson(jbVersion)}",
                  "short": true
              },
              {
                  "title": "JIRA Issue",
                  "value": "${escapeJson(formatIssue(issue))}",
                  "short": true
              },
              {
                  "title": "Step${steps.size > 1 ? "s" : ""}",
                  "value": "${escapeJson(formatList(steps, false))}",
                  "short": true
              },
              {
                  "title": "Description",
                  "value": "${escapeJson(desc)}",
                  "short": false
              }
    """
    if (!logs.allWhitespace) {
        attachment += """,
               {
                    "title": "Logs",
                    "value": "```${escapeJson(logs)}```",
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

    try {
        // for debugging purposes
        new File(getValue("WORKSPACE") + "/payload-${NOW}.json").withWriter { out ->
            out.println(payload)
        }
    } catch (Exception ignored) {
        // ignored
    }

    // posting payload to slack
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
            println(payload)
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
def action = "START"
if (args.size() > 0 && !args[0].contains(':')) {
    action = args[0]
}
sendNotification(action)
