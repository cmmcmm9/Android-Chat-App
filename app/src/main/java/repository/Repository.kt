package repository

import androidx.lifecycle.LiveData
import database.*
import kotlinx.coroutines.*


/**
 * Repository class to handle all of the database calls.
 * Following the MVMM app architecture recommended by Google.
 *
 * @property userDao
 * @property userSettingsDao
 * @property userAndUserSettingsDao
 * @property userStatusDao
 * @property userAndUserStatusDao
 * @property userTimeAvailableDao
 * @property userAndUserTimeAvailDao
 * @property chatSessionDao
 * @property userAndChatSessionDao
 * @property chatSessionAndMessagesDao
 * @property groupChatAndChatSessioDao
 * @property messagesDao
 * @property messageStatusDao
 * @property groupChatDao
 * @property contactAndGroupChatDao
 * @property contactDao
 * @property contactAndContactTimeAvailDao
 * @property contactTimeAvailableDao
 * @property contactAndContactStatusDao
 * @property contactStatusDao
 */
class Repository(
    private val userDao: UserDao,
    private val userSettingsDao: UserSettingsDao,
    private val userAndUserSettingsDao : UserandUserSettingsDao,
    private val userStatusDao: UserStatusDao,
    private val userAndUserStatusDao: UserAndUserStatusDao,
    private val userTimeAvailableDao: UserTimeAvailableDao,
    private val userAndUserTimeAvailDao: UserAndUserTimeAvailDao,
    private val chatSessionDao: ChatSessionDao,
    private val userAndChatSessionDao: UserAndChatSessionDao,
    private val chatSessionAndMessagesDao: ChatSessionAndMessagesDao,
    private val groupChatAndChatSessioDao: GroupChatandChatSessionDao,
    private val messagesDao: MessagesDao,
    private val messageStatusDao: MessageStatusDao,
    private val groupChatDao: GroupChatDao,
    private val contactAndGroupChatDao: ContactAndGroupChatDao,
    private val contactDao: ContactDao,
    private val contactAndContactTimeAvailDao: ContactAndContactTimeAvailDao,
    private val contactTimeAvailableDao: ContactTimeAvailableDao,
    private val contactAndContactStatusDao: ContactAndContactStatusDao,
    private val contactStatusDao: ContactStatusDao

){
//    private lateinit var messagesData: LiveData<List<DisplayMessagesData>>
    val user = userDao.getUser()
    //val chatSession = chatSessionDao.getAllChatSessions()
    val displayedChatSession = chatSessionDao.getChatSessionsForRecycler()
    val displayContacts = contactDao.getDisplayContacts(TimeDateManager.getDayOfWeek())
    val contacts = contactDao.getAllContacts()
    private val repositoryScope = CoroutineScope(Job() + Dispatchers.IO)

    /**
     * Function to insert a new user into the local database
     * as well as set up all of the needed user information in other tables.
     *
     * @param user
     */
    suspend fun insertNewUser(user: User){


        userDao.insertUser(user)
        userSettingsDao.newUserSettings(user)
        userTimeAvailableDao.newUserTimeAvail(user)
        userStatusDao.insertUserStatus(
            UserStatus(
                rowId = 0,
                userJID = user.userJID,
                isAvailable = false,
                isOnline = false,
                isTyping = false,
                lastOnlineDate = TimeDateManager.getDate(),
                lastOnlineTime = TimeDateManager.getTime()
        ))
        //xmppTapIn.setUserVCard(userAndUserTimeAvailDao.getUserAndUserTimeAvail())
    }

    /**
     * Function to get the User dataclass based on their FirebaseUID.
     *
     * @param firebaseUID the firebase UID of the user
     * @return User? : the User data class corresponding to the supplied firebase UID or null
     */
    fun getUserByUID(firebaseUID: String?) : User? = firebaseUID?.let { userDao.getUserByUID(it) }


    /**
     * Function to determine whether or not a Contact exists in the local database.
     *
     * @param contactJID : the contactJID of the user to query
     * @return boolean : true if the user exists, else false
     */
    suspend fun checkIfContactExists(contactJID: String?) :Boolean {
        return !contactDao.doesContactExist(contactJID).isNullOrEmpty()
    }

    /**
     * Function to get a list of all the contactsJIDs in the local database
     *
     * @return list<contactJIDs> : all list of all the contactJIDS
     */
    suspend fun getAllContactJIDS() :List<String> = contactDao.getAllContactsJIDs()

    /**
     * Function to get the display name for a given ChatSession.
     *
     * @param groupJID : groupJID of the ChatSession
     * @return ChatSessionDisplayName : the name of the Chat Session
     */
    fun getChatSessionDisplayName(groupJID: String) :String {
        return groupChatDao.getChatSessionDisplayName(groupJID)
    }

    /**
     * Function to get the display names for all occupants for a given ChatSession.
     *
     * @param groupJID : groupJID of the ChatSession
     * @return LiveData List: LiveData List of all group members Display Names
     */
    suspend fun getContactDisplayNamesForChat(groupJID: String) :LiveData<List<String>>{
        val occupants = groupChatDao.getOccupantsInGroupChatAsList(groupJID)
//        val testList = listOf("9kyacnywsfzopac9sz5edrkht6m1@tapinapp.com", "mudafrahgbanlioyml2mywesbhf3@tapinapp.com")
//        println("Occupants in reposttory is $occupants and size is ${occupants.size}")
//        println("Non query is $testList and size is ${testList.size}")
//        val test1 = contactDao.getContactDisplayNamesForChatTest(occupants)
//        println("Non-live data got $test1")
        if(!occupants.isNullOrEmpty() && occupants != null){
            return contactDao.getContactDisplayNamesForChat(occupants.toList())
        }
        return contactDao.getContactDisplayNamesForChat(listOf())

    }

    /**
     * Function to get a single Contact from the database
     *
     * @param contactJID : the contactJID of the contact to query
     * @return Contact: the contact as a Contact data class object
     */
    suspend fun getContact(contactJID: String) :Contact = contactDao.getSingleContact(contactJID)

    suspend fun getContactTimeAvailable(contactJID: String) :List<ContactTimeAvailable> = contactTimeAvailableDao.getContactAvailableTime(contactJID)

    /**
     * Function to return all of the Unique Row IDS of a contact's available times so it can be updated.
     *
     * @param contactJID : the contactJID of the contact to query
     * @return List<RowIDS> : a list of the rowIDS from the ContactAvailableTime table
     */
    suspend fun getContactTimeAvailableRowIDs(contactJID: String) :List<Int> = contactTimeAvailableDao.getRowIDSForContactAvailableTime(contactJID)

    /**
     * Function to insert a new contact, and their respective information.
     *
     * @param contact : contact data class
     * @param contactTimeAvailable : contact Time Available data class
     * @param contactStatus : contact status data class
     */
    suspend fun insertNewContacts(contact: Contact, contactTimeAvailable: List<ContactTimeAvailable>, contactStatus: ContactStatus){

        val x = contactDao.insertContact(contact)
        println("Inserting Contact rowID is $x")
        contactTimeAvailable.forEach{
            val y = contactTimeAvailableDao.insertContactTimeAvail(it)
            println("Inserting Contact Time availbe rowID is $y")
        }
        val z = contactStatusDao.insertContactStatus(contactStatus)
        println("Contact Status insertion row id is $z")
    }

    /**
     * Function to update a contact's available time.
     *
     * @param contactTimeAvailable : contact Time Available data class
     */
    suspend fun updateContactTimeAvailable(contactTimeAvailable: List<ContactTimeAvailable>){
        contactTimeAvailable.forEach{
            contactTimeAvailableDao.updateContactTimeAvail(it)
        }
    }

    /**
     * Function to update a contact's information in the database.
     *
     * @param contact : contact data class
     */
    suspend fun updateContact(contact: Contact){
        contactDao.updateContact(contact)
    }

    /**
     * Function to update a contact's status in the database.
     *
     * @param contactStatus : contact status data class
     */
    suspend fun updateContactStatus(contactStatus: ContactStatus){
        contactStatusDao.updateContactStatus(contactStatus)
    }

    /**
     * Function to update a contact's public key stored in the local database
     *
     * @param contactJID : JID of the contact
     * @param publicKey : new public key for the contact (as string), should be retrieved from Firebase Database.
     */
    suspend fun updateContactPublicKey(contactJID: String, publicKey: String) = contactDao.updateContactPublicKey(contactJID, publicKey)

    /**
     * Get a contact's public key from the local database.
     *
     * @param contactJID : JID of the contact
     * @return string : public key of the user as a string
     */
    suspend fun getContactsPublicKey(contactJID: String) = contactDao.getContactsPublicKey(contactJID)

    /**
     * Delete a contact from the database.
     *
     * @param contactJID : The JID of the contact
     */
    suspend fun deleteContact(contactJID: String){
        contactDao.deleteSingleContact(contactJID)
        contactTimeAvailableDao.deleteSingleContactTimeAvailable(contactJID)
        contactStatusDao.deleteSingleContactStatus(contactJID)
    }

    /**
     * Get the available time for a user base on the day of the week
     *
     * @param contactJID : JID of the contact
     * @param dayOfTheWeek : current day of the week as an integer. Sunday = 1, Saturday = 7
     * @return updateContactHelper: Data class used holding the contact information
     */
    suspend fun getContactAvailableTimeForToday(contactJID: String, dayOfTheWeek: Int) :UpdateContactHelper{
        return contactStatusDao.getContactAvailableTime(contactJID, dayOfTheWeek)
    }

    /**
     * Determines if the chat session already exists in the database, based on the groupJID
     *
     * @param groupJID : JID of the group chat (or single chat, in this case contactJID) to check for
     * @return Int: returns the chat session ID if it exists, else null
     */
    suspend fun doesChatSessionExist(groupJID: String) :Int? {
        return chatSessionDao.checkIfChatExists(groupJID = groupJID)
    }

    /**
     * Updates the typing status for a chat session, whether someone is typing, and who is typing
     *
     * @param isContactTyping : boolean for status of contact Typing (true, false)
     * @param contactJID : JID of the contact typing
     * @param groupJID : groupJID of the chat session this typing status is referring to.
     */
    suspend fun updateTypingStatusForChat(isContactTyping: Boolean, contactJID: String, groupJID: String) = chatSessionDao.updateTypingStatusForChat(isContactTyping, contactJID, groupJID = groupJID)

    /**
     * Retrieve the chatsessionID for a given chat session.
     *
     * @param groupJID : groupJID of the chat session (or contactJID for one to one chat)
     * @return
     */
    suspend fun getChatSessionID(groupJID: String) :Int {
        return chatSessionDao.getChatSessionID(groupJID)
    }

    /**
     * Create a new chat session and await for the chatsessionID of the newly inserted chat session
     *
     * @param chatSession : ChatSession Data Class of the new chat session
     * @return Int : chatsessionID of the newly inserted chat session
     */
    private suspend fun createNewChatSessionWithID(chatSession: ChatSession): Long {
        val rowID = repositoryScope.async { chatSessionDao.insertChatSession(chatSession) }
        return rowID.await()
    }

    /**
     * Create a new chat, both the chatSession and groupChat.
     *
     * @param chatSession : ChatSession Data class of the new chat
     * @param groupChat : GroupChat data class of the new chat
     */
    suspend fun createNewChat(chatSession: ChatSession, groupChat: GroupChat){
        groupChatDao.insertGroupChat(groupChat)
        chatSessionDao.insertChatSession(chatSession)
    }

    /**
     * Create a new chat session and group chat instance for a new incoming message,
     * where a one does not exists already. Then, insert new incoming message (either group chat or single)
     *
     * @param chatSession : New ChatSession data class to insert
     * @param groupChat : New GroupChat data class to insert
     * @param message : the message received
     * @param messageStatus : the message status of the message received
     */
    suspend fun insertNewIncomingChatSession(chatSession: ChatSession, groupChat: GroupChat, message: Messages, messageStatus: MessageStatus){
        groupChatDao.insertGroupChat(groupChat)
        val chatID = createNewChatSessionWithID(chatSession)
        message.chatSessionID = chatID.toInt()
        messagesDao.insertMessage(message)
        messageStatusDao.insertMessageStatus(messageStatus)

    }

    /**
     * Delete a chat session and all of its corresponding information.
     * This includes the entry in the groupChat table, as well
     * as all of the messages and their corresponding message status.
     * This is permanent.
     * @param groupJID : groupJID of the chat session to delete. If single chat, contactJID
     */
    suspend fun deleteGroupChatCascade(groupJID: String) {
        groupChatDao.deleteGroupChat(groupJID)
        chatSessionDao.deleteChatSessionCascade(groupJID)
    }

    /**
     * Get all of the JIDs for all group chats (not a single chat)
     *
     * @return : List<String> : List of all of the group chat JIDs
     */
    suspend fun getAllGroupChatJIDS() :List<String> = groupChatDao.getAllGroupJIDs()

    /**
     * Get the last message from of group chat (MUC), if it exists.
     *
     * @param groupJID : JID of the chat session to get the last message from
     * @return Message? : Message data class if a message exists, or null if not
     */
    suspend fun getLastMessageFromMUC(groupJID: String) = messagesDao.getLastMessageFromMUC(groupJID)

    /**
     * Determine if a chat session is a group chat.
     *
     * @param groupJID : groupJID of the chat to query for
     * @return Boolean : true if this chat is a group chat (MUC) or false if not
     */
    fun isGroupChat(groupJID: String) :Boolean = groupChatDao.isGroupChat(groupJID)

    fun fixGroupChatOccupants(listOfOccupants: List<String>, groupJID: String) = groupChatDao.fixOccupants(listOfOccupants, groupJID)

    /**
     * Determine if a chat session is silenced.
     *
     * @param groupJID : the groupJID of the chat session to query
     * @return Boolean : true if the the chat session is a group chat, otherwise false
     */
    fun isChatSessionSilenced(groupJID: String) :Boolean = groupChatDao.isChatSessionSilenced(groupJID)

    /**
     * Update silencing of a chat session (group or single)
     * This is done if the user wishes to silence a particular chat session.
     *
     * @param isSilenced : New value for the chat session to be silenced
     * @param groupJID : groupJID of the chat session
     */
    suspend fun updateSilenceGroupChat(isSilenced: Boolean, groupJID: String) = groupChatDao.updateSilenceGroupChat(isSilenced, groupJID)

    /**
     * Insert a new message with its status, given it already has a valid chat session and group chat entry
     * in the database. Will not allow duplicate messages to be inserted. 
     *
     * @param message
     * @param messageStatus
     */
    suspend fun insertMessage(message: Messages, messageStatus: MessageStatus){
        if(doesMessageExists(messageID = message.messageID)){
            println("Appartenly this chat ID already exists")
            return
        }
        messagesDao.insertMessage(message)
        messageStatusDao.insertMessageStatus(messageStatus)
    }

    /**
     * Determine if a message is already in the database given its messageID
     *
     * @param messageID : messageID of the message to check for
     * @return Boolean: true if the message already exists
     */
    private fun doesMessageExists(messageID: String) :Boolean {
        val messageIDInDatabase = messagesDao.checkIfMessageExists(messageID)
        return !(messageIDInDatabase.isNullOrEmpty() && messageIDInDatabase.isNullOrBlank())
    }

    /**
     * Update message status "sent"
     *
     * @param isSent : boolean to update isSent value
     * @param messageID : messageID of the message to update the value for
     */
    suspend fun updateMessageIsSent(isSent: Boolean, messageID: String) = messageStatusDao.updateIsSent(isSent, messageID)

    /**
     * Update message status "read" of outgoing messages
     *
     * @param isRead : boolean to update isRead value
     * @param contactJID : contactJID or groupJID of the chat session
     */
    suspend fun updateMessageIsReadOutGoing(isRead: Boolean, contactJID: String) = messageStatusDao.updateIsReadOutGoing(isRead, contactJID)

    /**
     * Update message status "read' of incoming message
     *
     * @param isRead : boolean to update isRead value
     * @param contactJID : contactJID or groupJID of the chat session
     */
    suspend fun updateMessageIsReadIncoming(isRead: Boolean, contactJID: String) = messageStatusDao.updateIsReadIncoming(isRead, contactJID)

    /**
     * Update Message "received" status
     *
     * @param isReceived : boolean value to update to
     * @param messageID : message ID of the message to update
     */
    suspend fun updateMessageIsReceived(isReceived: Boolean, messageID: String) = messageStatusDao.updateIsReceived(isReceived, messageID)

    /**
     * Update message "draft" status
     *
     * @param isDraft : boolean value to update isDraft field
     * @param messageID : message ID of the message to update
     */
    suspend fun upadteMessageIsDraft(isDraft: Boolean, messageID: String) = messageStatusDao.updateIsDraft(isDraft, messageID)

    /**
     * Get the name of a contact given their JID
     *
     * @param contactJID : JID of the contact to retrieve the name for
     */
    fun getContactName(contactJID: String) = contactDao.getContactJID(contactJID)

    /**
     * Dev function to delete all of the contacts in the database.
     * Only used in development process
     *
     */
    suspend fun deleteAllContact(){
        contactDao.deleteAllContact()
        contactStatusDao.deleteAllContactStatus()
        contactTimeAvailableDao.deleteAllContactTime()
    }

    /**
     * Function to return all of the messages for a given chat session.
     * If the chat session does not exist, it will create it.
     *
     * @param groupJID : the JID of the chat session (or contactJID in the case of a single chat)
     * @param userUID : the user's firebase UID, lowercase
     * @param isGroupChat : whether this chat is a group chat (Multi user chat)
     * @return
     */
    fun getMessagesToDisplay(groupJID: String, userUID: String, isGroupChat: Boolean?) : LiveData<List<DisplayMessagesData>> {
        repositoryScope.launch {
            if(isGroupChat != null){
                if(doesChatSessionExist(groupJID) == null && groupJID.isNotEmpty() && !isGroupChat){
                    println("Got into this if statement")
                    val contactName = getContactName(groupJID)
                    val chatSession = ChatSession(
                        chatSessionID = 0,
                        groupID = groupJID,
                        createdBy = userUID,
                        dateCreated = TimeDateManager.getDate(),
                        userJID = userUID,
                        isContactTyping = false,
                        contactTypingName = null
                    )
                    val groupChat = GroupChat(
                        groupID = groupJID,
                        groupName = contactName,
                        contactJID = groupJID,
                        isGroupChat = false,
                        isSilenced = false,
                        occupants = listOf(contactName),
                        groupChatAvatarURI = null
                    )
                    chatSessionDao.insertChatSession(chatSession)
                    groupChatDao.insertGroupChat(groupChat)
            }

            }
        }

        println("Got into the repostiroy")
//        val displayMessagesData = MutableLiveData
        return if (!isGroupChat!!) messagesDao.getMessagesFromContactJID(groupJID)
        else messagesDao.getMessagesFromGroupJID(groupJID)
    }




}