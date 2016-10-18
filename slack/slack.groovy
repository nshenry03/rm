JIRA_URL = "https://appdirect.jira.com/browse"
SLACK_URL = "https://hooks.slack.com/services/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"
MUTE = "adaeaf"
INFO = "64a62e"
WARN = "eeae3f"
FAIL = "d00000"
NOW = (new Date()).toTimestamp().getTime()/1000

/**
 * send a notification, using slack
 */
def sendNotification(String action) {
    // handle environment variables
    def adVersion = (System.getenv("appdirectVersion") ?: "").allWhitespace ? "N/A" : System.getenv("appdirectVersion")
    def jbVersion = (System.getenv("billingVersion") ?: "").allWhitespace ? "N/A" : System.getenv("billingVersion")
    def issue = (System.getenv("issue") ?: "").toUpperCase()
    def customers = (System.getenv("customers") ?: "").tokenize(",")
    def steps = (System.getenv("steps") ?: "").tokenize(",")


    // notify
    switch (action) {
        case "start":
            deployStartNotification("@joan.roch", adVersion, jbVersion, issue, customers, steps)
            break
        case "success":
            deployEndNotification("@joan.roch", adVersion, jbVersion, issue, customers, steps)
            break
        case "failure":
            deployFailNotification("@joan.roch", adVersion, jbVersion, issue, customers, steps)
            break
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
 * notifies that a deployment has just been launched
 */
def deployStartNotification(String channel, String adVersion, String jbVersion,
        String issue, ArrayList customers, ArrayList steps) {
    sendDeployNotification(channel, MUTE, adVersion, jbVersion, issue, customers, steps, "rocket",
            "Production deployment launched: AppDirect ${adVersion}, JBilling ${jbVersion}",
            "Production deployment to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} launched.",
            "The following marketplace${customers.size > 1 ? "s" : ""} " +
                    "will be upgraded: ${formatList(customers, true)}.")
}

/**
 * notifies that a deployment has just been completed
 */
def deployEndNotification(String channel, String adVersion, String jbVersion,
        String issue, ArrayList customers, ArrayList steps) {
    sendDeployNotification(channel, INFO, adVersion, jbVersion, issue, customers, steps, "checkered_flag",
            "Production deployment completed: AppDirect ${adVersion}, JBilling ${jbVersion}",
            "Production deployment to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} completed.",
            "The following marketplace${customers.size > 1 ? "s" : ""} " +
                    "ha${customers.size > 1 ? "ve" : "s"} been upgraded: ${formatList(customers, true)}.")
}

/**
 * posts a deployment notification to the specified slack channel
 */
def sendDeployNotification(String channel, String color, String adVersion, String jbVersion,
        String issue, ArrayList customers, ArrayList steps, String emoji,
        String fallback, String title, String desc) {
    def attachment = """
      {
          "mrkdwn_in": ["pretext", "text", "fields"],
          "color": "#${color}",
          "fallback": "${fallback}",
          "title": "${title}",
          "title_link": "${System.getenv("BUILD_URL")}",
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
          ],
          "footer": "${emoji.allWhitespace ?: ":" + emoji + ": "}Build ${System.getenv("BUILD_NUMBER")} started by ${System.getenv("BUILD_USER")}",
          "ts": ${NOW}
      }
  """
    sendAttachment(channel, attachment)
}

/**
 * notifies that a deployment has just failed
 */
def deployFailNotification(String channel, String adVersion, String jbVersion,
        String issue, ArrayList customers, ArrayList steps) {
    sendDeployNotification(channel, FAIL, adVersion, jbVersion, issue, customers, steps, "bomb",
            "Production deployment failed: AppDirect ${adVersion}, JBilling ${jbVersion}",
            "Production deployment to ${customers.size} marketplace${customers.size > 1 ? "s" : ""} failed.",
            "The following marketplace${customers.size > 1 ? "s" : ""} " +
                    "may not have been upgraded: ${formatList(customers, true)}.")
}

/**
 * posts an attachment to the specified slack channel
 */
def sendAttachment(String channel, String attachment) {
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

/**
 * posts a simple text message to the specified slack channel
 */
def sendText(String channel, String text) {
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

/**
 * post a payload to slack
 */
def postPayload(String payload) {
    def connection = SLACK_URL.toURL().openConnection()
    connection.addRequestProperty("Content-Type", "application/json")

    // posting payload to slack
    def encodedPayload = URLEncoder.encode(payload)
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

try {
    if (args != null & args.size() > 0) {
        println(args)
        sendNotification(args[0])
    }
} catch (MissingPropertyException ignored) {
    // do nothing
}
