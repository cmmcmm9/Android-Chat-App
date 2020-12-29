package viewmodel

import android.app.Application
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.lifecycle.*
import com.squareup.picasso.Picasso
import database.DisplayMessagesData
import database.TapInDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ktorClient.KtorClient
import ktorClient.secureAvatarURL
import org.minidns.dnsserverlookup.android21.AndroidUsingLinkProperties
import repository.Repository
import xmpp.XmppTapIn
import java.io.File

/**
 * ViewModel class for the @see [in_chats.kt] displaying the messages for the selected chat.
 * Following the Google Recommended MVVM architecture.
 * Will handle all of the blocking calls (network or database)
 * in a separate Kotlin coroutine scope.
 *
 * @constructor
 * Initialize all of the variables, and set the messages to display as null (for now, needed by LiveData)
 *
 * @param application : the application context
 */
class MessageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: Repository
    private val database = TapInDatabase.getDatabase(application, viewModelScope)
    private val xmppTapIn: XmppTapIn
    var messagesToDisplay: LiveData<List<DisplayMessagesData>>
    lateinit var contactDisplayNames: LiveData<List<String>>

    init {
        AndroidUsingLinkProperties.setup(application)
        repository = Repository(
            userDao = database.userDao(),
            userSettingsDao = database.userSettingsDao(),
            userAndUserSettingsDao  = database.userAndUserSettingsDao(),
            userStatusDao = database.userStatusDao(),
            userAndUserStatusDao = database.userAndUserStatusDao(),
            userTimeAvailableDao = database.userTimeAvailableDao(),
            userAndUserTimeAvailDao = database.userAndUserTimeAvailDao(),
            chatSessionDao = database.chatSessionDao(),
            userAndChatSessionDao = database.userAndChatSessionDao(),
            chatSessionAndMessagesDao = database.chatSessionAndMessagesDao(),
            groupChatAndChatSessioDao = database.groupChatAndChatSessioDao(),
            messagesDao = database.messagesDao(),
            messageStatusDao = database.messageStatusDao(),
            groupChatDao = database.groupChatDao(),
            contactAndGroupChatDao = database.contactAndGroupChatDao(),
            contactDao = database.contactDao(),
            contactAndContactTimeAvailDao = database.contactAndContactTimeAvailDao(),
            contactTimeAvailableDao = database.contactTimeAvailableDao(),
            contactAndContactStatusDao = database.contactAndContactStatusDao(),
            contactStatusDao = database.contactStatusDao()
        )
        xmppTapIn = XmppTapIn.getXmppTapIn(repository, application)
        messagesToDisplay = repository.getMessagesToDisplay("", "", false)
//        viewModelScope.launch(Dispatchers.IO) {
//            contactDisplayNames = repository.getContactDisplayNamesForChat("")
//        }

    }

    /**
     * Function to delete a chat session, and any messages associated with it.
     *
     * @param groupJID : The groupJID of the ChatSession
     */
    fun deleteChatCascade(groupJID: String) {
        viewModelScope.launch(Dispatchers.IO){
            repository.deleteGroupChatCascade(groupJID)
        }
    }

    /**
     * Function to update the silenced status of a chat.
     *
     * @param isSilenced : whether to silence or not (true or false)
     * @param groupJID : groupJID of the ChatSession.
     */
    fun updateSilenceGroupChat(isSilenced: Boolean, groupJID: String){
        viewModelScope.launch(Dispatchers.IO){
            repository.updateSilenceGroupChat(isSilenced, groupJID)
        }
    }

    /**
     * Function to set the LiveData for the contact's display names @see[contactDisplayNames] for the "Display Members" Dialog.
     * Initially set to null until the in_chats.kt activity is created.
     * @param groupJID : groupJID of the ChatSession
     */
    fun setContactDisplayNames(groupJID: String){
        viewModelScope.launch(Dispatchers.IO){ contactDisplayNames = repository.getContactDisplayNamesForChat(groupJID)
        }

    }

    /**
     * Function to set the LiveData for all of the messages to display for the
     * particular ChatSession. @see [messagesToDisplay]
     *
     * @param groupJID : the groupJID of the ChatSession
     * @param userUID : the current firebase user's UID
     * @param isGroupChat : boolean for whether or not this is a group chat
     */
    fun setMessagesToDisplay(groupJID: String, userUID: String, isGroupChat: Boolean?){
        messagesToDisplay = repository.getMessagesToDisplay(groupJID, userUID, isGroupChat)

    }

    /**
     * Function to upload an Avatar for a group chat. Request is made by @see [KtorClient.uploadAvatar]
     * to my Ktor Server which will save the file to the directory, and respond the
     * Avatar with a correct GET request so all group members can see the Avatar.
     * Note: only Room creators can upload an avatar for a group
     *
     * @param uploadFiles : Map of the filename (the groupJID) to the file to use for the Avatar.
     * @param text : Map of the text to send with the HTTP multiformdata. Used by Ktor server. MUST be set for group chats.
     * @param imageView : The image view in the ChatSession to update with the new avatar
     * @param groupJID : groupJID of the group chat
     */
    fun uploadAvatarForGroupChat(uploadFiles: Map<String, File>, text: Map<String, String>, imageView: ImageView, groupJID: String){
        viewModelScope.launch(Dispatchers.IO){
            KtorClient.uploadAvatar(uploadFiles, text)
            Picasso.get().invalidate("$secureAvatarURL$groupJID".toUri())
            viewModelScope.launch(Dispatchers.Main) {
                Picasso.get().load("$secureAvatarURL$groupJID".toUri()).into(imageView)
            }

        }
    }

    /**
     * Function to send an XMPP message
     *
     * @param contactJID : the contactJID or groupJID of the ChatSession. Will check to see if it is a group chat or single
     * @param messageBody : the message to send
     */
    fun sendMessage(contactJID: String, messageBody: String){
        viewModelScope.launch(Dispatchers.IO) {
            if(repository.isGroupChat(contactJID)){
                xmppTapIn.sendMUCMessage(groupJID = contactJID, messageBody, false)
            }
            else xmppTapIn.sendMessage(contactJID, messageBody, false)

        }

    }

    /**
     * Function to send a file to the respective sender.
     *
     * @param groupJID : the groupJID of the chat session.
     * @param fileToSend : the file to send
     * @param isGroupChat : whether or not this is a groupChat
     */
    fun sendFile(groupJID: String, fileToSend: File, isGroupChat: Boolean?){
        if(isGroupChat == null) return
        viewModelScope.launch(Dispatchers.IO){
            xmppTapIn.sendFile(mediaFile = fileToSend, groupJID, isGroupChat)
        }
    }

    /**
     * Function to send the typing status of the user to the respective chat session.
     * The user can choose not to send this, and this will be determined in @see [XmppTapIn.sendTypingState].
     *
     * @param contactJID : the contactJID or groupJID of the Chat Session
     * @param isGroupChat : whether or not the chat is a group chat
     */
    fun sendTypingStatus(contactJID: String, isGroupChat: Boolean?){
        viewModelScope.launch(Dispatchers.IO){
            if (isGroupChat != null) {
                xmppTapIn.sendTypingState(contactJID, isGroupChat)
            }
        }
    }

    /**
     * Function to send the paused status of the user to the respective chat session (they stopped typing).
     *
     * @param contactJID : the contactJID or groupJID of the Chat Session
     * @param isGroupChat : whether or not the chat is a group chat
     */
    fun sendPausedState(contactJID: String, isGroupChat: Boolean?){
        viewModelScope.launch(Dispatchers.IO){
            if (isGroupChat != null) {
                xmppTapIn.sendPausedState(contactJID, isGroupChat)
            }
        }
    }
    /**
     * Function to send the active status of the user to the respective chat session (they stopped opened the chat).
     * This will send a 'Read' status to the sender that the user has read their message.
     * The user can choose not to send this, and this will be determined in @see [XmppTapIn.sendTypingState].
     * @param contactJID : the contactJID or groupJID of the Chat Session
     * @param isGroupChat : whether or not the chat is a group chat
     */
    fun sendActiveState(contactJID: String, isGroupChat: Boolean?){
        viewModelScope.launch(Dispatchers.IO){
            if (isGroupChat != null) {
                xmppTapIn.sendActiveState(contactJID, isGroupChat)
            }
            repository.updateMessageIsReadIncoming(isRead = true, contactJID)
        }
    }

    /**
     * Function to send the gone status of the user to the respective chat session (they closed the chat).
     * This will send a 'Gone' status to the sender that the user has read left the chat.
     *
     * @param contactJID : the contactJID or groupJID of the Chat Session
     * @param isGroupChat : whether or not the chat is a group chat
     */
    fun sendGoneState(contactJID: String, isGroupChat: Boolean?){
        viewModelScope.launch(Dispatchers.IO){
            if (isGroupChat != null) {
                xmppTapIn.sendGoneState(contactJID, isGroupChat)
            }
        }
    }
}