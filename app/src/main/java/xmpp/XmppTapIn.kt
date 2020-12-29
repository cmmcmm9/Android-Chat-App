package xmpp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.example.tapin.LoginPage
import com.example.tapin.R
import com.google.firebase.auth.FirebaseAuth
import database.*
import encryption.RSAEncryptionManager
import kotlinx.coroutines.*
import org.jivesoftware.smack.*
import org.jivesoftware.smack.chat2.Chat
import org.jivesoftware.smack.chat2.ChatManager
import org.jivesoftware.smack.chat2.IncomingChatMessageListener
import org.jivesoftware.smack.chat2.OutgoingChatMessageListener
import org.jivesoftware.smack.packet.Message
//import org.jivesoftware.smack.packet.MessageBuilder
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.provider.ProviderManager
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.roster.RosterListener
import org.jivesoftware.smack.sasl.provided.SASLPlainMechanism
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
//import org.jivesoftware.smackx.carbons.packet.CarbonExtension
import org.jivesoftware.smackx.chatstates.ChatState
import org.jivesoftware.smackx.chatstates.ChatStateListener
import org.jivesoftware.smackx.chatstates.ChatStateManager
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager
import org.jivesoftware.smackx.httpfileupload.UploadProgressListener
import org.jivesoftware.smackx.muc.*
import org.jivesoftware.smackx.muc.packet.MUCUser
import org.jivesoftware.smackx.offline.OfflineMessageManager
//import org.jivesoftware.smackx.omemo.OmemoMessage
//import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener
import org.jivesoftware.smackx.ping.PingFailedListener
import org.jivesoftware.smackx.ping.PingManager
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener
import org.jivesoftware.smackx.vcardtemp.VCardManager
import org.jivesoftware.smackx.vcardtemp.packet.VCard
import org.jivesoftware.smackx.vcardtemp.provider.VCardProvider
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.EntityJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import org.jxmpp.jid.util.JidUtil
import repository.Repository
import java.io.File
import java.util.*

//Note: EntityBareJID is localpart@domain.com
//in sendMessage, message.stanzaid == recieptID in RecieptChanged()
//available == IS CONNECTED
//isAway, set status


/**
 * Class that Handles all of the XMPP Related logic.
 * It is a singleton, initialized by the companion object, and passed a reference to the @see [Repository]
 *
 */

 class XmppTapIn : IncomingChatMessageListener, MessageListener, ChatStateListener, ReceiptReceivedListener, OutgoingChatMessageListener, ConnectionListener, RosterListener, PingFailedListener, InvitationListener, InvitationRejectionListener, PresenceListener{
    //private val db = TapInDatabase.getDatabase(context, GlobalScope)


     //All variable that are initialized late, after an authorized XMPP connection has been established

    private lateinit var repository: Repository
    private lateinit var xmppContext: Context
    private lateinit var appLifecycleDetector: AppLifecycleDetector
    private lateinit var connection: XMPPTCPConnection
    private lateinit var chatManager: ChatManager
    private lateinit var pingManager: PingManager
    private lateinit var vCardManager: VCardManager
    private lateinit var stanzaCollector: StanzaCollector
    private lateinit var vCardProvider: VCardProvider
    private lateinit var deliveryReceiptManager: DeliveryReceiptManager
    private lateinit var chatStateManager: ChatStateManager
    private lateinit var reconnectionManager: ReconnectionManager
    private lateinit var mucManager: MultiUserChatManager
    private lateinit var roster: Roster
    private lateinit var offlineMessageManager: OfflineMessageManager

    private lateinit var rsaEncryptionManager: RSAEncryptionManager


    //This class contains its own coroutine scope, to make database and network calls
    private val xmppScope = CoroutineScope(Job() + Dispatchers.IO)

    //authToken, for reconnection. Will be set by fun connectAndLogin
    private var authToken: String? = null


    //TODO change to tapinapp.com and ubunut.tapinapp.com
    //Domain for my XMPP server
    private  val domain = "tapinapp.com"
    private val conference = "conference"

    //variables to make sure the connection was disconnected on purpose
    var connected = false
    private var disconnValid = false

    //
//    private val messageListener by lazy {
//        IncomingChatMessageListener { from, message, chat ->
//            newIncomingMessage(from, message, chat)
//        }
//
//    }

    //configuration variable for the XMPPTCP connection
    private val config = XMPPTCPConnectionConfiguration.builder()
        .setXmppDomain(domain)
        //.setHost("tapinserver.tapinapp.com")
        .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
        //.setSecurityMode(ConnectionConfiguration.SecurityMode.required)
        .enableDefaultDebugger()
        .setSendPresence(false)
        .setCompressionEnabled(true)
        //.setHostAddressByNameOrIp("$host.$domain")
        .build()

    //XMPP presences variables
     private val presenceAvail = Presence(Presence.Type.available)
     private val presenceUnavail = Presence(Presence.Type.unavailable)
     private val presenceAway = Presence(Presence.Type.available, "I am away", 42, Presence.Mode.away)


    /**
     * This is called after the connection has been established and authenticated
     * initializes all of the XMPP listeners and managers needed for the class instance.
     */
    private fun initializeXXMPPListeners(){


        println("In XMPP")
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(xmppContext)
        println(sharedPreferences.getString("sundayStart", ""))
        //connection variables
        connection.addConnectionListener(this)
        pingManager = PingManager.getInstanceFor(connection)
        pingManager.pingServerIfNecessary()
        pingManager.registerPingFailedListener(this)

        reconnectionManager = ReconnectionManager.getInstanceFor(connection)
        reconnectionManager.enableAutomaticReconnection()
        reconnectionManager.setFixedDelay(30)
        reconnectionManager.setReconnectionPolicy(ReconnectionManager.ReconnectionPolicy.FIXED_DELAY)


        connection.setUseStreamManagement(true)
        connection.setUseStreamManagementResumption(true)
//        connection.sendSmAcknowledgement()




        //chat managers, one-to-one init
        //val incommingMessageHandler = IncommingMessageHandler
        chatManager = ChatManager.getInstanceFor(connection)
        chatManager.addIncomingListener(this)
        //chatManager.addIncomingListener(incommingMessageHandler)
        chatManager.addOutgoingListener(this)
        chatStateManager = ChatStateManager.getInstance(connection)
        chatStateManager.addChatStateListener(this)

        //MUC managers
        mucManager = MultiUserChatManager.getInstanceFor(connection)
        mucManager.addInvitationListener(this)

        kotlin.runCatching { joinAllGroupChats() }

//        muc = mucManager.getMultiUserChat(JidCreate.entityBareFrom("test1@conference.${domain}"))
//        mucManager.setAutoJoinOnReconnect(true)
//
//        var resourcePart: Resourcepart = Resourcepart.from("test1")
//
//        var date: Date = SimpleDateFormat("yyyy-MM-dd").parse("2020-08-22")
//        var mec: MucEnterConfiguration.Builder = muc.getEnterConfigurationBuilder(resourcePart)
//        mec.requestHistorySince(date)
//        var mucEnterConfiguration = mec.build()
//
//
//        muc.join(mucEnterConfiguration)
//        muc.sendMessage("Hello from kotlin")
//        muc.addMessageListener(XmppTapIn())

        //delivery Receipt listeners
        deliveryReceiptManager = DeliveryReceiptManager.getInstanceFor(connection)
        deliveryReceiptManager.addReceiptReceivedListener(this)
        deliveryReceiptManager.autoAddDeliveryReceiptRequests()

        //roster init
        roster = Roster.getInstanceFor(connection)
        roster.addRosterListener(this)

        //vcard setup
        vCardProvider = VCardProvider()
        ProviderManager.addIQProvider("vCard", "vcard-temp", vCardProvider)
        vCardManager = VCardManager.getInstanceFor(connection)



    }

    /**
     * Connect to the server without listeners. This is done
     * when an offline message is received. If the listeners
     * are registered, then double messages can occur.
     * @see[getOfflineMessages]
     * @see[initializeXXMPPListeners]
     *
     * @param username : username of the user to sign in (firebase uid)
     * @param authToken : valid firebase ID token to send Openfire
     */
    fun connectAndLoginWithoutListeners(username: String, authToken: String?) {
        if(connection.isConnected && connection.isAuthenticated) return
        if(!connection.isConnected) connection = XMPPTCPConnection(config)
        offlineMessageManager = OfflineMessageManager(connection)
        val mechanisms = SASLPlainMechanism()
        SASLAuthentication.registerSASLMechanism(mechanisms)
        SASLAuthentication.blacklistSASLMechanism("SCRAM-SHA-1")
        SASLAuthentication.blacklistSASLMechanism("DIGEST-MD5")
        SASLAuthentication.blacklistSASLMechanism("SHA-1")
        SASLAuthentication.unBlacklistSASLMechanism("PLAIN")
        connection.replyTimeout = 60000L

        if(!connection.isConnected) connection.connect()
        if(connection.isAuthenticated){
            xmppScope.launch(Dispatchers.IO) {
                getOfflineMessages()
            }
        }
        else{
            connection.login(username, authToken)
        }
    }

    /**
     * This function establishes a connection to the XMPPP server and attempts to login.
     * It takes the firebaseUID and the corresponding IDToken as the password.
     * Note the email must be verified before a successful login can be established.
     * @param username The username to sign in with, the firebase UID
     * @param authToken The corresponding IDtoken for the firebase user
     */
    fun connectAndLogin(username: String, authToken: String?, sendPresence: Boolean){
        //return if the connection is already established
        if(connection.isConnected && connection.isAuthenticated) return
        if(!connection.isConnected) connection = XMPPTCPConnection(config)

        offlineMessageManager = OfflineMessageManager(connection)
        val mechanisms = SASLPlainMechanism()
        SASLAuthentication.registerSASLMechanism(mechanisms)
        SASLAuthentication.blacklistSASLMechanism("SCRAM-SHA-1")
        SASLAuthentication.blacklistSASLMechanism("DIGEST-MD5")
        SASLAuthentication.blacklistSASLMechanism("SHA-1")
        SASLAuthentication.unBlacklistSASLMechanism("PLAIN")

        connection.replyTimeout = 60000L

       if(!connection.isConnected) connection.connect()

        println("Is resumption possible: ${connection.isSmResumptionPossible}")
        if(connection.isAuthenticated){
            initializeXXMPPListeners()
            connected = true
            println("Already authenticated")

        }
        else{
            println("Not authenticated")
            connection.login(username, authToken)
            initializeXXMPPListeners()
            connected = true
        }

        if(sendPresence) setAvailable()

        //check is the user VCard is set, if not set it.
        if(loadUserVCard().firstName.isNullOrEmpty()) {
            xmppScope.launch(Dispatchers.IO){
                repository.getUserByUID(FirebaseAuth.getInstance().currentUser!!.uid)?.let { user ->
                    setUserVCard(user)
                }
            }
        }
    }

    /**
     * Function to get all of the user's offline messages
     * they have stored on the server.
     *
     * @return Map<String, String> : a map of the retrieved messages. map["From"] = contact name , map["Body"] = message body
     */
    fun getOfflineMessages() :Map<String, String>{
        //println("Does the server support flex offline message: ${offlineMessageManager.supportsFlexibleRetrieval()}")
        val messagesRetrieved = mutableMapOf<String, String>()
        val messagesToDelete = mutableListOf<String>()
        offlineMessageManager.headers.forEach {messageHeader ->
            val date = TimeDateManager.convertUTCtoLocalDate(messageHeader.stamp)
            val time = TimeDateManager.convertUTCtoLocalTime(messageHeader.stamp)
            val timeStamp = mutableListOf<String>(messageHeader.stamp)
            val offlineMessage = offlineMessageManager.getMessages(timeStamp)
            offlineMessage.forEach{message ->
                if(!message.body.isNullOrEmpty()){
                    messagesToDelete.addAll(timeStamp)
                    messagesRetrieved["From"] = repository.getContactName(message.from.asEntityBareJidIfPossible().toString())
                    messagesRetrieved["Body"] = message.body
                    var isMediaMessage = false
                    var isEncrypted = false
                    message.subjects?.forEach { subject ->
                        when(subject.subject){
                            "Media" -> isMediaMessage = true
                            "Encrypted" -> isEncrypted = true
                        }
                    }
                    if(isEncrypted) message.body = rsaEncryptionManager.decryptMessage(message.body)
                    insertNewMessage(from = message.from.asEntityBareJidIfPossible().toString(), groupJID = message.from.asEntityBareJidIfPossible().toString(),  message = message, date, time, isMediaMessage, isGroupChat = false, isEncrypted)

                }

            }
        }
        offlineMessageManager.deleteMessages(messagesToDelete)
        return messagesRetrieved
    }

    /**
     * Function to insert a new message. Will create a new chat session if one does not exist with the given
     * groupJID.
     *
     * @param from : JID of the message sender
     * @param groupJID : JID of the chat session
     * @param message : message (XMPP) received
     * @param date : date the message was received
     * @param time : time the message was received
     * @param isMediaMessage : whether or not this is a Media Message
     * @param isGroupChat :whether this chat session is a group chat
     * @param isEncrypted : whether this message is encrypted
     */
    private fun insertNewMessage(from: String, groupJID: String, message: Message?, date: String, time: String, isMediaMessage: Boolean, isGroupChat: Boolean, isEncrypted: Boolean){

        if(message == null || message.body.isNullOrEmpty()) return

        xmppScope.launch {

            val chatSessionID = repository.doesChatSessionExist(groupJID = groupJID)
            val doesContactExist = repository.checkIfContactExists(from)

            if(chatSessionID != null){
                val newMessage =
                    Messages(
                        messageID = message.stanzaId.toString(),
                        chatSessionID = chatSessionID,
                        date = date,
                        time = time,
                        messageBody = message.body.toString(),
                        messageFrom = from,
                        messageTo = message.to.toString(),
                        isIncoming = true,
                        isOutgoing = false,
                        isEncrypted = isEncrypted,
                        isMediaMessage = isMediaMessage
                    )

                val messageStatus = MessageStatus(
                    rowId = 0,
                    messageID = newMessage.messageID,
                    isDraft = false,
                    isRead = false,
                    isReceived = true,
                    isSent = false
                )

                repository.insertMessage(message = newMessage, messageStatus = messageStatus)


            }
            else if(doesContactExist){
                val contactName = repository.getContactName(from)
                println("In New Chat Session with contactName: $contactName")
                val chatSession = ChatSession(
                    chatSessionID = 0,
                    userJID = message.to.localpartOrNull.toString(),
                    groupID = groupJID,
                    createdBy = from,
                    dateCreated = TimeDateManager.getDate(),
                    isContactTyping = false,
                    contactTypingName = null
                )
                val groupChat = GroupChat(
                    groupID = groupJID,
                    groupName = contactName,
                    contactJID = from,
                    isGroupChat = isGroupChat,
                    isSilenced = false,
                    occupants = listOf(from),
                    groupChatAvatarURI = null
                )

                val newMessage = Messages(
                    messageID = message.stanzaId.toString(),
                    chatSessionID = 0,
                    date = date,
                    time = time,
                    messageBody = message.body.toString(),
                    messageFrom = from.toString(),
                    messageTo = message.to.toString(),
                    isIncoming = true,
                    isOutgoing = false,
                    isEncrypted = isEncrypted,
                    isMediaMessage = isMediaMessage
                )
                val messageStatus = MessageStatus(
                    rowId = 0,
                    messageID = newMessage.messageID,
                    isDraft = false,
                    isRead = false,
                    isReceived = true,
                    isSent = false
                )

                repository.insertNewIncomingChatSession(chatSession, groupChat, newMessage, messageStatus)
            }
            else{
                generateNewContact(from)

                val chatSession = ChatSession(
                    chatSessionID = 0,
                    userJID = message.to.localpartOrNull.toString(),
                    groupID = groupJID,
                    createdBy = from,
                    dateCreated = TimeDateManager.getDate(),
                    isContactTyping = false,
                    contactTypingName = null
                )
                val groupChat = GroupChat(
                    groupID = groupJID,
                    groupName = from,
                    contactJID = from,
                    isGroupChat = isGroupChat,
                    isSilenced = false,
                    occupants = listOf(from),
                    groupChatAvatarURI = null
                )

                val newMessage = Messages(
                    messageID = message.stanzaId.toString(),
                    chatSessionID = 0,
                    date = date,
                    time = time,
                    messageBody = message.body.toString(),
                    messageFrom = from,
                    messageTo = message.to.toString(),
                    isIncoming = true,
                    isOutgoing = false,
                    isEncrypted = isEncrypted,
                    isMediaMessage = isMediaMessage
                )
                val messageStatus = MessageStatus(
                    rowId = 0,
                    messageID = newMessage.messageID,
                    isDraft = false,
                    isRead = false,
                    isReceived = true,
                    isSent = false
                )

                repository.insertNewIncomingChatSession(chatSession, groupChat, newMessage, messageStatus)


            }

            val chatSessionName = repository.getChatSessionDisplayName(from)
            val notificationContent = mapOf<String, String>(Pair("From", chatSessionName), Pair("Body", message.body))
            sendNotification(notificationContent, groupJID, isGroupChat)
        }
    }

    /**
     * This method sends a "Presence Available" status to the XMPP server.
     */
     fun setAvailable(){
         connection.sendStanza(presenceAvail)
     }

    /**
     * This function sends a "Presence Unavailable" status to the XMPP server
     */
     fun setUnAvailable(){
         connection.sendStanza(presenceUnavail)
     }

    /**
     * This function sends a "Presence Away" status to the XMPP server
     */
     fun setAway() = connection.sendStanza(presenceAway)

    /**
     * This function disconnects the XMPP connection from the server
     */
    fun disconnect(){
        connection.sendStanza(presenceUnavail)
        connection.removeConnectionListener(this)

        connection.disconnect()

        println("The connection is connected: ${connection.isConnected}")
        println("The connection is auth: ${connection.isAuthenticated}")
        disconnValid = true
        connected = false
    }

    /**
     * This function is called when the connection is established, but not authenticated.
     * @see [ConnectionListener]
     */
    override fun connected(connection: XMPPConnection?) {
        connected = connection?.isConnected ?: false

//        if(!connected){
//            connectAndLogin(username, authToken)
//        }
    }

    /**
     * This function is called when the connection is disconnected.
     * It will check if the disconnection was intentional.
     * @see [ConnectionListener]
     */
    override fun connectionClosed() {
        println("Connect closed on error")
        if(!disconnValid){
            connectAndLogin(FirebaseAuth.getInstance().currentUser?.uid!!, authToken, sendPresence = true)

        }
    }

    /**
     * This function is called when the connection was disconnected because of an error.
     * @see [ConnectionListener]
     */
    override fun connectionClosedOnError(e: Exception?) {
        println("Connect closed on error")
    }

    /**
     * This function is called when the connection has been authenticated.
     * @see [ConnectionListener]
     */
    override fun authenticated(connection: XMPPConnection?, resumed: Boolean) {
        println("The user has been authenticated")
    }


//    fun setAvailable(){
//        //TODO check avail hours in android
//
//        presenceAvail.status = "Available"
//        //presenceAway.status = "away"
//    }

    /**
     * This function sets the users vCard based on the data in @see [User] and Settings in Shared Preferences
     * @param user The current user in the database. @see [User]
     * @see [VCard]
     */
     fun setUserVCard(user: User){
        println("Got into setUserVcard with $user")
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(xmppContext)

        val vCard: VCard = vCardManager.loadVCard()
        vCard.firstName = user.fullName
        vCard.setPhoneHome("CELL", user.phoneNumber)
        vCard.emailHome = user.email

        //set the available time for the user
        vCard.setField("SUNSTART", sharedPreferences.getString("sundayStart", "08:00"))
        vCard.setField("SUNEND", sharedPreferences.getString("sundayEnd", "17:00"))
        vCard.setField("MONSTART", sharedPreferences.getString("mondayStart", "08:00"))
        vCard.setField("MONEND", sharedPreferences.getString("mondayEnd", "17:00"))
        vCard.setField("TUESSTART", sharedPreferences.getString("tuesdayStart", "08:00"))
        vCard.setField("TUESEND", sharedPreferences.getString("tuesdayEnd", "17:00"))
        vCard.setField("WEDSTART", sharedPreferences.getString("wednesdayStart", "08:00"))
        vCard.setField("WEDEND", sharedPreferences.getString("wednesdayEnd", "17:00"))
        vCard.setField("THURSTART", sharedPreferences.getString("thursdayStart", "08:00"))
        vCard.setField("THUREND", sharedPreferences.getString("thursdayEnd", "17:00"))
        vCard.setField("FRISTART", sharedPreferences.getString("fridayStart", "08:00"))
        vCard.setField("FRIEND", sharedPreferences.getString("fridayEnd", "17:00"))
        vCard.setField("SATSTART", sharedPreferences.getString("saturdayStart", "08:00"))
        vCard.setField("SATEND", sharedPreferences.getString("saturdayEnd", "17:00"))

//        vCard.setField("SUNSTART", sharedPreferences.getString("weekEndStartTime", "08:00"))
//        vCard.setField("SUNEND", sharedPreferences.getString("weekEndEndTime", "17:00"))
//        vCard.setField("MONSTART", sharedPreferences.getString("weekDayStartTime", "08:00"))
//        vCard.setField("MONEND", sharedPreferences.getString("weekDayEndTime", "17:00"))
//        vCard.setField("TUESSTART", sharedPreferences.getString("weekDayStartTime", "08:00"))
//        vCard.setField("TUESEND", sharedPreferences.getString("weekDayEndTime", "17:00"))
//        vCard.setField("WEDSTART", sharedPreferences.getString("weekDayStartTime", "08:00"))
//        vCard.setField("WEDEND", sharedPreferences.getString("weekDayEndTime", "17:00"))
//        vCard.setField("THURSTART", sharedPreferences.getString("weekDayStartTime", "08:00"))
//        vCard.setField("THUREND", sharedPreferences.getString("weekDayEndTime", "17:00"))
//        vCard.setField("FRISTART", sharedPreferences.getString("weekDayStartTime", "08:00"))
//        vCard.setField("FRIEND", sharedPreferences.getString("weekDayEndTime", "17:00"))
//        vCard.setField("SATSTART", sharedPreferences.getString("weekEndStartTime", "08:00"))
//        vCard.setField("SATEND", sharedPreferences.getString("weekEndEndTime", "17:00"))

        //send the updated vCard to the xmpp server
        vCardManager.saveVCard(vCard)
    }

    /**
     * This function returns the user's vCard stored in the server.
     * @return The user's current vCard
     */
    fun loadUserVCard() :VCard{

        return vCardManager.loadVCard()

    }

    /**
     * This function returns the contacts vCard from the server, given the contact's bareJID.
     * @param contactJID The contact's bareJID as a String. For example 'contactJID@tapinapp.com'
     * @return The contact's vCard.
     */
    private fun loadContactVCard(contactJID: String) :VCard {

        val bareJid = JidCreate.entityBareFrom(contactJID)
        return vCardManager.loadVCard(bareJid)
    }

    /**
     * Function to update the contact information in the phone's database based on the contact's vCard.
     * @param contactJID The contact's bareJID as a String. For example 'contactJID@tapinapp.com'
     */
     fun updateContactFromVcard(contactJID: String){
         xmppScope.launch {
             val currentContact = repository.getContact(contactJID)
             val currentContactTimeAvailable = repository.getContactTimeAvailableRowIDs(contactJID)
             val vCard = loadContactVCard(contactJID)
             val contactName = vCard.firstName
             println("Attempting to add $contactName")
             val startAndEndTimes = LinkedList<String>()
             startAndEndTimes.add(vCard.getField("SUNSTART"))
             startAndEndTimes.add(vCard.getField("SUNEND"))
             startAndEndTimes.add(vCard.getField("MONSTART"))
             startAndEndTimes.add(vCard.getField("MONEND"))
             startAndEndTimes.add(vCard.getField("TUESSTART"))
             startAndEndTimes.add(vCard.getField("TUESEND"))
             startAndEndTimes.add(vCard.getField("WEDSTART"))
             startAndEndTimes.add(vCard.getField("WEDEND"))
             startAndEndTimes.add(vCard.getField("THURSTART"))
             startAndEndTimes.add(vCard.getField("THUREND"))
             startAndEndTimes.add(vCard.getField("FRISTART"))
             startAndEndTimes.add(vCard.getField("FRIEND"))
             startAndEndTimes.add(vCard.getField("SATSTART"))
             startAndEndTimes.add(vCard.getField("SATEND"))

             val newContactInfo = Contact(
                 contactName = contactName,
                 contactJID = currentContact.contactJID,
                 contactAvatar = vCard.avatar,
                 contactEmail = vCard.emailHome,
                 contactPhoneNumber = vCard.getPhoneHome("CELL"),
                 contactPublicKey = null
             )

             print("Contact to add is $newContactInfo")

             val newContactTimeAvailableAll = mutableListOf<ContactTimeAvailable>()

             for(i in 1..7){
                 val contactTimeAvailable = ContactTimeAvailable(
                     rowId = currentContactTimeAvailable[i-1],
                     dayOfTheWeek = i,
                     timeAvailableStart = startAndEndTimes.remove(),
                     timeAvailableEnd = startAndEndTimes.remove(),
                     contactJID = currentContact.contactJID
                 )
                 newContactTimeAvailableAll.add(contactTimeAvailable)

             }

             repository.updateContactTimeAvailable(newContactTimeAvailableAll)
             repository.updateContact(newContactInfo)
         }
     }


    /**
     * Function to send an XMPP message.
     * @param contactJID The contact's bareJID as a String. For example 'contactJID@tapinapp.com'
     * @param messageBody The message body. For example "Hey, how are you?"
     */
    fun sendMessage(contactJID: String ,messageBody: String, isMediaMessage: Boolean){

        val encryptMessage = PreferenceManager.getDefaultSharedPreferences(xmppContext).getBoolean("encrypt-messages", false)

        val userUID = FirebaseAuth.getInstance().currentUser!!.uid
        //val chatManager = ChatManager.getInstanceFor(connection)
        val bareContactJID = JidCreate.entityBareFrom(contactJID)
        val chat = chatManager.chatWith(bareContactJID)
        val message = Message()
        message.body = messageBody
        if(isMediaMessage) message.subject = "Media"
        message.type = Message.Type.chat

        //message.stanzaId = UUID.randomUUID().toString().replace("-", "")
        println("Test if the stanza id is already set ${message.stanzaId}")

        xmppScope.launch(Dispatchers.IO) {
            if(encryptMessage){
                message.addSubject("English", "Encrypted")
                message.body = rsaEncryptionManager.encryptMessage(messageBody, contactJID)
            }
            val chatSessionID =repository.getChatSessionID(contactJID)
            val messageData = Messages(
                messageID = message.stanzaId,
                chatSessionID = chatSessionID,
                messageTo = contactJID,
                messageFrom = userUID,
                messageBody = messageBody,
                isEncrypted = encryptMessage,
                isOutgoing = true,
                isIncoming = false,
                date = TimeDateManager.getDate(),
                time = TimeDateManager.getTime(),
                isMediaMessage = isMediaMessage
                )
            val messageStatus = MessageStatus(
                rowId = 0,
                messageID = message.stanzaId,
                isSent = false,
                isReceived = false,
                isRead = false,
                isDraft = false
            )
            repository.insertMessage(messageData, messageStatus)
            chat.send(message)
        }



    }



    /**
     * Function called to sync the user's roster upon a refresh request on @see [com.example.tapin.DisplayContactsActivity]
     * Will launch a separate co-routine to reload the user's roster, and double check each roster entry
     * with the current contacts in the database.
     *
     */
    fun syncRoster(){
         xmppScope.launch(Dispatchers.IO){
             print("Got into sync contact co-routine")
             roster.reloadAndWait()
             println("The roster count is ${roster.entryCount}")
             roster.entries.forEach{rosterEntry ->
                 val entityBareJid = rosterEntry.jid.asEntityBareJidIfPossible().toString()
                 if(!repository.checkIfContactExists(contactJID = entityBareJid)){
                     println("Attempting to add $entityBareJid in syncRoster")
                     generateNewContact(entityBareJid)
                 }
                 updateContactFromVcard(entityBareJid)
             }
         }
     }


    /**
     * Function to insert a new contact in the database, based on their vCard.
     * @param contactJID The contact's bareJID as a String. For example 'contactJID@tapinapp.com'
     */
     private fun generateNewContact(contactJID: String) {

         val vCard = loadContactVCard(contactJID)
         val contactName = vCard.firstName
         println("Attempting to add $contactName")
         val startAndEndTimes = LinkedList<String>()
         startAndEndTimes.add(vCard.getField("SUNSTART"))
         startAndEndTimes.add(vCard.getField("SUNEND"))
         startAndEndTimes.add(vCard.getField("MONSTART"))
         startAndEndTimes.add(vCard.getField("MONEND"))
         startAndEndTimes.add(vCard.getField("TUESSTART"))
         startAndEndTimes.add(vCard.getField("TUESEND"))
         startAndEndTimes.add(vCard.getField("WEDSTART"))
         startAndEndTimes.add(vCard.getField("WEDEND"))
         startAndEndTimes.add(vCard.getField("THURSTART"))
         startAndEndTimes.add(vCard.getField("THUREND"))
         startAndEndTimes.add(vCard.getField("FRISTART"))
         startAndEndTimes.add(vCard.getField("FRIEND"))
         startAndEndTimes.add(vCard.getField("SATSTART"))
         startAndEndTimes.add(vCard.getField("SATEND"))

         val contact = Contact(
             contactName = contactName,
             contactJID = contactJID,
             contactAvatar = vCard.avatar,
             contactEmail = vCard.emailHome,
             contactPhoneNumber = vCard.getPhoneHome("CELL"),
             contactPublicKey = null
         )

         print("Contact to add is $contact")

         val contactStatus = ContactStatus(
             rowId = 0,
             contactJID = contactJID,
             isAvailable = false,
             isTyping = false,
             isOnline = false,
             isBlocked = false,
             isMuted = false,
             lastOnlineTime = TimeDateManager.getTime(),
             lastOnlineDate = TimeDateManager.getDate()

         )
         print("Contact Status is: $contactStatus")

         val contactTimeAvailableAll = mutableListOf<ContactTimeAvailable>()

         print("Contact AvailableTimeAll is: $contactTimeAvailableAll")
         for(i in 1..7){
             val contactTimeAvailable = ContactTimeAvailable(
                 rowId = 0,
                 dayOfTheWeek = i,
                 timeAvailableStart = startAndEndTimes.remove(),
                 timeAvailableEnd = startAndEndTimes.remove(),
                 contactJID = contactJID
             )
             contactTimeAvailableAll.add(contactTimeAvailable)

         }
         xmppScope.launch(Dispatchers.IO){
             repository.insertNewContacts(contact, contactTimeAvailableAll, contactStatus)
         }

     }


    /**
     * Function called when a roster entry is deleted from the user's roster.
     * Will delete them from the database.
     * @param addresses A mutable collection of the JID's of the deleted entries
     */
    override fun entriesDeleted(addresses: MutableCollection<Jid>?) {
        xmppScope.launch {
            addresses?.forEach {jid ->
                val entityBareJid = jid.asEntityBareJidIfPossible().toString()
                repository.deleteContact(entityBareJid)
                //TODO Delete Contact
            }
        }

    }


    /**
     * Called when the presence of a roster entry is changed.
     *
     * To get the current "best presence" for a user after the presence update, query the roster:
     * <pre>
     *    String user = presence.getFrom();
     *    Presence bestPresence = roster.getPresence(user);
     * </pre>
     *
     * That will return the presence value for the user with the highest priority and
     * availability.
     *
     * Note that this listener is triggered for presence (mode) changes only
     * (e.g presence of types available and unavailable. Subscription-related
     * presence packets will not cause this method to be called.
     *
     * Updates the contacts presence in @see [ContactStatus].
     * Note even if a user is "Online" but not within their available time, their status will still be set to Offline.
     *
     * @param presence the presence that changed.
     * @see Roster#getPresence(org.jxmpp.jid.BareJid)
     */
    override fun presenceChanged(presence: Presence?) {
        if(presence == null) return

        xmppScope.launch {
            val entityBareJid = presence.from.asEntityBareJidIfPossible().toString()
            val currentContactStatus = repository.getContactAvailableTimeForToday(entityBareJid, TimeDateManager.getDayOfWeek())

            if(!repository.checkIfContactExists(entityBareJid)) generateNewContact(entityBareJid)

            println("Current contact Status is: ${currentContactStatus.timeAvailableEnd} and ${currentContactStatus.timeAvailableStart}")


            if(TimeDateManager.isAvailable(currentContactStatus.timeAvailableStart, currentContactStatus.timeAvailableEnd)){
                repository.updateContactStatus(
                    ContactStatus(
                        rowId = currentContactStatus.rowId,
                        contactJID = currentContactStatus.contactJID,
                        isAvailable = true,
                        isMuted = currentContactStatus.isMuted,
                        isBlocked = currentContactStatus.isBlocked,
                        isOnline = presence.isAvailable,
                        isTyping = currentContactStatus.isTyping,
                        lastOnlineDate = TimeDateManager.getDate(),
                        lastOnlineTime = TimeDateManager.getTime()

                ))
            }
            else{
                repository.updateContactStatus(
                    ContactStatus(
                        rowId = currentContactStatus.rowId,
                        contactJID = currentContactStatus.contactJID,
                        isAvailable = false,
                        isMuted = currentContactStatus.isMuted,
                        isBlocked = currentContactStatus.isBlocked,
                        isOnline = false,
                        isTyping = currentContactStatus.isTyping,
                        lastOnlineDate = TimeDateManager.getDate(),
                        lastOnlineTime = TimeDateManager.getTime()

                    ))
            }
        }

    }

    /**
     * Called when a roster entries are updated.
     *
     * @param addresses the XMPP addresses of the contacts whose entries have been updated.
     */
    override fun entriesUpdated(addresses: MutableCollection<Jid>?) {
        syncRoster()
    }

    /**
     * Called when roster entries are added.
     * It will add the contact into the database if they do not already exists
     * @param addresses the XMPP addresses of the contacts that have been added to the roster.
     */
    override fun entriesAdded(addresses: MutableCollection<Jid>?) {
        println("Got into the entries added function")
        xmppScope.launch {
            addresses?.forEach { jid ->
                val entityBareJid = jid.asEntityBareJidIfPossible().toString()
                if (!repository.checkIfContactExists(contactJID = entityBareJid)) {
                    generateNewContact(entityBareJid)
                    rsaEncryptionManager.addNewEventListeners(entityBareJid)
                }
            }
        }
//        addresses?.forEach { jid ->
//            generateNewContact(jid.asEntityBareJidIfPossible().toString())
//        }
    }

//    override fun processMessage(chat: org.jivesoftware.smack.chat.Chat, message: Message?) {
//        TODO("Not yet implemented")
//    }

    /**
     * Called when the server ping fails.
     */
    override fun pingFailed() {
        println("PING FAILED")
        disconnect()
    }

    /**
     * Called when a new XMPP message is received.
     * Will insert the chat into the database. If a chat session does not already exist,
     * it will first create a [ChatSession] and [GroupChat] before inserting the
     * message into database.
     * @param from BareJID of the sender
     * @param message The message sent
     * @param chat The Chat this corresponds to. This is not used
     */
    override fun newIncomingMessage(from: EntityBareJid?, message: Message?, chat: Chat?) {
        if(from.isNullOrEmpty() || message == null || message.stanzaId.isNullOrEmpty()) return
        var isMediaMessage = false
        var isEncrypted = false
        message.subjects?.forEach { subject ->
            when(subject.subject){
                "Media" -> isMediaMessage = true
                "Encrypted" -> isEncrypted = true
            }
        }
        if(isEncrypted) message.body = rsaEncryptionManager.decryptMessage(message.body)
        println("Received a message from $from and the message body is ${message.body} ")
        println("The received message is part of chat $chat")


        insertNewMessage(from.asEntityBareJidString(), groupJID = from.asEntityBareJidString(), message, date = TimeDateManager.getDate(), time = TimeDateManager.getTime(), isMediaMessage, isGroupChat = false, isEncrypted = isEncrypted)

    }


    /**
     * Function called when a message is outgoing. This will update the message status to "sent". @see [MessageStatus]
     * @param to The BareJID of the contact who the message is being sent to.
     * @param message The message being sent.
     * @param chat The chat. This is not used.
     */
    override fun newOutgoingMessage(to: EntityBareJid?, message: Message?, chat: Chat?) {
        xmppScope.launch {
            message?.stanzaId?.let { repository.updateMessageIsSent(isSent = true, messageID = it) }
        }

    }

    /**
     * Called when a contact's state has changed. This is how to detect typing, paused, read-receipts, and vacant (left)
     * @param chat The chat this refers to. This is not used.
     * @param state The state sent.
     * @param message The message (if sent)
     */
    override fun stateChanged(chat: Chat?, state: ChatState?, message: Message?) {
        //TODO("Not yet implemented")
        if(chat == null || state == null || message == null) return

        println("Got into this chat stateChanged")
//        println(chat.xmppAddressOfChatPartner)
//        println(state.declaringClass)
//        println(state.name)
//        println(state.ordinal)
//        println(message?.body)
        val contactJID = message.from.asEntityBareJidIfPossible().toString()


        when(state){
            ChatState.composing -> {
                xmppScope.launch(Dispatchers.IO){
                    repository.updateTypingStatusForChat(isContactTyping = true, contactJID = contactJID, groupJID = contactJID)
                    repository.updateMessageIsReadOutGoing(isRead = true, contactJID = contactJID)
                }

            }
            ChatState.paused, ChatState.gone, ChatState.inactive -> {
                xmppScope.launch(Dispatchers.IO){
                    repository.updateTypingStatusForChat(isContactTyping = false, contactJID = contactJID, groupJID = contactJID)
                }
            }
            ChatState.active -> {
                xmppScope.launch {
                    repository.updateMessageIsReadOutGoing(isRead = true, contactJID = contactJID)
                }

            }

            else -> return
        }
    }

    /**
     * Send typing status for the user, so long as they allowed this is the
     * user preferences.
     *
     * @param groupJID : JID of the chat session
     * @param isGroupChat : whether or not this chat session is a group chat
     */
    fun sendTypingState(groupJID: String, isGroupChat: Boolean){
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(xmppContext)
        if(!sharedPreferences.getBoolean("showTyping", true)) return
        if(!isGroupChat){
            val chat = chatManager.chatWith(JidCreate.entityBareFrom(groupJID))
            chatStateManager.setCurrentState(ChatState.composing, chat)
        }
        else{
            val mucRoom = mucManager.getMultiUserChat(JidCreate.entityBareFrom(groupJID))
            if(!mucRoom.isJoined) joinSingleRoom(groupJID, room = mucRoom)
            mucRoom.changeAvailabilityStatus("Typing", Presence.Mode.available)
        }

    }

    /**
     * Send a paused state to the chat session.
     *
     * @param groupJID : JID of the chat session
     * @param isGroupChat : whether or not this chat session is a group chat
     */
    fun sendPausedState(groupJID: String, isGroupChat: Boolean){
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(xmppContext)
        if(!sharedPreferences.getBoolean("showTyping", true)) return

        if(!isGroupChat){
            val chat = chatManager.chatWith(JidCreate.entityBareFrom(groupJID))
            chatStateManager.setCurrentState(ChatState.paused, chat)
        }
        else{
            val mucRoom = mucManager.getMultiUserChat(JidCreate.entityBareFrom(groupJID))
            if(!mucRoom.isJoined) joinSingleRoom(groupJID, room = mucRoom)
            mucRoom.changeAvailabilityStatus("Paused", Presence.Mode.available)
        }

    }

    /**
     * Send an active state to the chat session.
     * This will update the "read" status for the other user's in the
     * chat session. Will be sent according to user's preferences.
     *
     * @param groupJID : JID of the chat session
     * @param isGroupChat : whether or not this chat session is a group chat
     */
    fun sendActiveState(groupJID: String, isGroupChat: Boolean){
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(xmppContext)
        if(!sharedPreferences.getBoolean("showRead", true)) return

        if(!isGroupChat){
            val chat = chatManager.chatWith(JidCreate.entityBareFrom(groupJID))
            chatStateManager.setCurrentState(ChatState.active, chat)
        }
        else{
            val mucRoom = mucManager.getMultiUserChat(JidCreate.entityBareFrom(groupJID))
            if(!mucRoom.isJoined) joinSingleRoom(groupJID, room = mucRoom)
            mucRoom.changeAvailabilityStatus("Read", Presence.Mode.available)
        }

    }

    /**
     * Send a gone state to the chat session. This will
     * cancel the typing status of the user to the chat
     * and will ensure no faulty "read" receipts are sent
     * via @see[sendActiveState]
     *
     * @param groupJID : JID of the chat session
     * @param isGroupChat : whether or not this chat session is a group chat
     */
    fun sendGoneState(groupJID: String, isGroupChat: Boolean){
        if(!isGroupChat){
            val chat = chatManager.chatWith(JidCreate.entityBareFrom(groupJID))
            chatStateManager.setCurrentState(ChatState.gone, chat)
        }
        else{
            val mucRoom = mucManager.getMultiUserChat(JidCreate.entityBareFrom(groupJID))
            if(!mucRoom.isJoined) joinSingleRoom(groupJID, room = mucRoom)
            mucRoom.changeAvailabilityStatus("Gone", Presence.Mode.available)
        }

    }


    /**
     * Callback invoked when a new receipt got received.
     *
     *
     * `receiptId` correspondents to the message ID, which can be obtained with
     * [org.jivesoftware.smack.packet.Stanza.getStanzaId].
     *
     *
     * @param fromJid the jid that send this receipt
     * @param toJid the jid which received this receipt
     * @param receiptId the message ID of the stanza which has been received and this receipt is for.
     * This might be `null`.
     * @param receipt the receipt
     */
    override fun onReceiptReceived(fromJid: Jid?, toJid: Jid?, receiptId: String?, receipt: Stanza?) {
        xmppScope.launch {
            if (receiptId != null) {
                repository.updateMessageIsReceived(isReceived = true, messageID = receiptId)
            }
        }
    }

    /**
     * Function to register all of the needed listeners for a MUC
     *
     * @param room : XMPP MultiUserChat object
     */
    private fun addMUCListeners(room: MultiUserChat?){
        room?.addMessageListener(this)
        //room?.addParticipantListener(this)
        room?.addParticipantListener(this@XmppTapIn)
        room?.changeAvailabilityStatus("Available", Presence.Mode.available)

    }

    /**
     * Join a single room given the groupJID and room instance to join.
     *
     * @param groupJID : JID of the MUC (multi user chat)
     * @param room : MultiUserChat instance to join
     */
    private fun joinSingleRoom(groupJID: String, room: MultiUserChat?){
        xmppScope.launch(Dispatchers.IO) {
            val firebaseUID = FirebaseAuth.getInstance().currentUser!!.uid.toLowerCase(Locale.ROOT)
            val userJID = "$firebaseUID@$domain"
            val nickname = Resourcepart.from(userJID)
            val lastMessage = repository.getLastMessageFromMUC(groupJID)
            val secondsSinceLastMessage = TimeDateManager.getSecondsSinceLastMessage(localDate = lastMessage?.date, localTime = lastMessage?.time)
            val mucEnterConfigForm = room?.getEnterConfigurationBuilder(nickname)

            mucEnterConfigForm?.requestHistorySince(secondsSinceLastMessage)?.withPresence(presenceAvail)
            if (mucEnterConfigForm != null) {
                room.join(mucEnterConfigForm.build())
                addMUCListeners(room)
            }
        }

    }

    /**
     * Function to join all of the group chats the user is apart of.
     *
     */
    private fun joinAllGroupChats(){
        xmppScope.launch(Dispatchers.IO){
            val firebaseUID = FirebaseAuth.getInstance().currentUser!!.uid.toLowerCase(Locale.ROOT)
            val userJID = "$firebaseUID@$domain"
            val nickname = Resourcepart.from(userJID)
            val allGroupChats = repository.getAllGroupChatJIDS()
            allGroupChats.forEach{ groupJID ->
                val room = mucManager.getMultiUserChat(JidCreate.entityBareFrom(groupJID))
                val lastMessage = repository.getLastMessageFromMUC(groupJID)
                val secondsSinceLastMessage = TimeDateManager.getSecondsSinceLastMessage(localDate = lastMessage?.date, localTime = lastMessage?.time)
                val mucEnterConfigForm = room.getEnterConfigurationBuilder(nickname)
                println("Got Config Form for")

                mucEnterConfigForm
                    .requestHistorySince(secondsSinceLastMessage)
                    .withPresence(presenceAvail)
                room.join(mucEnterConfigForm.build())
                addMUCListeners(room)
            }
        }
    }


    /**
     * Function the create a new MUC (multi-user chatroom)
     */
    fun createNewMUCRoom(contactJIDS: List<String>, groupName: String, groupJID: String){

        val firebaseUID = FirebaseAuth.getInstance().currentUser!!.uid.toLowerCase(Locale.ROOT)
        val userJID = "$firebaseUID@$domain"

        val members = JidUtil.entityBareJidSetFrom(contactJIDS)
        val userNickname = Resourcepart.from(userJID)
        val newMuc = mucManager.getMultiUserChat(JidCreate.entityBareFrom(groupJID))

        newMuc.create(userNickname)
            .configFormManager
            .setRoomOwners(JidUtil.entityBareJidSetFrom(mutableSetOf(userJID)))
            .setMembersOnly(true)
            .submitConfigurationForm()

        newMuc.changeSubject(groupName)

        newMuc.grantMembership(members)
        newMuc.grantOwnership(members)
        members.forEach {
            newMuc.invite(it, "Because")
        }

        val groupChat = GroupChat(
            groupID = groupJID,
            contactJID = null,
            occupants = contactJIDS,
            groupName = groupName,
            isSilenced = false,
            isGroupChat = true,
            groupChatAvatarURI = null

        )

        val chatSession = ChatSession(
            chatSessionID = 0,
            userJID = userJID,
            createdBy = firebaseUID,
            dateCreated = TimeDateManager.getDate(),
            groupID = groupJID,
            isContactTyping = false,
            contactTypingName = null

        )
        if(!newMuc.isJoined){
            joinSingleRoom(groupJID, newMuc)
        }
        addMUCListeners(newMuc)
        xmppScope.launch(Dispatchers.IO){
            repository.createNewChat(chatSession, groupChat)
        }

    }

    /**
     * Function to encrypt the message for all user's in a MUC.
     * Will encrypt the message for every user, individually, based on their public key.
     * The clear text message will be deleted.
     * @param message : Smack XMPP message to encrypt
     * @param room : MutliUserChat room this message is intended for
     * @return message: Smack XMPP message with all of the encrypted messages. The clear text body of the message is deleted
     */
    private suspend fun encryptMessageForMUC(message: Message, room: MultiUserChat?) :Message{
        val firebaseUserUID = FirebaseAuth.getInstance().currentUser?.uid?.toLowerCase(Locale.ROOT)
        room?.members?.forEach { groupMember ->
            val groupMemberLocalPart = groupMember.jid.localpartOrNull.toString()
            if(groupMemberLocalPart != firebaseUserUID){
                val encryptedMessage = rsaEncryptionManager.encryptMessage(message.body, contactJID = groupMember.jid.asEntityBareJidIfPossible().asEntityBareJidString() )
                message.addBody(groupMemberLocalPart, encryptedMessage)
            }
        }
        room?.owners?.forEach { groupOwner ->
            val groupMemberLocalPart = groupOwner.jid.localpartOrNull.toString()
            if(groupMemberLocalPart != firebaseUserUID){
                val encryptedMessage = rsaEncryptionManager.encryptMessage(message.body, contactJID = groupOwner.jid.asEntityBareJidIfPossible().asEntityBareJidString() )
                message.addBody(groupMemberLocalPart, encryptedMessage)
            }
        }
        //delete plain text body of message. Since message cannot be null, set to blank
        message.body = ""

        return message
    }

    /**
     * Function to send a MUC message.
     * Will encrypt the message base on the user's preferences.
     * @param groupJID : JID of the MUC to send the message to
     * @param messageBody : The body of the message to send
     * @param isMediaMessage : whether this message is a media message
     */
    fun sendMUCMessage(groupJID: String, messageBody: String, isMediaMessage: Boolean) {
        val encryptMessage = PreferenceManager.getDefaultSharedPreferences(xmppContext).getBoolean("encrypt-messages", false)
        val userUID = FirebaseAuth.getInstance().currentUser!!.uid
        val room = mucManager.getMultiUserChat(JidCreate.entityBareFrom(groupJID))
        if (!room.isJoined) {
            joinSingleRoom(groupJID, room)
        }
        var message = Message()
        message.body = messageBody
        xmppScope.launch(Dispatchers.IO) {
            if(isMediaMessage) message.subject = "Media"
            if(encryptMessage) {
                message.addSubject("English", "Encrypted")
                message = encryptMessageForMUC(message, room)
            }

            println("Test if the stanza id is already set ${message.stanzaId}")
            message.type = Message.Type.groupchat
            val chatSessionID = repository.getChatSessionID(groupJID)
            println("I got $chatSessionID for the chat session ID")
            val messageData = Messages(
                messageID = message.stanzaId,
                chatSessionID = chatSessionID,
                messageTo = groupJID,
                messageFrom = userUID,
                messageBody = messageBody,
                isEncrypted = encryptMessage,
                isOutgoing = true,
                isIncoming = false,
                date = TimeDateManager.getDate(),
                time = TimeDateManager.getTime(),
                isMediaMessage = isMediaMessage
            )
            val messageStatus = MessageStatus(
                rowId = 0,
                messageID = message.stanzaId,
                isSent = false,
                isReceived = false,
                isRead = false,
                isDraft = false
            )
            repository.insertMessage(messageData, messageStatus)
            val tryToSendMessage = kotlin.runCatching {
                room.sendMessage(message)
            }

            tryToSendMessage.onSuccess {
                repository.updateMessageIsSent(isSent = true, messageID = message.stanzaId)
            }


        }
    }


    /**
     * Process a new message from a MUC.
     * Will not insert duplicate messages
     * @param message : message to process
     */
     override fun processMessage(message: Message?) {
        val firebaseUID = FirebaseAuth.getInstance().currentUser!!.uid.toLowerCase(Locale.ROOT)
        val userJID = "$firebaseUID@$domain"
        var isMediaMessage = false
        var isEncrypted = false
        message?.subjects?.forEach { subject ->
            when(subject.subject){
                "Media" -> isMediaMessage = true
                "Encrypted" -> isEncrypted = true
            }
        }
        println("Got a message in group Chat")
        if(message == null){
            println("Apparently the message was null In Group Chat")
            return
        }

        if(message.from.resourceOrEmpty.toString() == userJID){
            println("this message was from myself?")
            return
        }

        println("About to insert the MUC message")
        if(isEncrypted){
            println("About to insert the MUC message that was encrypted")
            message.bodies.forEach { encryptedMessageBody ->
                println("Iterating over message Bodies")
                if(encryptedMessageBody.language == firebaseUID){
                    val decryptedMessage = Message()
                    decryptedMessage.stanzaId = message.stanzaId
                    decryptedMessage.to = message.to
                    decryptedMessage.from = message.from
                    val testDecrypt = rsaEncryptionManager.decryptMessage(encryptedMessageBody.message)
                    println("Stuff in testDecrypt is $testDecrypt")
                    decryptedMessage.body = testDecrypt
                    println("The message in messageBody is ${encryptedMessageBody.message}")
                    println("Decrypted Message Body = ${decryptedMessage.body}")
                    insertNewMessage(from = message.from.resourceOrEmpty.toString(), groupJID = message.from.asEntityBareJidOrThrow().toString(), message = decryptedMessage, time = TimeDateManager.getTime(), date = TimeDateManager.getDate(), isMediaMessage = isMediaMessage, isGroupChat = true, isEncrypted = true)
                    }
                }
            }
        else {
            println("Appartenly MUC message was not encrypted")
            insertNewMessage(from = message.from.resourceOrEmpty.toString(), groupJID = message.from.asEntityBareJidOrThrow().toString(), message = message, time = TimeDateManager.getTime(), date = TimeDateManager.getDate(), isMediaMessage = isMediaMessage, isGroupChat = true, isEncrypted = false)
        }



    }

    /**
     * Called when the an invitation to join a MUC room is received.
     *
     *
     *
     * If the room is password-protected, the invitee will receive a password to use to join
     * the room. If the room is members-only, the the invitee may be added to the member list.
     *
     * @param conn the XMPPConnection that received the invitation.
     * @param room the room that invitation refers to.
     * @param inviter the inviter that sent the invitation. (e.g. crone1@shakespeare.lit).
     * @param reason the reason why the inviter sent the invitation.
     * @param password the password to use when joining the room.
     * @param message the message used by the inviter to send the invitation.
     * @param invitation the raw invitation received with the message.
     */

    override fun invitationReceived(
        conn: XMPPConnection?,
        room: MultiUserChat?,
        inviter: EntityJid?,
        reason: String?,
        password: String?,
        message: Message?,
        invitation: MUCUser.Invite?
    ) {

        println("Got an Invite!!")
        if(room == null) return
        val firebaseUID = FirebaseAuth.getInstance().currentUser!!.uid.toLowerCase(Locale.ROOT)
        val userJID = "$firebaseUID@$domain"
        val userNickname = Resourcepart.from(userJID)
        room.join(userNickname)
        addMUCListeners(room)
        val occupants = mutableListOf<String>()
        val owner = room.owners[0].jid.asEntityBareJidIfPossible().toString()

        //Add all members of the group to the occupants list, making sure not to add the user
        room.members.forEach {
            if(it.jid.asEntityBareJidIfPossible().localpart.toString() != firebaseUID){
                occupants.add(it.jid.asEntityBareJidIfPossible().toString())
            }
        }

        //Add all owners to the occupants, since owners are not considered members
        room.owners.forEach {
            if(it.jid.asEntityBareJidIfPossible().localpart.toString() != firebaseUID){
                occupants.add(it.jid.asEntityBareJidIfPossible().toString())
            }
        }

        val groupChat = GroupChat(
            groupID = room.room.asEntityBareJidString(),
            contactJID = null,
            occupants = occupants.distinct(),
            groupName = room.subject,
            isSilenced = false,
            isGroupChat = true,
            groupChatAvatarURI = null

        )

        val chatSession = ChatSession(
            chatSessionID = 0,
            userJID = FirebaseAuth.getInstance().currentUser!!.uid,
            groupID = room.room.asEntityBareJidString(),
            dateCreated = TimeDateManager.getDate(),
            createdBy = owner,
            isContactTyping = false,
            contactTypingName = null
        )

        xmppScope.launch(Dispatchers.IO){
            occupants.forEach {
                if(!repository.checkIfContactExists(it)) generateNewContact(it)
            }
            repository.createNewChat(chatSession, groupChat)

        }
//        room.removeMessageListener(this)
//        room.leave()
//        room.join(userNickname)
//        addMUCListeners(room)

    }

    /**
     * Called when the invitee declines the invitation.
     *
     * @param invitee the invitee that declined the invitation. (e.g. hecate@shakespeare.lit).
     * @param reason the reason why the invitee declined the invitation.
     * @param message the message used to decline the invitation.
     * @param rejection the raw decline found in the message.
     */
    override fun invitationDeclined(
        invitee: EntityBareJid?,
        reason: String?,
        message: Message?,
        rejection: MUCUser.Decline?
    ) {
    }


    /**
     * Process the presence of a user in a MUC.
     * Either if they typing, active, or gone
     *
     * @param presence : presence received from the user
     */
    override fun processPresence(presence: Presence?) {
        println("Got a presence")
        val userJID = "${FirebaseAuth.getInstance().currentUser!!.uid.toLowerCase(Locale.ROOT)}@$domain"
        if(presence == null || presence.from == null || presence.from.resourceOrEmpty.toString() == userJID){
            println("Either prescen of presce.from was null")
            return
        }
        else println("Got this far in presence")
        val groupJID = presence.from.asEntityBareJidIfPossible().toString()
        val contactJID = presence.from.resourceOrEmpty.toString()
        println("The status was ${presence.status} from $contactJID")
        when(presence.status){
            "Typing" -> {
                println("status was typing")
                xmppScope.launch(Dispatchers.IO){
                    repository.updateTypingStatusForChat(isContactTyping = true, contactJID = contactJID, groupJID = groupJID)
                    repository.updateMessageIsReadOutGoing(isRead = true, contactJID = groupJID)
                }

            }
            "Gone", "Paused", "Stopped"-> {
                println("status was gone, or paused")
                xmppScope.launch(Dispatchers.IO){
                    repository.updateTypingStatusForChat(isContactTyping = false, contactJID = contactJID, groupJID = groupJID)
                }
            }
            "Read" -> {
                println("status was Available")
                xmppScope.launch {
                    repository.updateMessageIsReadOutGoing(isRead = true, contactJID = groupJID)
                }

            }

            else -> {
                println("It was none of the above")
                return
            }
        }

    }

    /**
     * Function to send a fil to the user
     *
     * @param mediaFile : File to send to the chat session
     * @param groupJID : JID of the chat session to send to
     * @param isGroupChat : whether this chat session is a group chat
     */
    fun sendFile(mediaFile: File, groupJID: String, isGroupChat: Boolean) {
        val httpUpload = HttpFileUploadManager.getInstanceFor(connection)
        val uploadProgressListener = UploadProgressListener { uploadedBytes, totalBytes ->

        }
        //messagesURI.toFile()
        val upload = httpUpload.uploadFile(mediaFile, uploadProgressListener)

        if(isGroupChat) sendMUCMessage(groupJID = groupJID, upload.toURI().toString(), true)
        else sendMessage(contactJID = groupJID, upload.toURI().toString(), true)

    }

    /**
     * Function to send a notification to the user from a new message.
     * Will not send a notification if the application is in the foreground @see[appLifecycleDetector]
     * or if the user has chosen to silence all group chats, and the current chat session is a group chat.
     * Will make the notification silent based on user's available time.
     * @param messageBody : map of the message object. @example [map["Body"] or map["From"]]
     * @param groupJID : JID of the chat session
     * @param isGroupChat : whether this chat is a grop chat
     */
    private fun sendNotification(messageBody: Map<String, String>, groupJID: String, isGroupChat: Boolean) {
//        !appLifecycleDetector.isInBackground
        if(!BackGroundDetector.isInBackground){
            println("App is not in background")
            println("Value is ${BackGroundDetector.isInBackground}")
            return
        }

        val preferenceManager = PreferenceManager.getDefaultSharedPreferences(xmppContext)

        if((isGroupChat && preferenceManager.getBoolean("silenceAllGroupChats", false)) || repository.isChatSessionSilenced(groupJID)){
            println("Either group chats are silenced, or this chat session is silenced")
            return
        }

        val dayPair = TimeDateManager.getDayForPreferences()
        val startTime = preferenceManager.getString(dayPair["startTime"], "08:00")
        val endTime = preferenceManager.getString(dayPair["endTime"], "17:00")
        if(startTime != null && endTime != null) {
            println("Available value is: ${TimeDateManager.isAvailable(startTime = startTime, endTime = endTime)}")

        }
        else println("They are null")

        println("Startime and Endtime is $startTime $endTime")
        val isSilent =
            if(startTime != null && endTime != null){
            !TimeDateManager.isAvailable(startTime = startTime, endTime = endTime)
            }
            else false




        val intent = Intent(xmppContext, LoginPage::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            xmppContext, 0 /* Request code */, intent,
            PendingIntent.FLAG_ONE_SHOT
        )

        val channelId = "Changing it"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(xmppContext, channelId)
            .setSmallIcon(R.drawable.new_message_notification)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    xmppContext.resources,
                    R.drawable.logo
                )
            )
            .setContentTitle(messageBody["From"])
            .setContentText(messageBody["Body"])
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        if(isSilent){
            println("Notification Should be silent")
            notificationBuilder.setNotificationSilent()
        }


        val notificationManager = xmppContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager


        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(1 /* ID of notification */, notificationBuilder.build())
        println("At the end of notification showing")
    }
    /**
     * Companion object that is called to get the single instance of [XmppTapIn]
     * By Definition, object's in Kotlin are always singletons.
     */
    companion object{

        @Volatile
        private var INSTANCE: XmppTapIn? = null

        fun getXmppTapIn(repository: Repository, applicationContext: Context): XmppTapIn {
            val tempInsta = INSTANCE
            if(tempInsta != null) return tempInsta

            synchronized(this){
                val instance = XmppTapIn()
                instance.appLifecycleDetector = AppLifecycleDetector.getAppLifeCycle()
                instance.repository = repository
                instance.rsaEncryptionManager = RSAEncryptionManager.getRSAEncryptionManagerInstance(repository)
                instance.xmppContext = applicationContext
                instance.connection = XMPPTCPConnection(instance.config)
                INSTANCE = instance
                return instance
            }
        }

    }


 }