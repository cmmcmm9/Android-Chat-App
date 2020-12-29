package firebaseMessaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.tapin.LoginPage
import com.example.tapin.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import database.TapInDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.minidns.dnsserverlookup.android21.AndroidUsingLinkProperties
import repository.Repository
import xmpp.XmppTapIn

/**
 * Worker Class to handle scheduled jobs (queued by @see[TapInFirebaseMessaging])
 * Will retrieve offline messages and new contact information upon changes.
 *
 * @constructor
 *
 *
 * @param appContext : context of the application
 * @param workerParams : parameters for the scheduled job (what kind of job to handle)
 */
class TapInWorker(appContext: Context, workerParams: WorkerParameters) : Worker(
    appContext,
    workerParams
) {
    init {
        AndroidUsingLinkProperties.setup(appContext)
    }
    private val workerScope = CoroutineScope(Job() + Dispatchers.IO)
    private val database = TapInDatabase.getDatabase(appContext, workerScope)
    private val messageData = workerParams.inputData
    private val offlineSingle = "offline-single"
    private val offlineMuc = "offline-muc"
    private val vcardUpdated = "vcard-updated"
    private val contactJID = "contactJID"
    private val customToken = "customToken"
    private val roomJID = "customToken"
    private val time = "Time"
    private val messageType = "KtorMessage"

    private val repository = Repository(
        userDao = database.userDao(),
        userSettingsDao = database.userSettingsDao(),
        userAndUserSettingsDao = database.userAndUserSettingsDao(),
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

    private val xmppTapIn = XmppTapIn.getXmppTapIn(repository, applicationContext)

    /**
     * Function to do the work defined in @see[mWorkerParams]
     * In all jobs, the firebase message must have recieved a one time
     * firebase access token to use to sign into Openfire.
     *
     * @return
     */
    override fun doWork(): Result {
        Log.d(TAG, "Performing long running task in scheduled job")

        val customAuthToken = messageData.getString(customToken)
        println("Cusotm auth token is: $customAuthToken")
        println("Message type is: ${messageData.getString(messageType)}")
        when(messageData.getString(messageType)){

            offlineSingle -> {

                if (customAuthToken != null) {
                    FirebaseAuth.getInstance().signInWithCustomToken(customAuthToken)
                        .addOnCompleteListener { signIn ->
                            if (signIn.isSuccessful) signIn.result?.user?.let {
                                getSingleOfflineMessage(
                                    it
                                )
                            }
                        }
                }

            }
            offlineMuc -> {
                if (customAuthToken != null) {
                    FirebaseAuth.getInstance().signInWithCustomToken(customAuthToken)
                        .addOnCompleteListener { signIn ->
                            if (signIn.isSuccessful) signIn.result?.user?.let {
                                getSingleOfflineMessage(
                                    it
                                )
                            }
                        }
                }

            }
            vcardUpdated -> {
                val contactJID = messageData.getString(contactJID)
                if (customAuthToken != null) {
                    FirebaseAuth.getInstance().signInWithCustomToken(customAuthToken)
                        .addOnCompleteListener { signIn ->
                            if (signIn.isSuccessful) signIn.result?.user?.let {
                                updateVcard(
                                    it,
                                    contactJID = contactJID
                                )
                            }
                        }
                }

            }
        }
        return Result.success()
    }

    /**
     * Function to retrieve the offline single message (one-to-one chat)
     * Will sign the user in with their one time access token to connect to the server.
     * @param firebaseUser : the firebase user that was logged in with the access token
     */
    private fun getSingleOfflineMessage(firebaseUser: FirebaseUser){

        println("Fire base user in Worker is ${firebaseUser.uid} and email is verified: ${firebaseUser.isEmailVerified}")
        firebaseUser.getIdToken(false).addOnCompleteListener { getToken ->

            if(getToken.isSuccessful){
                workerScope.launch(Dispatchers.IO){
                    xmppTapIn.connectAndLoginWithoutListeners(
                        username = firebaseUser.uid,
                        authToken = getToken.result?.token
                    )
                    val messageToDisplay = xmppTapIn.getOfflineMessages()
                    //IncomingMessageHandler.sendNotification(messageBody = messageToDisplay, groupJID = messageToDisplay["From"], )
                    sendNotification(messageToDisplay)
                    xmppTapIn.setUnAvailable()
                    xmppTapIn.disconnect()

            }

            }
        }
    }

    private fun getMUCOfflineMessage(){


    }

    private fun updateVcard(firebaseUser: FirebaseUser, contactJID: String?){

        println("Fire base user in Worker.updateVccard is ${firebaseUser.uid} and email is verified: ${firebaseUser.isEmailVerified}")
        firebaseUser.getIdToken(false).addOnCompleteListener { getToken ->

            if(getToken.isSuccessful){
                workerScope.launch(Dispatchers.IO){
                    xmppTapIn.connectAndLoginWithoutListeners(
                        username = firebaseUser.uid,
                        authToken = getToken.result?.token
                    )
                    contactJID?.let { xmppTapIn.updateContactFromVcard(contactJID) }
                    xmppTapIn.setUnAvailable()
                    xmppTapIn.disconnect()

                }
            }
        }
    }

    /**
     * Function to send a notification to the user about an offline messasge.
     *
     * @param messageBody : Map<Key, Value>, example: Map["From"], Map["Body"]
     */
    private fun sendNotification(messageBody: Map<String, String>) {
        val intent = Intent(applicationContext, LoginPage::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0 /* Request code */, intent,
            PendingIntent.FLAG_ONE_SHOT
        )

        val channelId = "My ID"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    Resources.getSystem(),
                    R.drawable.asset1ldpi
                )
            )
            .setSmallIcon(R.drawable.asset1ldpi)
            .setContentTitle(messageBody["From"])
            .setContentText(messageBody["Body"])
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)


        val notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager


        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build())
    }

    companion object {
        private val TAG = "MyWorker"
    }
}