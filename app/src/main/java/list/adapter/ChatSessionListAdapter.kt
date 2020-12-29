package list.adapter

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Picture
import android.opengl.Visibility
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tapin.R
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import database.ChatSessionRecyclerData
import ktorClient.secureAvatarURL


/**
 * Custom list adapter to use for the Chat Session recycler view.
 * Will display all of the current chat sessions in the database.
 *
 * @property context : context of the application state
 * @property recyclerViewClickListener : listener for clicks on recycler items @see[TapInRecyclerViewClickListener]
 * @property recyclerViewLongPressListener : listener for long presses on recycler items @see[TapInRecyclerViewLongPressListener]
 */
class ChatSessionListAdapter internal constructor(
    private val context: Context, private val recyclerViewClickListener: TapInRecyclerViewClickListener, private val recyclerViewLongPressListener: TapInRecyclerViewLongPressListener
) : RecyclerView.Adapter<ChatSessionListAdapter.ChatSessionViewHolder>(){
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var displayChatSessions = emptyList<ChatSessionRecyclerData>()
    private val noMessagesToDisplay = HtmlCompat.fromHtml("<i>No Messages To Display Yet</i>", HtmlCompat.FROM_HTML_MODE_LEGACY)
    private val fromYou = "<i>You: </i>"
    private val imageText = "<i>Image: </i>"

    /**
     * Inner class to control chat session view holder.
     * Will format each view item
     * @constructor
     *
     *
     * @param itemView : view for item
     * @param clickListener : click listener for item
     * @param longPressListener : long press listener for item
     */
    inner class ChatSessionViewHolder(itemView: View, clickListener: TapInRecyclerViewClickListener, longPressListener: TapInRecyclerViewLongPressListener) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener{
        val contactName: TextView = itemView.findViewById(R.id.contactName)
        val lastMessage: TextView = itemView.findViewById(R.id.lastMessage)
        val timeStamp: TextView = itemView.findViewById(R.id.timeStamp)
        val dateStamp: TextView = itemView.findViewById(R.id.dateStamp)
        val contactImage: ImageView = itemView.findViewById(R.id.contactImage)
        val silentIcon: ImageView = itemView.findViewById(R.id.silenced_image_view)
        var groupJID = ""
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
                myClickListener.onClick(view = v, groupJID , displayName)
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
                myLongPressListener.onLongPress(view = v, groupJID , displayName)
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
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatSessionViewHolder {
        val itemView = inflater.inflate(R.layout.recycler_view_item_chatsession, parent, false)
        return ChatSessionViewHolder(itemView, recyclerViewClickListener, recyclerViewLongPressListener)
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
    override fun onBindViewHolder(holder: ChatSessionViewHolder, position: Int) {
        val current = displayChatSessions[position]

        holder.groupJID = current.groupID
        holder.displayName = current.groupName
        holder.contactName.text = current.groupName

        if(current.messageBody.isNullOrEmpty()){
            holder.lastMessage.text = noMessagesToDisplay
//            holder.lastMessage.setTypeface(null, Typeface.ITALIC)
            holder.dateStamp.text = current.dateCreated
        }
        else{
            val messageToDisplayFromYou = HtmlCompat.fromHtml("$fromYou${current.messageBody}", HtmlCompat.FROM_HTML_MODE_LEGACY)
            if(!current.isMediaMessage){
                if(current.isOutgoing) holder.lastMessage.text = messageToDisplayFromYou else holder.lastMessage.text = current.messageBody
                holder.timeStamp.text = current.time
                holder.dateStamp.text = current.date
            }
            else {
                val fromYouImageText = HtmlCompat.fromHtml("$fromYou$imageText", HtmlCompat.FROM_HTML_MODE_LEGACY)
                val toYouImageText = HtmlCompat.fromHtml(imageText, HtmlCompat.FROM_HTML_MODE_LEGACY)
                if(current.isOutgoing) holder.lastMessage.text = fromYouImageText else holder.lastMessage.text = toYouImageText
                holder.timeStamp.text = current.time
                holder.dateStamp.text = current.date
            }

        }
        val isAllGroupChatsSilenced = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("silenceAllGroupChats", false)

        if(current.isSilenced || (current.isGroupChat && isAllGroupChatsSilenced)) holder.silentIcon.isVisible = true

        if(!current.isGroupChat){
//            val bmp = BitmapFactory.decodeByteArray(current.contactAvatar, 0, current.contactAvatar.size)
//            holder.contactImage.setImageBitmap(bmp)
            val contactUsername = current.groupID.substringBefore('@')
            Picasso.get().load("$secureAvatarURL$contactUsername".toUri()).error(R.drawable.person_def_avatar).placeholder(R.drawable.person_def_avatar).into(holder.contactImage)
        }
        else{
            //get default image
//            if(!current.isGroupChat) holder.contactImage.setImageResource(R.drawable.person_def_avatar)
//            else holder.contactImage.setImageResource(R.drawable.people_def_avatar)
            val chatRoomUsername = current.groupID.substringBefore('@')
            Picasso.get().load("$secureAvatarURL$chatRoomUsername".toUri()).error(R.drawable.people_def_avatar).placeholder(R.drawable.people_def_avatar).into(holder.contactImage)
        }


    }

    /**
     * Internal function to set the chat session data to display.
     *
     * @param displayChatSessions : List of ChatSessionRecyclerData data class
     */
    internal fun setChatSessionData(displayChatSessions: List<ChatSessionRecyclerData>){
        this.displayChatSessions = displayChatSessions
        //println("Data is $displayChatSessions")
        notifyDataSetChanged()
    }

    /**
     * Get the size of the chat session list
     *
     */
    override fun getItemCount() =  displayChatSessions.size //1 for testing

}