package list.adapter


import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.example.tapin.R
import java.util.*
import kotlin.collections.ArrayList

/**
 * Helper data class to hold contact's to display for a group chat suggestion
 *
 * @property displayName : display name of the contact
 * @property email: email of the contact
 * @property contactJID : JID of the contact
 */
data class ContactsForGroupChat(
    val displayName: String,
    val email: String?,
    val contactJID: String
)

/**
 * Custom array adapter used to give suggestions to a user when creating a new group chat.
 *
 * @constructor
 *
 *
 * @param context : application context
 * @param contactsForGroupChatList  : list of user's contacts @see[ContactsForGroupChat]
 */
class AutoCompleteGroupChatAdapter(
    context: Context?,
    contactsForGroupChatList:  List<ContactsForGroupChat?>
) :
    ArrayAdapter<ContactsForGroupChat?>(context!!, 0, contactsForGroupChatList) {
    private val contactListFull = contactsForGroupChatList.distinctBy { it?.contactJID }

    /**
     * Get the filter object for the array
     *
     * @return contactFilter object
     */
    override fun getFilter(): Filter {
        return contactFilter
    }

    /**
     * Get the item in contact list
     *
     * @param position : position to get contact
     * @return @see[ContactsForGroupChat]
     */
    override fun getItem(position: Int): ContactsForGroupChat? {
        return contactListFull[position]
    }

    /**
     * Get list size of all contacts
     *
     * @return Int : list size
     */
    override fun getCount(): Int {
        return contactListFull.size
    }


    /**
     * Get the view to display for the suggestions.
     *
     * @param position
     * @param convertView
     * @param parent
     * @return : custom view
     */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView: View? = convertView
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(
                R.layout.row_item_for_group_chat, parent, false
            )
        }
        val textViewName: TextView? = convertView?.findViewById(R.id.display_contact_name_row_item)
        val textViewEmail: TextView? = convertView?.findViewById(R.id.display_contact_email_row_item)
        val contactItem: ContactsForGroupChat? = getItem(position)
        if (contactItem != null) {
            textViewName?.text = contactItem.displayName
            textViewEmail?.text = contactItem.email
        }
        return convertView!!
    }

    /**
     * Filter object to perform filtering based on text given
     */
    private val contactFilter: Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            val suggestions: MutableList<ContactsForGroupChat> = mutableListOf()
            if (constraint == null || constraint.isEmpty()) {
                contactListFull.forEach {
                    if (it != null) {
                        suggestions.add(it)
//                        results.values = contactListFull
//                        results.count = contactListFull.size
                    }
                }
            } else {
                val filterPattern = constraint.toString().toLowerCase(Locale.ROOT).trim { it <= ' ' }
                println("Filter Patter is $filterPattern")
                println(contactListFull)
                for (item in contactListFull) {
                    if(item?.email.isNullOrEmpty()){
                        if (item?.displayName?.toLowerCase(Locale.ROOT)?.contains(filterPattern)!!) {
                            suggestions.add(item)
                        }
                    }
                    else{
                        if (item?.displayName?.toLowerCase(Locale.ROOT)?.contains(filterPattern)!! || item.email?.toLowerCase(Locale.ROOT)?.contains(filterPattern)!!) {
                            suggestions.add(item)
                        }
                    }

                }
            }
            results.values = null
            results.count = 0
            results.values = suggestions
            results.count = suggestions.size
            return results
        }

        /**
         * Publish the results to user interface
         *
         * @param constraint
         * @param results : filtered results
         */
        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            val myResults = results.values as Collection<ContactsForGroupChat>
            clear()
            println("I got into publish results")
            println(myResults.distinct())
            addAll(myResults.distinct())
            notifyDataSetChanged()
        }

        /**
         * Convert the result (contact suggestion) to a string to display in chip
         * Will return the display name for the contact
         * @param resultValue : value to convert
         * @return : display name for contact
         */
        override fun convertResultToString(resultValue: Any): CharSequence {
            return (resultValue as ContactsForGroupChat).displayName
        }
    }

}