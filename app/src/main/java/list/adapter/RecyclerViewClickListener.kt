package list.adapter

import android.graphics.drawable.Drawable
import android.view.View

/**
 * Interface for click listeners in a recycler view.
 * Used in @see[ChatSessionListAdapter] , @see[DisplayContactsListAdapter]
 *
 */
interface TapInRecyclerViewClickListener {

    /**
     * Handle click event
     *
     * @param view : view being clicked
     * @param contactJID : contactJID of the view being clicked
     * @param displayName : display Name of the view being clicked
     */
    fun onClick(view: View, contactJID: String, displayName: String)
}

/**
 * Interface for a "long press" listener used in the recycler view.
 * Used in @see[DisplayContactsListAdapter] @see[ChatSessionListAdapter]
 */
interface TapInRecyclerViewLongPressListener {
    /**
     * Handle long press event
     *
     * @param view : view being long pressed
     * @param contactJID : contactJID of the view being clicked
     * @param displayName : display Name of the view being clicked
     */
    fun onLongPress(view: View, contactJID: String, displayName: String)
}

/**
 * Interface used when a media message is clicked.
 *
 *
 */
interface MessagePictureClickListener {
    /**
     * Handle a message being clicked, given its drawable resource file
     *
     * @param drawable : drawable resource containing the image
     */
    fun onImageClicked(drawable: Drawable)

    /**
     * Handle the media message being clicked, given its URI
     *
     * @param messagesUri : uri of he message on the backend server
     */
    fun onImageClickedWithUri(messagesUri: String)
}