package com.mobclaw.android.skill

/**
 * Skill: Opening and finding apps.
 *
 * Teaches the LLM the reliable pattern for finding and launching any app on the device
 * using `list_apps` and `open_app` instead of navigating the home screen.
 */
class OpenAppSkill : MobSkill {

    override val id = "open_app"

    override val name = "Open & Launch Apps"

    override val triggerKeywords = listOf(
        "open", "launch", "start app", "run app", "go to app",
    )

    override fun instructions(): String = """
You need to open an app. Follow this reliable pattern:

### Language Awareness — CRITICAL
The device may be set to ANY language (English, Vietnamese, Chinese, Korean, etc.).
- After calling `screen_read()`, **look at the actual text/desc values** to determine the UI language.
- Do NOT assume English labels. For example, "Settings" may appear as "Cài đặt" (Vietnamese), "設定" (Chinese), "설정" (Korean).
- When searching for apps using `list_apps(filter)`, the app NAME shown is in the device language, but the **package name is always the same** regardless of language. Always rely on package names.
- Use **resourceId** (e.g. `id/title`, `id/search_button`) for element identification — IDs are language-independent.

### Step-by-step
1. **If you know the package name** (e.g. `com.android.settings`), call `open_app(package_name)` directly.
2. **If you don't know the package name**, call `list_apps(filter="<app name>")` to search.
   - Example: `list_apps(filter="chrome")` → finds `com.android.chrome`
   - If the filter in English returns nothing, try the app name in other languages, or use `list_apps()` with no filter to browse all.
3. Call `open_app(package_name)` with the result.
4. Call `wait(milliseconds=1000)` to let the app load.
5. Call `screen_read()` to see the app's current state.

### Common Package Names
| App | Package |
|-----|---------|
| Settings | `com.android.settings` |
| Chrome | `com.android.chrome` |
| Phone/Dialer | `com.android.dialer` or `com.google.android.dialer` |
| Messages | `com.google.android.apps.messaging` |
| Gmail | `com.google.android.gm` |
| Camera | `com.google.android.GoogleCamera` |
| Maps | `com.google.android.apps.maps` |
| YouTube | `com.google.android.youtube` |
| Play Store | `com.android.vending` |
| Calculator | `com.google.android.calculator` |
| Calendar | `com.google.android.calendar` |
| Contacts | `com.android.contacts` |
| Facebook | `com.facebook.katana` |
| Messenger | `com.facebook.orca` |
| WhatsApp | `com.whatsapp` |
| Instagram | `com.instagram.android` |
| Telegram | `org.telegram.messenger` |

### Important
- **NEVER** try to find app icons on the home screen — always use `open_app`.
- If `open_app` fails with "not found", the app may not be installed. Use `list_apps()` with no filter to see all installed apps.
    """.trimIndent()
}
