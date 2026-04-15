package com.wamynobe.mobclaw.skill

/**
 * Skill: Making phone calls.
 *
 * Teaches the LLM how to navigate the Phone/Dialer app to make a call,
 * including dialing a number, selecting a contact, and handling call UI states.
 */
class CallSkill : MobSkill {

    override val id = "call"

    override val name = "Phone Calls"

    override val triggerKeywords = listOf(
        "call", "phone", "dial", "ring", "make a call",
    )

    override fun instructions(): String = """
You need to make a phone call. Follow these instructions carefully.

### Language Awareness — CRITICAL
The phone app UI may be in ANY language (English, Vietnamese, Chinese, Korean, etc.).
- After calling `screen_read()`, **read the actual text values** to determine the UI language.
- Do NOT assume tabs are labeled "Keypad", "Recents", "Contacts" — they may be "Bàn phím", "Gần đây", "Danh bạ" (Vietnamese) or other languages.
- **Always prefer resourceId and className** to identify elements:
  - Dial pad button: look for `resourceId` containing `dialpad`, `call`, `fab`, or className `FloatingActionButton`
  - Keypad tab: look for `resourceId` containing `tab`, `keypad`, `dialpad`
  - Search: look for `resourceId` containing `search`
- The **green call button** icon is universal — look for a clickable element with desc containing a phone icon or resourceId with `call`, `dial`, `fab`.

### How to Call a Phone Number
1. Open the dialer: `open_app(package_name="com.android.dialer")` or `open_app(package_name="com.google.android.dialer")`
   - If neither works, use `list_apps(filter="phone")` or `list_apps(filter="dialer")` to find it
   - Samsung: `com.samsung.android.dialer`
2. Wait for app to load: `wait(milliseconds=1000)`
3. Read the screen: `screen_read()`
4. **Determine the UI language** from the visible text elements (tab labels, titles, etc.)
5. Look for the **dial pad / keypad** tab/button:
   - Identify it by resourceId (e.g. `id/tab_keypad`) or by its icon, NOT by text label
   - Click it if the dial pad is not already showing
6. **Enter the phone number**: Tap each digit on the dial pad, OR:
   - Look for a text field where you can type the number directly
   - Use `input_text(node_id, text="<phone number>")` if there's an editable field
7. **Press the call button**: Look for a green phone icon button (usually at the bottom center)
   - It might have resourceId like `id/dialpad_floating_action_button` or `id/call_button`

### How to Call a Contact by Name
1. Open the dialer app (same as above)
2. Look for a **Contacts** or **Search** tab — identify by resourceId, not text
3. Click the search icon or search bar
4. Type the contact name using `input_text`
5. Read the screen to find the matching contact
6. Click on the contact entry
7. Look for a phone icon or call button next to their number
8. Click to initiate the call

### Common Pitfalls
- The dialer package name varies by device manufacturer
- If you see a "Recents" equivalent tab instead of the dial pad, switch to the keypad tab
- After initiating a call, the screen changes to the in-call UI — you can call `finish` at this point
- If asked to "call back", check the Recents tab for the last call entry

### Verification
- After pressing the call button, the screen should show an in-call UI with the number/contact name
- If you see the in-call screen, the call was successfully initiated — call `finish`
    """.trimIndent()
}
