package computer.helium.mobile

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView

internal class RustClickListener(
    private val activity: MainActivity,
    private val action: Int,
    private val payload: String?,
) : View.OnClickListener {
    override fun onClick(view: View) {
        activity.nativeOnAction(action, payload)
    }
}

internal class RustEditorListener(
    private val activity: MainActivity,
    private val editor: TextView,
) : TextView.OnEditorActionListener {
    override fun onEditorAction(view: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId != EditorInfo.IME_ACTION_GO && actionId != EditorInfo.IME_ACTION_SEARCH && actionId != EditorInfo.IME_ACTION_DONE) {
            return false
        }
        activity.nativeOnNavigate(editor.text.toString())
        val inputMethodManager = editor.context.getSystemService(InputMethodManager::class.java)
        inputMethodManager?.hideSoftInputFromWindow(editor.windowToken, 0)
        editor.clearFocus()
        return true
    }
}
