package com.berghof.scanner

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * System-wide accessibility service that remembers the last focused editable
 * text field (in ANY app, including a browser showing WebVisu) and can push
 * the scanned value directly into it, avoiding a manual long-press-paste.
 *
 * The user must explicitly enable this under
 * Settings > Accessibility > Berghof Scanner.
 */
class PasteAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react per-event; pasteIntoFocusedField() looks up
        // the currently focused input node on demand via the active window.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return if (focused != null && focused.isEditable) focused else null
    }

    fun paste(value: String): Boolean {
        val node = findFocusedEditableNode() ?: return false
        val arguments = Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            value
        )
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        node.recycle()
        return result
    }

    companion object {
        private var instance: PasteAccessibilityService? = null

        val isRunning: Boolean get() = instance != null

        /** Returns true if a focused editable field was found and updated. */
        fun pasteIntoFocusedField(value: String): Boolean {
            return instance?.paste(value) ?: false
        }
    }
}
