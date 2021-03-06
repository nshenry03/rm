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
    def handles = getValue("handles").tokenize(', ')
    def adVersion = getValue("appdirectVersion").allWhitespace ? NA : getValue("appdirectVersion")
    def bulkVersion = getValue("bulkVersion").allWhitespace ? NA : getValue("bulkVersion")
    def jbVersion = getValue("billingVersion").allWhitespace ? NA : getValue("billingVersion")
    def versions = []
    if (adVersion != NA) versions << "AppDirect ${adVersion}"
    if (bulkVersion != NA) versions << "Bulk ${bulkVersion}"
    if (jbVersion != NA) versions << "JBilling ${jbVersion}"
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
    println("handles: ${handles}")
    println("adVersion: '${adVersion}'")
    println("bulkVersion: '${bulkVersion}'")
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
                        sendDeployNotification(action, channels, ["!here"] + handles, MUTE, adVersion, bulkVersion, jbVersion,
                                reason, issue, customers, steps, "", "rocket",
                                "Deployment of ${formatList(versions, false)} " +
                                        "to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} launched.",
                                "The following marketplace${customers.size > 1 ? "s" : ""} " +
                                        "will be upgraded: ${formatList(customers, true)}.")
                    }
                } else if (!customer.allWhitespace) {
                    // sent only when upgrading a single marketplace
                    sendDeployNotification(action, channels, handles, MUTE, adVersion, bulkVersion, jbVersion,
                            "Upgrade of ${customer} launched.",
                            issue, [customer], steps, "", "rocket",
                            "Upgrade of ${customer} to ${formatList(versions, false)} launched.", "")
                }
                break
            case "SUCCESS":
                // notifies that a deployment has just been completed successfully
                if (customers.size() > 0 && customer.allWhitespace) {
                    // sent only when upgrading multiple marketplaces
                    sendDeployNotification(action, channels, ["!here"] + handles, INFO, adVersion, bulkVersion, jbVersion,
                            reason, issue, customers, steps, "", "checkered_flag",
                            "Deployment of ${formatList(versions, false)} " +
                                    "to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} completed.",
                            "The following marketplace${customers.size > 1 ? "s" : ""} " +
                                    "ha${customers.size > 1 ? "ve" : "s"} been upgraded: ${formatList(customers, true)}.")
                }
                if (!customer.allWhitespace) {
                    // sent for every upgraded marketplace
                    sendDeployNotification(action, channels, handles, INFO, adVersion, bulkVersion, jbVersion,
                            "Upgrade of ${customer} completed.",
                            issue, [customer], steps, "", "checkered_flag",
                            "Upgrade of ${customer} to ${formatList(versions, false)} completed.", "")
                }
                break
            case "FAILURE":
                // notifies that a deployment has just failed
                if (customers.size() > 0 && customer.allWhitespace) {
                    // sent only when upgrading multiple marketplaces
                    sendDeployNotification(action, channels, ["!here"] + handles, FAIL, adVersion, bulkVersion, jbVersion,
                            reason, issue, customers, steps, logs, "bomb",
                            "Deployment of ${formatList(versions, false)} " +
                                    "to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} failed.",
                            "The following marketplace${customers.size > 1 ? "s" : ""} " +
                                    "may not have been upgraded: ${formatList(customers, true)}.")
                }
                if (!customer.allWhitespace) {
                    // sent for every upgraded marketplace
                    sendDeployNotification(action, channels, handles, FAIL, adVersion, bulkVersion, jbVersion,
                            "Upgrade of ${customer} failed.",
                            issue, [customer], steps, logs, "bomb",
                            "Upgrade of ${customer} to ${formatList(versions, false)} failed.", "")
                }
                break
            default:
                // notifies that a deployment has just ended... but in a weird fashion
                if (customers.size() > 0 && customer.allWhitespace) {
                    // sent only when upgrading multiple marketplaces
                    sendDeployNotification(action, channels, ["!here"] + handles, WARN, adVersion, bulkVersion, jbVersion,
                            reason, issue, customers, steps, logs, "warning",
                            "Deployment of ${formatList(versions, false)} " +
                                    "to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} ended.",
                            "The following marketplace${customers.size > 1 ? "s" : ""} " +
                                    "may not have been upgraded: ${formatList(customers, true)}.")
                }
                if (!customer.allWhitespace) {
                    // sent for every upgraded marketplace
                    sendDeployNotification(action, channels, handles, WARN, adVersion, bulkVersion, jbVersion,
                            "Upgrade of ${customer} ended.",
                            issue, [customer], steps, logs, "warning",
                            "Upgrade of ${customer} to ${formatList(versions, false)} ended.", "")
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
def sendDeployNotification(String action, ArrayList channels, ArrayList handles, String color,
                           String adVersion, String bulkVersion, String jbVersion,
                           String title, String issue, ArrayList customers, ArrayList steps, String logs, String emoji,
                           String fallback, String desc) {

    // get a few more environment variables
    def jobName = getValue("JOB_NAME")
    def buildNumber = getValue("BUILD_NUMBER")
    def buildUrl = getValue("BUILD_URL")
    def buildUser = getValue("BUILD_USER")

    // prepare attachment
    def attachment = """
      {
          "mrkdwn_in": ["pretext", "text", "fields"],
          "color": "#${color}",
          "fallback": "[${action}] ${escapeJson(fallback)}",
          "title": "[${action}] ${escapeJson(title)}",
          "title_link": "${escapeJson(buildUrl)}",
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
        """
    if (bulkVersion != NA) {
        attachment += """
                {
                    "title": "Bulk",
                    "value": "${escapeJson(bulkVersion)}",
                    "short": false
                },
        """
    }
    attachment += """
              {
                  "title": "JIRA Issue",
                  "value": "${escapeJson(formatIssue(issue))}",
                  "short": true
              },
              {
                  "title": "Step${steps.size > 1 ? "s" : ""}",
                  "value": "${escapeJson(formatList(steps, false))}",
                  "short": true
              }
    """
    if (!desc.allWhitespace) {
        attachment += """,
              {
                  "title": "Description",
                  "value": "${escapeJson(desc)}",
                  "short": false
              }
        """
    }
    if (!logs.allWhitespace) {
        attachment += """,
               {
                    "title": "Logs",
                    "value": "```${escapeJson(logs)}```",
                    "short": false
               }
        """
    }
    if (handles) {
        attachment += """,
              {
                  "value": "${escapeJson(handles.collect{"<${it}>"}.join(' '))}",
                  "short": false
              }
        """
    }
    def footer = "${emoji.allWhitespace ?: ":$emoji: "}${jobName} #${buildNumber}"
    if (!buildUser.allWhitespace) {
        footer += " started by ${buildUser}"
    }
    attachment += """
          ],
          "footer": "${footer}",
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
sendNotification(action.toUpperCase())
