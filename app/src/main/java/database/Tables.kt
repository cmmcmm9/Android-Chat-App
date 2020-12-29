package database

import androidx.room.*
import androidx.room.ForeignKey.CASCADE
import androidx.room.ForeignKey.NO_ACTION
import java.sql.Blob
import java.sql.Time
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Data class representation for the User table in android room
 *
 * @property userJID
 * @property fullName
 * @property email
 * @property phoneNumber
 * @property dateCreated
 * @property userAvatar
 */

@Entity
data class User(
    @PrimaryKey val userJID: String,
    val fullName: String?,
    val email: String?,
    val phoneNumber: String?,
    val dateCreated: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) var userAvatar: ByteArray?
)

/**
 * Data class representation for the UserTimeAvailable table in android room
 * @property rowId
 * @property userJID
 * @property timeAvailableStart
 * @property timeAvailableEnd
 * @property dayOfTheWeek
 */
@Entity
data class UserTimeAvailable (
    @PrimaryKey(autoGenerate = true) val rowId: Int,
    @ForeignKey(
        entity = User::class,
        parentColumns = ["userJID"],
        childColumns = ["userJID"],
        onUpdate = NO_ACTION,
        onDelete = CASCADE

    ) val userJID: String,
    var timeAvailableStart: String,
    var timeAvailableEnd: String,
    var dayOfTheWeek: Int

)

//One to many Relationship
data class UserandUserTimeAvailable(
    @Embedded val user: User,
    @Relation(
        parentColumn = "userJID",
        entityColumn = "userJID"
    )
    val userTimeAvailable: List<UserTimeAvailable>
)

//One to one
data class UserandUserStatus(
    @Embedded val user: User,
    @Relation(
        parentColumn = "userJID",
        entityColumn = "userJID"
    )
    val userStatus: UserStatus
)

/**
 * Data class representation for the UserStatus table in android room
 *
 * @property rowId
 * @property userJID
 * @property isOnline
 * @property isAvailable
 * @property lastOnlineTime
 * @property lastOnlineDate
 * @property isTyping
 */
@Entity
data class UserStatus(
    @PrimaryKey(autoGenerate = true) val rowId: Int?,
    @ForeignKey(
        entity= User::class,
        parentColumns = ["userJID"],
        childColumns = ["userJID"],
        onUpdate = CASCADE,
        onDelete = CASCADE

    ) val userJID: String,
    var isOnline: Boolean,
    var isAvailable: Boolean,
    var lastOnlineTime: String,
    var lastOnlineDate: String,
    var isTyping: Boolean
)

//One to many

data class UserandUserSettings(
    @Embedded val user: User,
    @Relation(
        parentColumn = "userJID",
        entityColumn = "userJID"
    )
    val userSettings: List<UserSettings>
)

/**
 * Data class representation for the UserSettings table in android room
 *
 * @property rowId
 * @property userJID
 * @property stayLoggedIn
 * @property theme
 * @property isAppLocked
 * @property allowScreenShots
 * @property backUpChats
 * @property messageFontSize
 * @property autoDownloadPictures
 * @property showReadReceipts
 * @property showTypingIndicator
 * @property useLocation
 * @property silenceAllGroupChats
 * @property displayAvailableTime
 */
@Entity
data class UserSettings(
    @PrimaryKey(autoGenerate = true) val rowId: Int,
    @ForeignKey(
        entity= User::class,
        parentColumns = ["userJID"],
        childColumns = ["userJID"],
        onUpdate = CASCADE,
        onDelete = CASCADE

    ) val userJID: String,
    var stayLoggedIn: Boolean,
    var theme: String,
    var isAppLocked: Boolean,
    var allowScreenShots: Boolean,
    var backUpChats: Boolean,
    var messageFontSize: String,
    var autoDownloadPictures: Boolean,
    var showReadReceipts: Boolean,
    var showTypingIndicator: Boolean,
    var useLocation: Boolean,
    var silenceAllGroupChats: Boolean,
    var displayAvailableTime: Boolean
)

//one to many
data class UserandChatSession(
    @Embedded val user: User,
    @Relation(
        parentColumn = "userJID",
        entityColumn = "userJID"
    )
    val chatSession: List<ChatSession>
)

/**
 * Data class representation for the ChatSession table in android room
 *
 * @property chatSessionID
 * @property dateCreated
 * @property createdBy
 * @property userJID
 * @property groupID
 * @property isContactTyping
 * @property contactTypingName
 */
@Entity
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val chatSessionID: Int,
    val dateCreated: String,
    val createdBy: String,
    @ForeignKey(
        entity= User::class,
        parentColumns = ["userJID"],
        childColumns = ["userJID"]


    ) val userJID: String,
    @ForeignKey(
        entity = GroupChat::class,
        parentColumns = ["groupID"],
        childColumns = ["groupID"],
        onDelete = CASCADE
    ) val groupID: String,

    val isContactTyping: Boolean,
    val contactTypingName: String?

)

//one to one
data class GroupChatAndChatSession(
    @Embedded val groupChat: GroupChat,
    @Relation(
        parentColumn = "groupID",
        entityColumn = "groupID"
    )
    val chatSession: ChatSession
)

/**
 * Data class representation for the GroupChat table in android room
 *
 * @property groupID
 * @property contactJID
 * @property groupName
 * @property occupants
 * @property isGroupChat
 * @property isSilenced
 * @property groupChatAvatarURI
 */
@Entity
data class GroupChat(
    @PrimaryKey val groupID: String,
    @ForeignKey(
        entity = Contact::class,
        parentColumns = ["contactJID"],
        childColumns = ["contactJID"]
    ) val contactJID: String?,
    val groupName: String,
    val occupants: List<String>,
    var isGroupChat: Boolean,
    var isSilenced: Boolean,
    val groupChatAvatarURI: String?

)

//one to many
data class ChatSessionAndMessages(
    @Embedded val chatSession: ChatSession,
    @Relation(
        parentColumn = "chatSessionID",
        entityColumn = "chatSessionID"
    )
    val messages: List<Messages>
)

/**
 * Data class representation for the Messages table in android room
 *
 * @property messageID
 * @property chatSessionID
 * @property messageFrom
 * @property messageTo
 * @property isIncoming
 * @property isOutgoing
 * @property date
 * @property time
 * @property messageBody
 * @property isEncrypted
 * @property isMediaMessage
 */
@Entity
data class Messages(
    @PrimaryKey val messageID: String,
    @ForeignKey(
        entity = ChatSession::class,
        parentColumns = ["chatSessionID"],
        childColumns = ["chatSessionID"],
        onDelete = CASCADE
    ) var chatSessionID: Int,
    val messageFrom: String,
    val messageTo: String,
    var isIncoming: Boolean,
    var isOutgoing: Boolean,
    val date: String,
    val time: String,
    val messageBody: String,
    val isEncrypted: Boolean,
    val isMediaMessage: Boolean
)

//one to one
data class MessagesandMessageStatus(
    @Embedded val messages: Messages,
    @Relation(
        parentColumn = "messageID",
        entityColumn = "messageID"
    )
    val messageStatus: MessageStatus
)

/**
 * Data class representation for the MessageStatus table in android room
 *
 * @property rowId
 * @property messageID
 * @property isRead
 * @property isReceived
 * @property isSent
 * @property isDraft
 */
@Entity
data class MessageStatus(
    @PrimaryKey(autoGenerate = true) val rowId: Int,
    @ForeignKey(
        entity = Messages::class,
        parentColumns = ["messageID"],
        childColumns = ["messageID"],
        onUpdate = CASCADE,
        onDelete = CASCADE
    ) val messageID: String,
    var isRead: Boolean,
    var isReceived: Boolean,
    var isSent: Boolean,
    var isDraft: Boolean

)

//one to many
data class ContactandGroupChat(
    @Embedded val contact: Contact,
    @Relation(
        parentColumn = "contactJID",
        entityColumn = "contactJID"
    )
    val groupChat: List<GroupChat>
)

/**
 * Data class representation for the Contact table in android room
 *
 * @property contactJID
 * @property contactName
 * @property contactEmail
 * @property contactPhoneNumber
 * @property contactAvatar
 * @property contactPublicKey
 */
@Entity
data class Contact(
    @PrimaryKey val contactJID: String,
    var contactName: String,
    var contactEmail: String,
    var contactPhoneNumber: String?,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) var contactAvatar: ByteArray?,
    val contactPublicKey: String?
)


//one to one
data class ContactandContactTimeAvailable(
    @Embedded val contact: Contact,
    @Relation(
        parentColumn = "contactJID",
        entityColumn = "contactJID"
    )
    val contactTimeAvailable: ContactTimeAvailable
)

/**
 * Data class representation for the ContactTimeAvailable table in android room
 *
 * @property rowId
 * @property contactJID
 * @property timeAvailableStart
 * @property timeAvailableEnd
 * @property dayOfTheWeek
 */
@Entity
data class ContactTimeAvailable(
    @PrimaryKey(autoGenerate = true) val rowId: Int,
    @ForeignKey(
        entity = Contact::class,
        parentColumns = ["contactJID"],
        childColumns = ["contactJID"],
        onUpdate = NO_ACTION,
        onDelete = CASCADE
    ) val contactJID: String,
    var timeAvailableStart: String,
    var timeAvailableEnd: String,
    var dayOfTheWeek: Int
)

//one to one
data class ContactandContactStatus(
    @Embedded val contact: Contact,
    @Relation(
        parentColumn = "contactJID",
        entityColumn = "contactJID"
    )
    val contactStatus: ContactStatus
)

/**
 * Data class representation for the ContactStatus table in android room
 *
 * @property rowId
 * @property contactJID
 * @property isOnline
 * @property isAvailable
 * @property lastOnlineTime
 * @property lastOnlineDate
 * @property isTyping
 * @property isBlocked
 * @property isMuted
 */
@Entity
data class ContactStatus(
    @PrimaryKey(autoGenerate = true) val rowId: Int,
    @ForeignKey(
        entity = Contact::class,
        parentColumns = ["contactJID"],
        childColumns = ["contactJID"],
        onUpdate = CASCADE,
        onDelete = CASCADE
    ) val contactJID: String,
    var isOnline: Boolean,
    var isAvailable: Boolean,
    var lastOnlineTime: String,
    var lastOnlineDate: String,
    var isTyping: Boolean,
    var isBlocked: Boolean,
    var isMuted: Boolean
)


