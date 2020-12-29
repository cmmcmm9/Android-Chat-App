package list.adapter

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tapin.R
import com.squareup.picasso.Picasso
import database.DisplayContactsRecyclerData
import database.TimeDateManager
import kotlinx.android.synthetic.main.activity_in_chats.*
import ktorClient.secureAvatarURL

/**
 * Custom adapter for recycler view used to display the user's contacts
 *
 * @property recyclerViewClickListener : listener for clicks on recycler items @see[TapInRecyclerViewClickListener]
 * @property recyclerViewLongPressListener : listener for long presses on recycler items @see[TapInRecyclerViewLongPressListener]
 * @constructor
 *
 *
 * @param context : context of the application
 */
class DisplayContactsListAdapter internal constructor(
    context: Context, private val recyclerViewClickListener: TapInRecyclerViewClickListener, private val recyclerViewLongPressListener: TapInRecyclerViewLongPressListener
) : RecyclerView.Adapter<DisplayContactsListAdapter.DisplayContactsViewHolder>() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var displayContacts = emptyList<DisplayContactsRecyclerData>()
    private val onlineBubble = R.drawable.circle_green
    private val awayBubble = R.drawable.circle_yellow
    private val offlineBubble = R.drawable.circle_red

    /**
     * Inner class to format the view holder for a recycler item.
     *
     * @constructor
     * TODO
     *
     * @param itemView : view for item
     * @param clickListener : click listener for item
     * @param longPressListener : long press listener for item
     */
    inner class DisplayContactsViewHolder(itemView: View, clickListener: TapInRecyclerViewClickListener, longPressListener: TapInRecyclerViewLongPressListener ) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener {
        val contactName: TextView = itemView.findViewById(R.id.displayContactsName)
        val contactImage: ImageView = itemView.findViewById(R.id.displayContactPhoto)
        val timeAvailable: TextView = itemView.findViewById(R.id.contactAvailableTime)
        val statusBubble: ImageView = itemView.findViewById(R.id.contactStatusBubble)
        val availableTimeText: TextView = itemView.findViewById(R.id.availbe_today)
        var contactJID = ""
        var displayName = ""
        private val myClickListener = clickListener
        private val myLongPressListener = longPressListener
        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
        }

        /**
         * Called when a view has been clicked.
         *
         * @param v The view that was clicked.
         */
        override fun onClick(v: View?) {
            if (v != null) {
                myClickListener.onClick(view = v, contactJID , displayName)
            }
        }

        /**
         * Called when a view has been clicked and held.
         *
         * @param v The view that was clicked and held.
         *
         * @return true if the callback consumed the long click, false otherwise.
         */
        override fun onLongClick(v: View?): Boolean {
            return if (v != null) {
                myLongPressListener.onLongPress(view = v, contactJID , displayName)
                true
            } else false
        }


    }

    /**
     * Called when RecyclerView needs a new [ViewHolder] of the given type to represent
     * an item.
     *
     *
     * This new ViewHolder should be constructed with a new View that can represent the items
     * of the given type. You can either create a new View manually or inflate it from an XML
     * layout file.
     *
     *
     * The new ViewHolder will be used to display items of the adapter using
     * [.onBindViewHolder]. Since it will be re-used to display
     * different items in the data set, it is a good idea to cache references to sub views of
     * the View to avoid unnecessary [View.findViewById] calls.
     *
     * @param parent The ViewGroup into which the new View will be added after it is bound to
     * an adapter position.
     * @param viewType The view type of the new View.
     *
     * @return A new ViewHolder that holds a View of the given view type.
     * @see .getItemViewType
     * @see .onBindViewHolder
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DisplayContactsViewHolder {
        val itemView = inflater.inflate(R.layout.display_contacts, parent, false)
        return DisplayContactsViewHolder(itemView, recyclerViewClickListener, recyclerViewLongPressListener)
    }

    /**
     * Called by RecyclerView to display the data at the specified position. This method should
     * update the contents of the [ViewHolder.itemView] to reflect the item at the given
     * position.
     *
     *
     * Note that unlike [android.widget.ListView], RecyclerView will not call this method
     * again if the position of the item changes in the data set unless the item itself is
     * invalidated or the new position cannot be determined. For this reason, you should only
     * use the `position` parameter while acquiring the related data item inside
     * this method and should not keep a copy of it. If you need the position of an item later
     * on (e.g. in a click listener), use [ViewHolder.getAdapterPosition] which will
     * have the updated adapter position.
     *
     * Override [.onBindViewHolder] instead if Adapter can
     * handle efficient partial bind.
     *
     * @param holder The ViewHolder which should be updated to represent the contents of the
     * item at the given position in the data set.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: DisplayContactsViewHolder, position: Int) {
//        val contact = contacts[position]
//        holder.contactName.text = contact.contactName
        val currentContact = displayContacts[position]
        holder.contactJID = currentContact.contactJID
        holder.displayName = currentContact.contactName
        val displayTime = "${TimeDateManager.getPrettyTime(currentContact.timeAvailableStart )} to ${TimeDateManager.getPrettyTime(currentContact.timeAvailableEnd)}"

        println("Display time is $displayTime")
        holder.contactName.text = currentContact.contactName
        holder.timeAvailable.text = displayTime

        if(TimeDateManager.isAvailable(currentContact.timeAvailableStart, currentContact.timeAvailableEnd)){
            if(currentContact.isOnline) holder.statusBubble.setImageResource(onlineBubble)
            else holder.statusBubble.setImageResource(awayBubble)
        }
        else holder.statusBubble.setImageResource(offlineBubble)

        Picasso.get().load("$secureAvatarURL${currentContact.contactJID.substringBefore('@')}").error(R.drawable.person_def_avatar).into(holder.contactImage)



    }

    /**
     * Internal function to set the contacts to display
     *
     * @param displayContactsRecyclerData : List of DisplayContactsRecyclerData data class
     */
    internal fun setDisplayContactData(displayContactsRecyclerData: List<DisplayContactsRecyclerData>){
        this.displayContacts = displayContactsRecyclerData
        notifyDataSetChanged()
    }

    /**
     * Get item count of List of contacts to display
     *
     * @return
     */
    override fun getItemCount(): Int{
        return displayContacts.size
    }


}