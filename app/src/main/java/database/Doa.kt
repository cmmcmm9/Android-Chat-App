package database

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.*
import java.security.PublicKey

/**
 * Below are the DOA's used to interact with the tables, corresponding to their
 * tables. There is One DAO for each table to user for its interaction.
 *
 */

@Dao
interface UserDao{
    @Query("SELECT * FROM User")
    fun getAll(): LiveData<List<User>>

    @Query("SELECT userJID from User")
    fun getJID(): LiveData<String>

    @Query("SELECT * from User LIMIT 1")
    fun getUser(): LiveData<User>

    @Query("SELECT * FROM User WHERE userJID = :firebaseUID LIMIT 1")
    fun getUserByUID(firebaseUID: String) :User

    @Insert
    suspend fun insertUser(vararg user: User)

    @Update
    suspend fun updateUser(vararg user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("DELETE from User")
    suspend fun nukeUserTable()

}

@Dao
interface UserSettingsDao{
    @Query("SELECT * FROM UserSettings")
    fun getUserSett(): LiveData<List<UserSettings>>

    @Query("SELECT stayLoggedIn FROM UserSettings LIMIT 1")
    fun getStayLoggedInVal(): Boolean

    @Insert
    suspend fun insertSettings(vararg userSettings: UserSettings)

    @Update
    suspend fun updateSettings(vararg userSettings: UserSettings)

    @Transaction
    suspend fun newUserSettings(user: User){
        var userSettings = UserSettings(
            rowId = 0,
            userJID = user.userJID,
            stayLoggedIn = true,
            theme = "Blue",
            isAppLocked = false,
            allowScreenShots = false,
            backUpChats = false,
            messageFontSize = "14sp", //refers to 14sp
            autoDownloadPictures = true,
            showReadReceipts = true,
            showTypingIndicator = true,
            useLocation = true,
            silenceAllGroupChats = false,
            displayAvailableTime = true
        )
        insertSettings(userSettings)

    }

    @Query("DELETE from UserSettings")
    suspend fun nukeUserSettingsTable()
}

@Dao
interface UserandUserSettingsDao{
    @Transaction
    @Query("SELECT * FROM User")
    fun getUserAndSettings(): LiveData<List<UserandUserSettings>>

    @Transaction
    @Query("SELECT stayLoggedIn from UserSettings")
    fun getUserwithStayLoggedIn(): Boolean


}

@Dao
interface UserTimeAvailableDao{
    @Query("SELECT * FROM UserTimeAvailable")
    fun getAllUserTimeAvail(): List<UserTimeAvailable>

    @Insert
    suspend fun insertUserTimeAvail(vararg userTimeAvailable: UserTimeAvailable)

    @Transaction
    suspend fun newUserTimeAvail(user: User){

        for(i in 1..7){
            var userTimeAvailable = UserTimeAvailable(
                rowId = 0,
                dayOfTheWeek = i,
                timeAvailableStart = "08:00",
                timeAvailableEnd = "17:00",
                userJID = user.userJID
                )
            insertUserTimeAvail((userTimeAvailable))

        }
    }

    @Update
    suspend fun updateUserTimeAvailable(vararg userTimeAvailable: UserTimeAvailable)

    @Query("DELETE from UserTimeAvailable")
    suspend fun nukeUserTimeTable()
}

@Dao
interface UserAndUserTimeAvailDao{

    @Transaction
    @Query("SELECT * FROM User")
    fun getUserAndUserTimeAvail(): List<UserandUserTimeAvailable>
}

@Dao
interface UserStatusDao{
    @Query("SELECT * FROM UserStatus")
    fun getAllUserStatus(): LiveData<List<UserStatus>>

    @Insert
    suspend fun insertUserStatus(vararg userStatus: UserStatus)

    @Update
    suspend fun updateUserStatus(vararg userStatus: UserStatus)
}

@Dao
interface UserAndUserStatusDao{

    @Transaction
    @Query("SELECT * FROM User")
    fun getUserandUserStatus(): LiveData<List<UserandUserStatus>>
}
@Dao
interface ChatSessionDao{

    @Query("SELECT * FROM ChatSession")
    fun getAllChatSessions(): LiveData<List<ChatSession>>

    @Query("SELECT chatSessionID FROM ChatSession WHERE groupID = :groupJID")
    fun checkIfChatExists(groupJID: String) :Int?

    @Query("SELECT chatSessionID FROM ChatSession WHERE groupID = :contactJID")
    fun getChatSessionID(contactJID: String) :Int

    @Query("UPDATE ChatSession SET isContactTyping = :isContactTyping, contactTypingName = (SELECT contactName FROM Contact WHERE contactJID = :contactJID) WHERE groupID = :groupJID")
    fun updateTypingStatusForChat(isContactTyping: Boolean, contactJID: String, groupJID: String )


    @Transaction
    @Query("SELECT Contact.contactAvatar, GroupChat.groupName, GroupChat.isGroupChat, GroupChat.isSilenced, GroupChat.groupID, Messages.messageBody, Messages.time, Messages.date, Messages.isOutgoing, Messages.isMediaMessage, ChatSession.dateCreated FROM ChatSession\n" +
            " LEFT JOIN (SELECT Messages.chatSessionID, Messages.date, Messages.time, Messages.messageBody, Messages.isOutgoing, Messages.isMediaMessage FROM Messages GROUP BY Messages.chatSessionID ORDER BY Messages.date DESC, Messages.time DESC) Messages on ChatSession.chatSessionID = Messages.chatSessionID\n" +
            " JOIN GroupChat on ChatSession.groupID = GroupChat.groupID\n" +
            " LEFT JOIN Contact on GroupChat.contactJID = Contact.contactJID\n" +
            " ORDER BY Messages.date DESC, Messages.time DESC")
    fun getChatSessionsForRecycler() : LiveData<List<ChatSessionRecyclerData>>

    @Query("DELETE FROM ChatSession WHERE groupID = :groupJID")
    fun deleteChatSessionCascade(groupJID: String)

    @Insert
    suspend fun insertChatSession(chatSession: ChatSession) :Long

    @Delete
    suspend fun deleteChateSession(vararg chatSession: ChatSession)





}

@Dao
interface UserAndChatSessionDao{

    @Transaction
    @Query("SELECT * FROM User")
    fun getUserAndChatSession(): LiveData<List<UserandChatSession>>
}

@Dao
interface ChatSessionAndMessagesDao{

    @Transaction
    @Query("SELECT * FROM ChatSession")
    fun getAllChatSessionAndMessages(): LiveData<List<ChatSessionAndMessages>>

}

@Dao
interface GroupChatandChatSessionDao{

    @Transaction
    @Query("SELECT * FROM GroupChat")
    fun getAllGroupChatAndChatSess(): LiveData<List<GroupChatAndChatSession>>
}
@Dao
interface MessagesDao{

    @Query("SELECT * FROM Messages")
    fun getAllMessages(): LiveData<List<Messages>>

    @Query("SELECT Messages.*, Contact.contactName, GroupChat.isGroupChat, GroupChat.isSilenced, Contact.contactJID, Contact.contactAvatar, ChatSession.isContactTyping, ChatSession.contactTypingName, ChatSession.createdBy, MessageStatus.isRead, MessageStatus.isReceived, MessageStatus.isSent, MessageStatus.isDraft FROM Messages JOIN MessageStatus ON MessageStatus.messageID = Messages.messageID JOIN ChatSession ON Messages.chatSessionID = ChatSession.chatSessionID JOIN GroupChat ON ChatSession.groupID = GroupChat.groupID LEFT JOIN Contact ON GroupChat.contactJID = Contact.contactJID LEFT JOIN ContactStatus ON ContactStatus.contactJID = GroupChat.contactJID WHERE ChatSession.groupID = :groupJID")
    fun getMessagesFromContactJID(groupJID: String) : LiveData<List<DisplayMessagesData>>

    @Query("SELECT Messages.*, MessageStatus.isRead, Contact.contactName, GroupChat.isGroupChat, GroupChat.isSilenced, MessageStatus.isReceived, MessageStatus.isSent, MessageStatus.isDraft, ChatSession.isContactTyping, ChatSession.contactTypingName, ChatSession.createdBy FROM Messages JOIN MessageStatus ON MessageStatus.messageID = Messages.messageID JOIN ChatSession ON Messages.chatSessionID = ChatSession.chatSessionID JOIN GroupChat ON ChatSession.groupID = GroupChat.groupID LEFT JOIN Contact ON Messages.messageFrom = Contact.contactJID WHERE ChatSession.groupID = :groupJID")
    fun getMessagesFromGroupJID(groupJID: String) :LiveData<List<DisplayMessagesData>>

    @Transaction
    @Query("SELECT * FROM Messages JOIN ChatSession ON Messages.chatSessionID = ChatSession.chatSessionID WHERE ChatSession.groupID = :groupJID ORDER by Messages.date DESC, Messages.time DESC LIMIT 1")
    suspend fun getLastMessageFromMUC(groupJID: String) :Messages?

    @Query("SELECT messageID FROM Messages WHERE messageID = :messageID")
    fun checkIfMessageExists(messageID: String) :String

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(vararg messages: Messages)

    @Update
    suspend fun updateMessage(vararg messages: Messages)

    @Delete
    suspend fun deleteMessage(vararg messages: Messages)

}

@Dao
interface MessageStatusDao{

    @Query("SELECT * FROM MessageStatus")
    fun getAllMessageStatus(): LiveData<List<MessageStatus>>

    @Query("UPDATE MessageStatus SET isSent = :isSent WHERE messageID = :messageID")
    fun updateIsSent(isSent: Boolean, messageID: String)

    @Query("UPDATE MessageStatus SET isDraft = :isDraft WHERE messageID = :messageID")
    fun updateIsDraft(isDraft: Boolean, messageID: String)

    @Query("UPDATE MessageStatus SET isRead = :isRead WHERE MessageStatus.rowId in (SELECT MessageStatus.rowId from MessageStatus JOIN Messages ON MessageStatus.messageID = Messages.messageID JOIN ChatSession ON ChatSession.chatSessionID = Messages.chatSessionID WHERE ChatSession.groupID = :contactJID AND Messages.isOutgoing = 1)")
    fun updateIsReadOutGoing(isRead: Boolean, contactJID: String)

    @Query("UPDATE MessageStatus SET isRead = :isRead WHERE MessageStatus.rowId in (SELECT MessageStatus.rowId from MessageStatus JOIN Messages ON MessageStatus.messageID = Messages.messageID JOIN ChatSession ON ChatSession.chatSessionID = Messages.chatSessionID WHERE ChatSession.groupID = :contactJID AND Messages.isIncoming = 1)")
    fun updateIsReadIncoming(isRead: Boolean, contactJID: String)

    @Query("UPDATE MessageStatus SET isReceived = :isReceived WHERE messageID = :messageID")
    fun updateIsReceived(isReceived: Boolean, messageID: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageStatus(vararg messageStatus: MessageStatus)

    @Delete
    suspend fun deleteMessageStatus(vararg messageStatus: MessageStatus)

    @Update
    suspend fun updateMessageStatus(vararg messageStatus: MessageStatus)
}

@Dao
interface GroupChatDao{

    @Query("SELECT groupName FROM GroupChat WHERE groupID = :groupJID")
    fun getChatSessionDisplayName(groupJID: String) :String

    @Query("SELECT * FROM GroupChat")
    fun getAllGroupChat(): LiveData<List<GroupChat>>

    @Query("SELECT groupID FROM GroupChat WHERE isGroupChat = 1")
    suspend fun getAllGroupJIDs() : List<String>

    @Query("SELECT isGroupChat FROM GroupChat where groupID = :contactJID")
    fun isGroupChat(contactJID: String) :Boolean

    @Query("DELETE FROM GroupChat WHERE groupID = :groupJID")
    fun deleteGroupChat(groupJID: String)

    @Query("UPDATE GroupChat SET isSilenced = :isSilenced WHERE groupID = :groupJID")
    suspend fun updateSilenceGroupChat(isSilenced: Boolean, groupJID: String)

    @Query("SELECT isSilenced FROM GroupChat WHERE groupID = :groupJID")
    fun isChatSessionSilenced(groupJID: String): Boolean

    @Query("SELECT occupants FROM GroupChat WHERE groupID = :groupJID")
    fun getOccupantsInGroupChatAsList(groupJID: String) :String

    @Insert
    suspend fun insertGroupChat(vararg groupChat: GroupChat)

    @Update
    suspend fun updateGroupChat(vararg groupChat: GroupChat)

    @Delete
    suspend fun deleteGroupChat(vararg groupChat: GroupChat)

    @Query("UPDATE GroupChat SET occupants = :listOfOccupants WHERE groupID = :groupJID")
    fun fixOccupants(listOfOccupants: List<String>, groupJID: String)
}

@Dao
interface ContactAndGroupChatDao{

    @Transaction
    @Query("SELECT * FROM Contact")
    fun getGroupChatAndContact(): LiveData<List<ContactandGroupChat>>
}

@Dao
interface ContactDao{

    @Query("SELECT * FROM Contact")
    fun getAllContacts(): LiveData<List<Contact>>

    @Query("SELECT contactJID FROM Contact")
    fun getAllContactsJIDs(): List<String>

    @Query("SELECT contactName FROM Contact WHERE contactJID = :contactJID")
    fun getContactJID(contactJID: String): String

    @Query("SELECT contactName FROM Contact WHERE contactJID = :contactJID")
    fun doesContactExist(contactJID: String?): String?

    @Query("SELECT * FROM Contact WHERE contactJID = :contactJID")
    fun getSingleContact(contactJID: String) :Contact

    @Query("DELETE FROM Contact WHERE contactJID = :contactJID")
    suspend fun deleteSingleContact(contactJID: String)

    @Query("SELECT contactName FROM Contact WHERE contactJID IN (:occupantsList)")
    fun getContactDisplayNamesForChat(occupantsList: List<String>) : LiveData<List<String>>

    @Query("SELECT contactName FROM Contact WHERE contactJID IN (:occupantsList)")
    fun getContactDisplayNamesForChatTest(occupantsList: List<String>) : List<String>

    @Transaction
    @Query("SELECT contactAvatar, contactName, contactEmail, Contact.contactJID, ContactStatus.isOnline, ContactStatus.isAvailable, ContactTimeAvailable.timeAvailableStart, ContactTimeAvailable.timeAvailableEnd from Contact JOIN ContactStatus ON Contact.contactJID = ContactStatus.contactJID JOIN ContactTimeAvailable ON Contact.contactJID = ContactTimeAvailable.contactJID WHERE ContactTimeAvailable.dayOfTheWeek = :dayOfTheWeek  ")
    fun getDisplayContacts(dayOfTheWeek: Int) : LiveData<List<DisplayContactsRecyclerData>>

    @Query("UPDATE Contact SET contactPublicKey = :contactPublicKey WHERE contactJID = :contactJID")
    fun updateContactPublicKey(contactJID: String, contactPublicKey: String)

    @Query("SELECT contactPublicKey FROM Contact WHERE contactJID = :contactJID")
    fun getContactsPublicKey(contactJID: String) :String

    @Insert
    suspend fun insertContact(contact: Contact) :Long

    @Update
    suspend fun updateContact(vararg contact: Contact)

    @Delete
    suspend fun deleteContact(vararg contact: Contact)

    @Query("DELETE FROM Contact")
    fun deleteAllContact()
}

@Dao
interface ContactAndContactTimeAvailDao{

    @Transaction
    @Query("SELECT * FROM Contact")
    fun getAllContactAndContactAvail(): LiveData<List<ContactandContactTimeAvailable>>
}

@Dao
interface ContactAndContactStatusDao{

    @Transaction
    @Query("SELECT * FROM Contact")
    fun getAllContactAndContactStatus(): LiveData<List<ContactandContactStatus>>
}

@Dao
interface ContactTimeAvailableDao{

    @Query("SELECT * FROM ContactTimeAvailable")
    fun getAllContactAvailable(): LiveData<List<ContactTimeAvailable>>

    @Query("SELECT * FROM ContactTimeAvailable where contactJID = :contactJID")
    fun getContactTimeAvailByJID(contactJID: String): LiveData<List<ContactTimeAvailable>>

    @Query("SELECT * FROM ContactTimeAvailable where contactJID = :contactJID")
    fun getContactAvailableTime(contactJID: String): List<ContactTimeAvailable>

    @Query("SELECT rowId FROM ContactTimeAvailable WHERE contactJID = :contactJID ORDER BY dayOfTheWeek")
    fun getRowIDSForContactAvailableTime(contactJID: String) :List<Int>

    @Insert
    suspend fun insertContactTimeAvail(contactTimeAvailable: ContactTimeAvailable) :Long

    @Update
    suspend fun updateContactTimeAvail(vararg contactTimeAvailable: ContactTimeAvailable)

    @Delete
    suspend fun deleteContactTimeAvail(vararg contactTimeAvailable: ContactTimeAvailable)

    @Query("DELETE FROM ContactTimeAvailable WHERE contactJID = :contactJID")
    suspend fun deleteSingleContactTimeAvailable(contactJID: String)

    @Query("DELETE FROM ContactTimeAvailable")
    fun deleteAllContactTime()
}

@Dao
interface ContactStatusDao{

    @Query("SELECT * FROM ContactStatus")
    fun getAllContactStatus(): LiveData<List<ContactStatus>>

    @Query("SELECT * FROM ContactStatus where contactJID = :contactJID")
    fun getContactStatusByJID(contactJID: String): ContactStatus

    @Transaction
    @Query("SELECT ContactStatus.*, ContactTimeAvailable.timeAvailableStart, ContactTimeAvailable.timeAvailableEnd FROM ContactStatus JOIN ContactTimeAvailable ON ContactStatus.contactJID = ContactTimeAvailable.contactJID WHERE ContactStatus.contactJID = :contactJID AND ContactTimeAvailable.dayOfTheWeek = :dayOfTheWeek")
    fun getContactAvailableTime(contactJID: String, dayOfTheWeek: Int) :UpdateContactHelper

    @Insert
    suspend fun insertContactStatus(contactStatus: ContactStatus) :Long

    @Update
    suspend fun updateContactStatus(vararg contactStatus: ContactStatus)

    @Delete
    suspend fun deleteContactStatus(vararg contactStatus: ContactStatus)

    @Query("DELETE FROM ContactStatus WHERE contactJID = :contactJID")
    suspend fun deleteSingleContactStatus(contactJID: String)

    @Query("DELETE FROM ContactStatus")
    fun deleteAllContactStatus()
}

//select messages.messageBody, contact.contactName, contact.contactAvatar from messages JOIN chatsession on chatsession.chatSessionID = messages.chatSessionID JOIN groupchat on chatsession.groupID = groupchat.groupID JOIN contact on groupchat.contactJID = contact.contactJID


