package com.mobclaw.android.overlay

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/**
 * Floating overlay that displays real-time agent reasoning and actions.
 * Shows a translucent panel over other apps so the user can watch
 * what the LLM is thinking and doing.
 *
 * Requires SYSTEM_ALERT_WINDOW permission (Settings → Display over other apps).
 */
class AgentOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    private var statusText: TextView? = null
    private var reasoningText: TextView? = null
    private var actionsContainer: LinearLayout? = null
    private var scrollView: ScrollView? = null
    private var stopButton: TextView? = null

    private var isShowing = false

    /** Callback invoked when the user taps the stop button. */
    var onStopRequested: (() -> Unit)? = null

    /**
     * Show the overlay on screen.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing) return

        mainHandler.post {
            val view = createOverlayView()
            val params = createLayoutParams()

            // Make draggable
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isDragging = false

            view.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                        if (isDragging) {
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()
                            windowManager.updateViewLayout(view, params)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // Toggle collapse/expand on tap
                            toggleCollapse()
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(view, params)
            overlayView = view
            isShowing = true

            // Fade in
            ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                duration = 200
                start()
            }
        }
    }

    /**
     * Hide and remove the overlay.
     */
    fun hide() {
        if (!isShowing) return
        mainHandler.post {
            overlayView?.let {
                ObjectAnimator.ofFloat(it, "alpha", 1f, 0f).apply {
                    duration = 150
                    start()
                }
                mainHandler.postDelayed({
                    try {
                        windowManager.removeView(it)
                    } catch (_: Exception) {}
                    overlayView = null
                    isShowing = false
                }, 160)
            }
        }
    }

    // --- Public update methods (called from any thread) ---

    fun updateStatus(status: String) {
        mainHandler.post {
            statusText?.text = status
        }
    }

    fun showReasoning(text: String) {
        mainHandler.post {
            reasoningText?.apply {
                this.text = text
                visibility = View.VISIBLE
            }
            scrollToBottom()
        }
    }

    fun showAction(actionName: String, args: String, isPending: Boolean = true) {
        mainHandler.post {
            val actionView = createActionBadge(actionName, args, isPending)
            actionsContainer?.addView(actionView)
            scrollToBottom()

            // Keep max 6 action items visible
            val container = actionsContainer ?: return@post
            while (container.childCount > 6) {
                container.removeViewAt(0)
            }
        }
    }

    fun markActionComplete(success: Boolean) {
        mainHandler.post {
            val container = actionsContainer ?: return@post
            val lastChild = container.getChildAt(container.childCount - 1) ?: return@post
            val badge = lastChild.findViewWithTag<TextView>("action_badge") ?: return@post
            badge.setBackgroundColor(
                if (success) 0xFF1B5E20.toInt() // dark green
                else 0xFFB71C1C.toInt() // dark red
            )
        }
    }

    fun clearActions() {
        mainHandler.post {
            actionsContainer?.removeAllViews()
            reasoningText?.text = ""
        }
    }

    // --- Private helpers ---

    @SuppressLint("SetTextI18n")
    private fun createOverlayView(): View {
        val dp = context.resources.displayMetrics.density

        // Root container
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD1A1A2E.toInt()) // dark translucent navy
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            elevation = 8 * dp
        }

        // Header: "🦀 MobClaw" + status + stop + close
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(context).apply {
            text = "🦀 MobClaw"
            setTextColor(0xFFE0E0E0.toInt())
            textSize = 13f
            setPadding(0, 0, (8 * dp).toInt(), 0)
        }
        header.addView(title)

        statusText = TextView(context).apply {
            text = "Ready"
            setTextColor(0xFF90CAF9.toInt()) // light blue
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(statusText)

        // Stop button
        stopButton = TextView(context).apply {
            text = "🛑"
            textSize = 16f
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener { onStopRequested?.invoke() }
        }
        header.addView(stopButton)

        // Close button
        val closeButton = TextView(context).apply {
            text = "✕"
            textSize = 14f
            setTextColor(0xFFEF5350.toInt()) // red
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener { hide() }
        }
        header.addView(closeButton)

        root.addView(header)

        // Divider
        val divider = View(context).apply {
            setBackgroundColor(0x33FFFFFF)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).apply {
                topMargin = (4 * dp).toInt()
                bottomMargin = (4 * dp).toInt()
            }
        }
        root.addView(divider)

        // Reasoning text
        reasoningText = TextView(context).apply {
            setTextColor(0xFFB0BEC5.toInt()) // grey
            textSize = 10.5f
            maxLines = 4
            visibility = View.GONE
            setPadding(0, 0, 0, (4 * dp).toInt())
        }
        root.addView(reasoningText)

        // Actions container (vertical list of action badges)
        actionsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Scrollable wrapper
        scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                height = (100 * dp).toInt() // max height
            }
            addView(actionsContainer)
        }
        root.addView(scrollView)

        return root
    }

    @SuppressLint("SetTextI18n")
    private fun createActionBadge(name: String, args: String, isPending: Boolean): LinearLayout {
        val dp = context.resources.displayMetrics.density

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * dp).toInt(), (3 * dp).toInt(), (4 * dp).toInt(), (3 * dp).toInt())

            // Status indicator
            val badge = TextView(context).apply {
                tag = "action_badge"
                val icon = if (isPending) "⏳" else "✅"
                text = icon
                textSize = 10f
                setBackgroundColor(
                    if (isPending) 0xFF33691E.toInt() // olive
                    else 0xFF1B5E20.toInt() // green
                )
                setPadding((4 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
            }
            addView(badge)

            // Tool name
            val nameView = TextView(context).apply {
                text = " $name"
                setTextColor(0xFFFFAB40.toInt()) // orange
                textSize = 11f
                setPadding((4 * dp).toInt(), 0, 0, 0)
            }
            addView(nameView)

            // Args preview
            if (args.isNotBlank()) {
                val argsView = TextView(context).apply {
                    text = args
                    setTextColor(0xFF78909C.toInt()) // blue grey
                    textSize = 9.5f
                    maxLines = 1
                    setPadding((6 * dp).toInt(), 0, 0, 0)
                }
                addView(argsView)
            }
        }
    }

    private fun toggleCollapse() {
        val content = scrollView ?: return
        val reasoning = reasoningText ?: return
        if (content.visibility == View.VISIBLE) {
            content.visibility = View.GONE
            reasoning.visibility = View.GONE
        } else {
            content.visibility = View.VISIBLE
            if (reasoning.text.isNotEmpty()) reasoning.visibility = View.VISIBLE
        }
    }

    private fun scrollToBottom() {
        scrollView?.post { scrollView?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val dp = context.resources.displayMetrics.density
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            (280 * dp).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * dp).toInt()
            y = (80 * dp).toInt()
        }
    }
}
