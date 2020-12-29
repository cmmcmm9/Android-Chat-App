package viewmodel

import xmpp.XmppTapIn
import android.app.Application
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.tapin.R
import com.google.firebase.auth.FirebaseAuth
import com.squareup.picasso.Picasso
import database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ktorClient.ContactFetcher
import ktorClient.KtorClient
import ktorClient.secureAvatarURL
import org.minidns.dnsserverlookup.android21.AndroidUsingLinkProperties
import repository.Repository
import java.io.File

/**
 * ViewModel Used throughout the application, except for the in_chats activity.
 * Following the Google Recommended MVVM architecture.
 * Will handle all of the blocking calls (network or database)
 * in a separate Kotlin coroutine scope. Handles loggin in, updating user information, registration,
 * and contact syncing.
 * @constructor
 * Initialize the repository variable, and set the LiveData @see [displayedChatSession] and @see [displayContacts]
 *
 * @param application : application context
 */
class TapInViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: Repository
    private val database = TapInDatabase.getDatabase(application, viewModelScope)
    val displayedChatSession: LiveData<List<ChatSessionRecyclerData>>
    val displayContacts: LiveData<List<DisplayContactsRecyclerData>>
    private val xmppTapIn: XmppTapIn

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
//        inActivityDetector = XmppTapIn.InactivtyDetecter
//        inActivityDetector.xmppTapIn = xmppTapIn
        displayedChatSession = repository.displayedChatSession
        displayContacts = repository.displayContacts

    }

//    fun insert(word: Word) = viewModelScope.launch(Dispatchers.IO){
//        repository.insert(word)
//    }

    /**
     *
     * Function to go through the process of registering a new user.
     * It will create the user in the local database, send a POST request to my
     * Ktor server to register the user in Openfire. Will also sync all of the user's
     * contacts in their phone (based on phone numbers) with current Openfire users.
     * If a match is found, it will add the contact to the user's roster.
     *
     * @param user : User dataclass filled out with the User's information @see []
     * @param idToken : Firebase Token of the user, used by Ktor to authenticate GET and POST requests.
     * @param application : application context to retrieve the user's contact.
     */
    fun registerNewUser(user: User, idToken: String, application: Application) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertNewUser(user)
        KtorClient.registerUserOpenfire(idToken)
        syncContacts(user.userJID, idToken, application)
    }

    /**
     * Function to sync all of the user's contacts in their phone
     * (based on phone numbers) with current Openfire users. Called by @see [registerNewUser]
     * and when a user requests a 'Swipe Refresh' in the DisplayContactsActivity.
     * If a match is found, it will add the contact to the user's roster.
     *
     * @param uid : Firebase UID of the current user
     * @param idToken : Firebase Token of the user, used by Ktor to authenticate GET and POST requests.
     * @param application : application context to retrieve the user's contact.
     */
    fun syncContacts(uid: String, idToken: String, application: Application) = viewModelScope.launch(Dispatchers.IO) {
        val contactPhoneNumbers = ContactFetcher.getPhoneNumbers(application)
        println("The contact fetcher got $contactPhoneNumbers")
        KtorClient.contactSync(uid, idToken, contactPhoneNumbers)
        if(xmppTapIn.connected){
            xmppTapIn.syncRoster()
        }

    }

    /*
    Test function, not used.
     */
    fun getUser() :LiveData<User> = repository.user

    /*
    Test function, not used.
    */
    fun getHello() = viewModelScope.launch { KtorClient.getHello() }

    /**
     * Function to login the user into Openfire.
     *
     * @param username : firebaseUID of the current user
     * @param authToken : Firebase ID Token or Auth Token to verify the user (JWT).
     */
    fun loginXmpp(username: String, authToken: String?) = viewModelScope.launch(Dispatchers.IO){
        xmppTapIn.connectAndLogin(username, authToken, sendPresence = true) }

    fun logoutXmpp() = viewModelScope.launch { xmppTapIn.disconnect() }

    /**
     * Function to update the user's Vcard stored in Openfire. Will trigger a Firebase message
     * by my Ktor server to tell all of the user's contacts to update the user's information stored
     * the local database.
     *
     */
    fun updateVcard() = viewModelScope.launch(Dispatchers.IO) {
        if(FirebaseAuth.getInstance().currentUser?.uid != null){
            val userUID = FirebaseAuth.getInstance().currentUser?.uid.toString()
            println("The value is userUID is $userUID")
            println("Current user is ${FirebaseAuth.getInstance().currentUser?.displayName}")
//            repository.getUserByUID(userUID)?.let { xmppTapIn.setUserVCard(it) }
            xmppTapIn.setUserVCard(repository.getUserByUID(userUID)!!)
        }

    }

    /**
     * Function to upload an Avatar for the user. Request is made by @see [KtorClient.uploadAvatar]
     * to my Ktor Server which will save the file to the directory, and respond the
     * Avatar with a correct GET request so all contacts can see the Avatar.
     *
     * @param uploadFiles : Map of the filename (the user's firebase UID) to the file to use for the Avatar.
     * @param text : Map of the text to send with the HTTP multiform data.
     * @param imageView : The image view in ViewProfile to update with the new avatar
     * @param userUID : firebase UID of the current user
     */
    fun uploadAvatar(uploadFiles: Map<String, File>, text: Map<String, String>, imageView: ImageView, userUID: String){
        viewModelScope.launch(Dispatchers.IO){
            KtorClient.uploadAvatar(uploadFiles, text)
            Picasso.get().invalidate("$secureAvatarURL$userUID".toUri())
            viewModelScope.launch(Dispatchers.Main) {
                Picasso.get().load("$secureAvatarURL$userUID".toUri()).error(R.drawable.person_def_avatar).placeholder(R.drawable.person_def_avatar).into(imageView)
            }



        }
    }

    /**
     * Function to delete a contact in the database. It use the repository class
     * to handle the database call. @see [Repository.deleteContact]
     *
     * @param contactJID : the JID of the contact to be deleted.
     */
    fun deleteContact(contactJID: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteContact(contactJID)
    }
    //fun deleteAllContact() = viewModelScope.launch(Dispatchers.IO) { repository.deleteAllContact() }

//    fun setUserInputActive() {
//        inActivityDetector.scheduleTask()
//        inActivityDetector.isActive = true
//    }

//    fun displayContactsClicked(contactJID: String){
//        viewModelScope.launch {
//            val doesChatSessionExists = repository.doesChatSessionExist(contactJID)
//
//            if(doesChatSessionExists)
//        }
//    }

//    fun fixOccupants(listOccupants: List<String>, groupJID: String){
//        viewModelScope.launch(Dispatchers.IO){
//            repository.fixGroupChatOccupants(listOccupants, groupJID)
//        }
//    }

    fun testDoesChatExist(contactJID: String) = viewModelScope.launch(Dispatchers.IO) {
        println("The does chat exist returned ${repository.doesChatSessionExist(contactJID)}")
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
     * Function to create a groupChat. @see [XmppTapIn.createNewMUCRoom]
     *
     * @param contactJIDlist : list of the contactJID's to include in the groupchat
     * @param groupName : the name to use to create the group. All user's will see this name.
     * @param groupJID : the groupJID to use for the group, it is randomly generated.
     */
    fun createGroupChat(contactJIDlist: List<String>, groupName: String, groupJID: String){
        viewModelScope.launch(Dispatchers.IO) {
            xmppTapIn.createNewMUCRoom(contactJIDlist, groupName, groupJID)
        }
    }


}