package com.mobclaw.android.model

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStateTest {

    @Test
    fun toPromptText_includesVisibleNodeDetailsAndSkipsInvisibleNodes() {
        val visible = ScreenNode(
            id = "n0",
            className = "android.widget.Button",
            resourceId = "com.example:id/accept",
            text = "Accept",
            contentDescription = "Accept button",
            hintText = "Confirm",
            stateDescription = "Enabled",
            bounds = Rect(0, 0, 120, 50),
            isClickable = true,
            isLongClickable = false,
            isScrollable = false,
            isEditable = false,
            isCheckable = false,
            isChecked = false,
            isSelected = true,
            isFocused = false,
            isEnabled = true,
            isVisibleToUser = true,
            depth = 0,
            childCount = 0,
        )

        val invisible = ScreenNode(
            id = "n1",
            className = "android.widget.TextView",
            resourceId = null,
            text = "Hidden",
            contentDescription = null,
            hintText = null,
            stateDescription = null,
            bounds = Rect(0, 0, 10, 10),
            isClickable = false,
            isLongClickable = false,
            isScrollable = false,
            isEditable = false,
            isCheckable = true,
            isChecked = false,
            isSelected = false,
            isFocused = false,
            isEnabled = true,
            isVisibleToUser = false,
            depth = 1,
            childCount = 0,
        )

        val state = ScreenState(
            packageName = "com.example",
            activityName = "MainActivity",
            nodes = listOf(visible, invisible),
        )

        val text = state.toPromptText()

        assertTrue(text.startsWith("## Current Screen: com.example"))
        assertTrue(text.contains("Activity: MainActivity"))
        assertTrue(text.contains("Found 2 UI elements:"))
        assertTrue(text.contains("[n0] android.widget.Button (id/accept)"))
        assertTrue(text.contains("text: \"Accept\""))
        assertTrue(text.contains("desc: \"Accept button\""))
        assertTrue(text.contains("[clickable, selected]"))
        assertTrue(text.contains("bounds:"))
        assertFalse(text.contains("[n1]"))
    }

    @Test
    fun toPromptText_formatsCheckboxState() {
        val unchecked = ScreenNode(
            id = "n0",
            className = "android.widget.CheckBox",
            resourceId = "pkg:id/agree",
            text = "Agree",
            contentDescription = null,
            hintText = null,
            stateDescription = null,
            bounds = Rect(5, 5, 30, 25),
            isClickable = false,
            isLongClickable = false,
            isScrollable = false,
            isEditable = false,
            isCheckable = true,
            isChecked = false,
            isSelected = false,
            isFocused = false,
            isEnabled = false,
            isVisibleToUser = true,
            depth = 1,
            childCount = 0,
        )

        val text = ScreenState("com.example", null, listOf(unchecked)).toPromptText()

        assertEquals("## Current Screen: com.example", text.lines().first())
        assertTrue(text.contains("[n0] android.widget.CheckBox (id/agree)"))
        assertTrue(text.contains("[☐ unchecked, DISABLED]"))
    }
}
