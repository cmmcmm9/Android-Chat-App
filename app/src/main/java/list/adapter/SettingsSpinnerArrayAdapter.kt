package list.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

/**
 * Custom Array adapter class to handle
 * spinner options (drop down menu).
 * Used in @see[in_chats.kt] and @see[main_page.kt]
 * Used for settings on main page, and settings for in chats
 * @constructor
 * TODO
 *
 * @param context
 * @param list
 */
class SettingsSpinnerArrayAdapter(context: Context, list: List<String>) : ArrayAdapter<String>(
    context,
    android.R.layout.simple_spinner_dropdown_item,
    list
) {

    /**
     * Function to get the drop down view for the spinner when selected.
     * Will hide the last position, otherwise awkward padding on the end of the
     * spinner view in UI.
     *
     * @param position : position of the given array
     * @param convertView : view to convert
     * @param parent : parent of the view
     * @return View? : formatted view (if possible, else null)
     */
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        val view: View?
        if (position == 0) {
            val tv = TextView(context)
            tv.visibility = View.GONE
            view = tv
        } else {
            view = super.getDropDownView(position, null, parent)
        }
        return view
    }



}