package com.wamynobe.mobclaw.skill

/**
 * Skill: Sending SMS/text messages.
 *
 * Teaches the LLM how to navigate the Messages app to compose and send SMS messages,
 * including finding contacts, typing messages, and handling common UI patterns.
 */
class MessageSkill : MobSkill {

    override val id = "message"

    override val name = "SMS Messages"

    override val triggerKeywords = listOf(
        "message", "sms", "text message", "send text", "send a message",
    )

    override fun instructions(): String = """
You need to send an SMS/text message. Follow these instructions carefully.

### Language Awareness — CRITICAL
The messaging app UI may be in ANY language (English, Vietnamese, Chinese, Korean, etc.).
- After calling `screen_read()`, **read the actual text/hint values** to determine the UI language.
- Do NOT assume English labels like "Start chat", "New message", "Send", "Text message".
  - In Vietnamese: "Bắt đầu trò chuyện", "Tin nhắn mới", "Gửi", "Tin nhắn văn bản"
  - Other languages will have their own translations
- **Always prefer resourceId and className** to identify elements:
  - Compose FAB: look for `FloatingActionButton` or resourceId with `fab`, `compose`, `start_chat`
  - Recipient field: look for editable field at the top, resourceId with `to`, `recipient`, `contact`
  - Message input: look for editable field at the bottom, resourceId with `compose`, `message`, `text`
  - Send button: look for resourceId with `send`, or an ImageButton/ImageView next to the text input

### How to Send a New Message
1. Open the Messages app: `open_app(package_name="com.google.android.apps.messaging")`
   - On Samsung: `open_app(package_name="com.samsung.android.messaging")`
   - If neither works: `list_apps(filter="message")` to find the correct package
2. Wait: `wait(milliseconds=1000)`
3. Read the screen: `screen_read()`
4. **Determine the UI language** from the visible text elements
5. Look for the **compose/new message** button:
   - Identify by className `FloatingActionButton` or resourceId containing `fab`, `compose`, `start_chat`
   - Do NOT rely solely on text labels — use resourceId and className
6. Click the compose button
7. In the **To/Recipient** field:
   - Identify by its editable state and position at the top, or resourceId containing `to`, `recipient`
   - Type the phone number or contact name
   - Use `input_text(node_id, text="<number or name>")`
   - If typing a name, wait for suggestions and click the matching contact
   - If typing a number, you may need to press Enter or click a "Send to" option
8. Find the **message input** field:
   - Usually the editable field at the bottom, resourceId containing `compose`, `message_body`, `text`
9. Type the message: `input_text(node_id, text="<your message>")`
10. Press the **Send** button:
   - Identify by resourceId containing `send`, or look for a clickable ImageButton/ImageView next to the text input

### How to Reply to an Existing Conversation
1. Open Messages app (same as above)
2. Read the screen to see the conversation list
3. Click on the conversation you want to reply to
4. The message input field will be at the bottom
5. Type and send as described above

### Common Pitfalls
- After typing in the recipient field, you may need to select from a dropdown/suggestions list
- The send button may be disabled until you type a message
- On some devices, you need to dismiss the keyboard to see the send button
- If the contact is not found, try the full phone number with country code
- Group messages: some apps require you to add recipients one by one

### Verification
- After sending, the message should appear in the conversation as a sent message (usually on the right side)
- Look for a delivery status indicator (sent/delivered checkmarks)
    """.trimIndent()
}
