package desu.inugram.ui

import android.content.DialogInterface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper

/**
 * Single-line text-input dialog. onSubmit returns true to dismiss, false to keep
 * the dialog open and shake the input.
 */
fun showInputDialog(
    fragment: BaseFragment,
    title: CharSequence,
    hint: CharSequence? = null,
    initialText: CharSequence? = null,
    selectAll: Boolean = false,
    inputType: Int = InputType.TYPE_CLASS_TEXT,
    onSubmit: (String) -> Boolean,
): AlertDialog? {
    val ctx = fragment.parentActivity ?: return null
    val theme = fragment.resourceProvider
    val typedInputType = inputType
    val editText = EditTextBoldCursor(ctx).apply {
        background = null
        setLineColors(
            Theme.getColor(Theme.key_dialogInputField, theme),
            Theme.getColor(Theme.key_dialogInputFieldActivated, theme),
            Theme.getColor(Theme.key_text_RedBold, theme),
        )
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        setTextColor(Theme.getColor(Theme.key_dialogTextBlack, theme))
        maxLines = 1
        setLines(1)
        this.inputType = typedInputType
        gravity = Gravity.LEFT or Gravity.TOP
        setSingleLine(true)
        imeOptions = EditorInfo.IME_ACTION_DONE
        setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, theme))
        setCursorSize(AndroidUtilities.dp(20f))
        setCursorWidth(1.5f)
        setPadding(0, AndroidUtilities.dp(4f), 0, 0)
        if (hint != null) this.hint = hint
        if (initialText != null) {
            setText(initialText)
            if (selectAll) setSelection(0, initialText.length)
        }
    }
    val container = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            editText,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP or Gravity.LEFT, 24, 6, 24, 0),
        )
    }
    val submit = submit@{
        val text = editText.text?.toString()?.trim().orEmpty()
        val ok = onSubmit(text)
        if (!ok) AndroidUtilities.shakeView(editText)
        ok
    }
    val dialog = AlertDialog.Builder(ctx, theme)
        .setTitle(title)
        .setView(container)
        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
        .setPositiveButton(LocaleController.getString(R.string.OK)) { _, _ -> }
        .create()
    editText.setOnEditorActionListener { _, _, _ ->
        if (submit()) dialog.dismiss()
        true
    }
    dialog.setOnShowListener {
        AndroidUtilities.runOnUIThread {
            editText.requestFocus()
            AndroidUtilities.showKeyboard(editText)
        }
    }
    fragment.showDialog(dialog)
    dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
        if (submit()) dialog.dismiss()
    }
    return dialog
}
