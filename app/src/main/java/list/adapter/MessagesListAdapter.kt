package list.adapter

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.view.LayoutInflater
import android.widget.CheckedTextView
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tapin.R
import com.squareup.picasso.Picasso
import database.DisplayMessagesData

/**
 * Custom class to populate the recycler view to display the messages
 * for the current chat session. Will format the message view depending on
 * whether the message is incoming or outgoing, read, sent received, or encrypted.
 *
 * @property imageClickListener : listener for clicks on media messages. @see [MessagePictureClickListener]
 * @constructor
 *
 *
 * @param context : application context (any form)
 */
class MessagesListAdapter internal constructor(
    context: Context, private val imageClickListener: MessagePictureClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>()  {

    // variables for resources (icons) and setup
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var displayMessagesData = emptyList<DisplayMessagesData>()
    private val USER_MESSAGE = 0
    private val CONTACT_MESSAGE = 1
    private val sentCheckMarkNoEncryption = R.drawable.ic_sent_no_encryption
    private val receivedCheckMarkNoEncryption = R.drawable.ic_recieved_no_encryption
    private val readCheckMarkNoEncryption = R.drawable.ic_read_no_encryption
    private val sentCheckMarkEncryption = R.drawable.ic_sent_encrypted
    private val receivedCheckMarkEncryption = R.drawable.ic_recieved_encrypted
    private val readCheckMarkEncryption = R.drawable.ic_read_encrypted
    private val fontSize = PreferenceManager.getDefaultSharedPreferences(context).getString("messageFontSize", "18.0")?.toFloat()
    private val messageNoEncryptedLock = R.drawable.ic_no_encryption
    private val messageEncryptedLock = R.drawable.ic_lock

    /**
     * Inner class to handle the view holder for an Incoming Message (Contact Message)
     *
     * @property contactImageClickListener : image click listener for media messages
     * @constructor
     *
     *
     * @param itemView : the view to manager and format
     */
    inner class ContactMessageViewHolder (itemView: View, private val contactImageClickListener: MessagePictureClickListener) : RecyclerView.ViewHolder(itemView){
        val contactMessageCheckedTextView: CheckedTextView = itemView.findViewById(R.id.contactMessageTextView)
        val contactMessageInfo: TextView = itemView.findViewById(R.id.incomingMessageInfo)
        val contactImageMessage: ImageView = itemView.findViewById(R.id.incoming_message_image)
        val messageSentFrom: TextView = itemView.findViewById(R.id.incoming_message_from)
        var messageUri = ""
        private val onContactImageClickListener = View.OnClickListener {
            contactImageClickListener.onImageClickedWithUri(messageUri)
        }
        init {
            contactImageMessage.setOnClickListener(onContactImageClickListener)
            if (fontSize != null) {
                contactMessageCheckedTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
                contactMessageInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
                messageSentFrom.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
            }

        }



    }

    /**
     * Inner class to handle the view holder for an Outgoing Message (User Message)
     *
     * @constructor
     *
     *
     * @param itemView : view to format
     * @param userImageClickListener : image lick listener for user media messages
     */
    inner class UserMessageViewHolder (itemView: View, userImageClickListener: MessagePictureClickListener) :RecyclerView.ViewHolder(itemView){
        val userMessageCheckedTextView :CheckedTextView = itemView.findViewById(R.id.userMessageTextView)
        val userMessageInfo :TextView = itemView.findViewById(R.id.outgoingMessageInfo)
        val userImageMessage :ImageView = itemView.findViewById(R.id.outgoing_message_image)
        var messageUri = ""
        private val myImageListenerFunction = userImageClickListener
        private val onUserImageClickListener = View.OnClickListener {
            myImageListenerFunction.onImageClicked(userImageMessage.drawable)
            myImageListenerFunction.onImageClickedWithUri(messageUri)
        }
        init {
            userImageMessage.setOnClickListener(onUserImageClickListener)
            if (fontSize != null) {
                userMessageCheckedTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
                userMessageInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
            }

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
     * @param viewType The view type of the new View. Either a contact (incoming) or user (outgoing) message
     *
     * @return A new ViewHolder that holds a View of the given view type.
     * @see .getItemViewType
     * @see .onBindViewHolder
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if(viewType == USER_MESSAGE){
            val itemView = inflater.inflate(R.layout.outgoing_chat_bubble, parent, false)
            UserMessageViewHolder(itemView, imageClickListener)
        } else{
            val itemView = inflater.inflate(R.layout.incoming_chat_bubble, parent, false)
            ContactMessageViewHolder(itemView, imageClickListener)
        }
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
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentMessage = displayMessagesData[position]

        if(getItemViewType(position) == USER_MESSAGE){

            val messageViewHolder = UserMessageViewHolder(holder.itemView, imageClickListener)
            if(currentMessage.isMediaMessage){
                messageViewHolder.userImageMessage.isVisible = true
                messageViewHolder.messageUri = currentMessage.messageBody
                Picasso.get().load(currentMessage.messageBody.toUri()).into(messageViewHolder.userImageMessage)
                //messageViewHolder.userImageMessage.setImageURI(currentMessage.messageBody.toUri())
                messageViewHolder.userMessageCheckedTextView.isGone = true
            }
            else {
                messageViewHolder.userMessageCheckedTextView.isVisible = true
                messageViewHolder.userMessageCheckedTextView.text = currentMessage.messageBody
                messageViewHolder.userImageMessage.isGone = true
                messageViewHolder.userMessageCheckedTextView.compoundDrawablePadding = 10

            }

            messageViewHolder.userMessageInfo.text = currentMessage.date
            messageViewHolder.userMessageInfo.append(" ${currentMessage.time}")

            if(currentMessage.isSent && !currentMessage.isReceived && !currentMessage.isRead){
                if(currentMessage.isEncrypted)
                    messageViewHolder.userMessageCheckedTextView.setCheckMarkDrawable(sentCheckMarkEncryption)
                else messageViewHolder.userMessageCheckedTextView.setCheckMarkDrawable(sentCheckMarkNoEncryption)
            }
            else if(currentMessage.isReceived && !currentMessage.isRead){
                if(currentMessage.isEncrypted)
                    messageViewHolder.userMessageCheckedTextView.setCheckMarkDrawable(receivedCheckMarkEncryption)
                else messageViewHolder.userMessageCheckedTextView.setCheckMarkDrawable(receivedCheckMarkNoEncryption)

            }
            else if(currentMessage.isRead){
                if(currentMessage.isEncrypted)
                    messageViewHolder.userMessageCheckedTextView.setCheckMarkDrawable(readCheckMarkEncryption)
                else messageViewHolder.userMessageCheckedTextView.setCheckMarkDrawable(readCheckMarkNoEncryption)
            }

        }
        else{

            val contactMessageViewHolder = ContactMessageViewHolder(holder.itemView, imageClickListener)
            if(currentMessage.isMediaMessage){
                contactMessageViewHolder.messageUri = currentMessage.messageBody
                contactMessageViewHolder.contactImageMessage.isVisible = true
                Picasso.get().load(currentMessage.messageBody.toUri()).into(contactMessageViewHolder.contactImageMessage)
                contactMessageViewHolder.contactMessageCheckedTextView.isGone = true
            }
            else {
                contactMessageViewHolder.contactMessageCheckedTextView.isVisible = true
                contactMessageViewHolder.contactMessageCheckedTextView.text = currentMessage.messageBody
                contactMessageViewHolder.contactImageMessage.isGone = true
                if(currentMessage.isEncrypted){
                    contactMessageViewHolder.contactMessageCheckedTextView.setCheckMarkDrawable(messageEncryptedLock)
                }
                else contactMessageViewHolder.contactMessageCheckedTextView.setCheckMarkDrawable(messageNoEncryptedLock)
            }

            contactMessageViewHolder.contactMessageInfo.text = currentMessage.date
            contactMessageViewHolder.contactMessageInfo.append(" ${currentMessage.time}")

            if(currentMessage.isGroupChat){
                contactMessageViewHolder.messageSentFrom.text = currentMessage.contactName
            }

        }
    }

    /**
     * Determine if the current message is a user message (outgoin) or contact message (incoming)
     *
     * @param position : poistion of the current item in the @see[displayMessagesData]
     * @return : 0 for user message, 1 for contact message. @see[USER_MESSAGE] @see[CONTACT_MESSAGE]
     */
    override fun getItemViewType(position: Int): Int {
        return if(displayMessagesData[position].isIncoming) CONTACT_MESSAGE
        else USER_MESSAGE
    }

    /**
     * Internal function to set the display message list.
     * Needed for LiveData since it is not immediately available (accessed synchronously)
     * @see [displayMessagesData]
     *
     * @param displayMessagesData : List<DisplayMessagesData> @see[DisplayMessagesData]
     */
    internal fun setDisplayMessagesData(displayMessagesData: List<DisplayMessagesData>){
        this.displayMessagesData = displayMessagesData
        notifyDataSetChanged()
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     *
     * @return The total number of items in this adapter @see[displayMessagesData]
     */
    override fun getItemCount(): Int {
//        println("The stuff in messages is $displayMessagesData")
        return displayMessagesData.size
    }

}

