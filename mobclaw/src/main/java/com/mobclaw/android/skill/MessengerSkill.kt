package com.mobclaw.android.skill

/**
 * Skill: Using Facebook Messenger for chat.
 *
 * Teaches the LLM how to navigate Messenger: opening chats, sending messages,
 * creating new conversations, and handling common UI patterns.
 */
class MessengerSkill : MobSkill {

    override val id = "messenger"

    override val name = "Facebook Messenger"

    override val triggerKeywords = listOf(
        "messenger", "fb message", "facebook message", "facebook chat",
        "send on messenger", "chat on messenger",
    )

    override fun instructions(): String = """
You need to use Facebook Messenger. Follow these instructions carefully.

### Language Awareness — CRITICAL
Messenger's UI language follows the user's Facebook language settings (which may differ from the device language).
- After calling `screen_read()`, **read the actual text/hint/desc values** to determine the UI language.
- Do NOT assume English labels. For example:
  - "Chats" → "Đoạn chat" (Vietnamese) / "聊天" (Chinese)
  - "New message" → "Tin nhắn mới" (Vietnamese)
  - "Send" → "Gửi" (Vietnamese)
  - "Type a message" / "Aa" → may appear differently per language
  - "Search" → "Tìm kiếm" (Vietnamese)
- **Always prefer resourceId, className, and icon descriptions** to identify elements:
  - Compose FAB: look for `FloatingActionButton` or resourceId with `compose`, `new`, `fab`, or desc with a pencil/edit icon
  - Search bar: look for editable field at the top, or resourceId with `search`
  - Message input: look for editable field at the bottom, hint text (any language), resourceId with `message`, `compose`, `input`
  - Send button: look for a clickable ImageButton/ImageView to the right of the text input, resourceId with `send`
- Messenger **icons are universal** (pencil for compose, paper plane for send, camera for photo) — use icon descriptions.

### Opening Messenger
- Package name: `com.facebook.orca`
- Messenger Lite: `com.facebook.mlite`
- Call `open_app(package_name="com.facebook.orca")`
- If not installed: `list_apps(filter="messenger")`

### Messenger App Layout
Identify tabs by their **icons**, not text labels:
- **Chats tab**: chat bubble icon — main chat list
- **People tab**: person/contacts icon — online contacts
- **Stories tab**: circle/stories icon — stories from friends
- **Search bar**: at the top — identify by editable state or search icon
- **Compose button**: floating button with pencil/edit icon — for new messages

### Sending a New Message
1. Open Messenger
2. Look for the **compose/new message** button:
   - Identify by `FloatingActionButton` className or pencil/edit icon desc, NOT by text
3. Click the compose button
4. In the **To** field (editable field at top), type the contact's name
5. Wait for suggestions to appear, then click the matching contact
6. The chat opens — find the message input field at the bottom:
   - Identify by editable state, position at bottom, or resourceId containing `message`, `input`
7. Type your message: `input_text(node_id, text="<message>")`
8. Press the **Send** button:
   - Identify by resourceId containing `send`, or the clickable element to the right of the input

### Replying in an Existing Chat
1. From the Chats tab, scroll to find the conversation
2. Click on the conversation to open it
3. Type in the message field at the bottom (identify by editable state)
4. Press Send

### Searching for a Contact/Chat
1. Click the search bar at the top of the chat list (identify by editable state or search icon)
2. Type the person's name
3. Click on the matching result to open the conversation

### Sending Photos/Media
1. In an open chat, look for icons below/beside the message input:
   - Camera icon, gallery icon, "+" icon — identify by icon desc NOT text
2. Click the appropriate icon
3. Select or capture the media
4. Press Send

### Common Pitfalls
- Messenger may show "Message Requests" for non-friends — look for a separate section or banner
- The app may show prompts — dismiss by clicking "X" or the secondary button
- Some elements inside chat bubbles are WebViews and may need `tap(x, y)`
- If Messenger asks for permissions, click the allow/grant button (identify by position, usually right-side)
- Group chats: the compose flow is the same, but you add multiple recipients in the To field
- Messenger sometimes shows emoji suggestions — dismiss or ignore

### Verification
- After sending: the message should appear in the chat as a sent message
- Look for a delivery indicator (checkmarks or avatar)
    """.trimIndent()
}
