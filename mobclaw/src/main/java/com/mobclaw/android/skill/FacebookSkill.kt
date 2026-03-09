package com.mobclaw.android.skill

/**
 * Skill: Navigating and interacting with the Facebook app.
 *
 * Teaches the LLM the Facebook app's UI patterns: news feed, creating posts,
 * navigating to profile, liking, commenting, and using the search function.
 */
class FacebookSkill : MobSkill {

    override val id = "facebook"

    override val name = "Facebook"

    override val triggerKeywords = listOf(
        "facebook", "fb", "post on facebook", "facebook post",
        "facebook profile", "news feed",
    )

    override fun instructions(): String = """
You need to interact with the Facebook app. Follow these instructions carefully.

### Language Awareness — CRITICAL
Facebook's UI language depends on the user's Facebook language settings (which may differ from the device language).
- After calling `screen_read()`, **read the actual text values** to determine the UI language.
- Do NOT assume English labels. For example:
  - "What's on your mind?" → "Bạn đang nghĩ gì?" (Vietnamese) / "你在想什么？" (Chinese)
  - "Like" → "Thích" (Vietnamese) / "좋아요" (Korean)
  - "Comment" → "Bình luận" (Vietnamese)
  - "Share" → "Chia sẻ" (Vietnamese)
  - "Post" → "Đăng" (Vietnamese)
  - "Not now" → "Không phải bây giờ" (Vietnamese)
- **Always prefer resourceId, className, and icon descriptions** to identify elements:
  - Bottom nav tabs: identify by icon descriptions or resourceId, not text labels
  - Like/Comment/Share buttons: identify by their position below posts and resourceId
  - Compose area: look for editable fields or resourceId with `composer`, `status`
- Facebook **icons are universal** (thumbs up, comment bubble, share arrow) — use desc values to identify them.

### Opening Facebook
- Package name: `com.facebook.katana`
- Call `open_app(package_name="com.facebook.katana")`
- If not installed: `list_apps(filter="facebook")` — note that "Facebook" and "Facebook Lite" are different apps

### Facebook App Layout
The Facebook app typically has a **bottom navigation bar** with tabs (identified by icons, not text):
- **Home** (house icon) — the main feed
- **Watch** (video/play icon) — video content
- **Marketplace** (shop icon) — buy/sell
- **Groups** (people icon) — groups
- **Notifications** (bell icon) — recent notifications
- **Menu** (hamburger/three lines) — settings, profile, and more

### Creating a Post
1. From the Home/News Feed tab, look for the compose area at the top:
   - It contains an editable field or a clickable text area (the "what's on your mind" equivalent)
   - Identify by resourceId or the editable/clickable property, not by matching English text
2. Click on the compose area
3. This opens the post composer
4. Type your post content in the text field
5. To add a photo: look for a gallery/photo option (camera or image icon)
6. Press the **Post/Submit** button (usually top-right) — identify by position and resourceId

### Searching on Facebook
1. Look for the search icon (magnifying glass) at the top of the screen — identify by icon desc
2. Click it to open the search bar
3. Type your search query
4. Results will show People, Pages, Groups, Posts

### Viewing a Profile
1. Tap the Menu tab (hamburger icon in bottom nav)
2. Look for your name/photo at the top of the menu
3. Click to view your profile

### Liking & Commenting
- **Like**: clickable button below a post with thumbs up icon — identify by icon/desc
- **Comment**: clickable button below a post with comment bubble icon
- **Share**: clickable button below a post with share/forward icon

### Common Pitfalls
- Facebook frequently updates its UI — element IDs and layout may change
- The app may show interstitial dialogs — dismiss by clicking "X" or the secondary/dismiss button
- Facebook uses a WebView for some content — elements may only be interactable via `tap(x, y)`
- Scrolling the news feed: use `scroll(direction="down")` to see more posts
- If login is required, look for email and password input fields (editable)

### Verification
- After posting: scroll up on the feed to see your new post
- After liking: the Like button should change state (filled/colored)
    """.trimIndent()
}
