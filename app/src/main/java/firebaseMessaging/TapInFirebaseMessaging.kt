package firebaseMessaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.example.tapin.LoginPage
import com.example.tapin.R
import com.example.tapin.R.drawable.asset1ldpi
import com.example.tapin.main_page
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.squareup.picasso.Picasso
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import java.util.*

/**
 * Firebase Messaging Service to handle received Firebase Cloud Messages.
 * Will be called whenever a new FCM (firbase cloud message) is recieved.
 * Runs as a service.
 *
 */
class TapInFirebaseMessaging : FirebaseMessagingService() {
    private val offlineSingle = "offline-single"
    private val offlineMuc = "offline-muc"
    private val vcardUpdated = "vcard-updated"
    private val ktorMessage = "KtorMessage"
    private val updateAvatar = "update-avatar"
    private val avatarUrl = "avatar-url"
    private val contactJID = "contactJID"
    private val customToken = "customToken"
    private val roomJID = "customToken"
    private val time = "Time"
    private val messageType = "MessageType"
    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
//        println("Message type is: ${remoteMessage.messageType}")
//        println("Message is notification: ${remoteMessage.notification}")
//        println("Got into here")
//        sendNotification("Before Job!")


        // [START_EXCLUDE]
        // There are two types of messages data messages and notification messages. Data messages are handled
        // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
        // traditionally used with GCM. Notification messages are only received here in onMessageReceived when the app
        // is in the foreground. When the app is in the background an automatically generated notification is displayed.
        // When the user taps on the notification they are returned to the app. Messages containing both notification
        // and data payloads are treated as notification messages. The Firebase console always sends notification
        // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options
        // [END_EXCLUDE]

        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload. If so, determine if it a long job or short job.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            val workerParams = Data.Builder()
            //val remoteMessageData = mutableMapOf<String, String>()
            remoteMessage.data.entries.forEach {
                workerParams.putString(it.key, it.value)
            }

            var longJob = false
            when(remoteMessage.data["KtorMessage"]){
                offlineSingle -> {
                    longJob = true
                }
                offlineMuc ->{
                    longJob = true
                }
                vcardUpdated ->{
                    longJob = true
                }
            }

            if (/* Check if data needs to be processed by long running job */ longJob) {
                // For long-running tasks (10 seconds or more) use WorkManager.
                scheduleJob(workerParams.build())
            } else {
                when(remoteMessage.data[ktorMessage]){
                    updateAvatar -> remoteMessage.data[avatarUrl]?.toUri()?.let {avatarUri ->
                        println("About to invalidate old Picasso URL")
                        println("")
                        invalidateOldAvatarURL(avatarUri)
                    }
                }
            }
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }

        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
        //sendNotification(messageBody)
    }
    // [END receive_message]

    // [START on_new_token]
    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token. Will trigger the @see[sendRegistrationToServer]
     * to update a new token value.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.
        sendRegistrationToServer(token)
    }
    // [END on_new_token]

    /**
     * Schedule async work using WorkManager.
     * Job types are: offline messages, and contact-info change
     */
    private fun scheduleJob(data: Data) {
        // [START dispatch_job]
        val work = OneTimeWorkRequest.Builder(TapInWorker::class.java)
        work.setInputData(data)
        WorkManager.getInstance().beginWith(work.build()).enqueue()
        // [END dispatch_job]
    }

    /**
     * Invalidate the cached avatar image upon a newly uploaded avatar.
     * Next time the avatar is request, it will reload the new image.
     *
     * @param avatarUri : the URI of the avatar (https://tapinapp.com:8090/avatar/SOME_VALUE)
     */
    private fun invalidateOldAvatarURL(avatarUri: Uri){
        Picasso.get().invalidate(avatarUri)
        Picasso.get().load(avatarUri)
    }

    /**
     * Upload the user's firebase cloud token to the firebase realtime database.
     * The database node is "users" and the structure of the data is:
     * { firebaseUID: { token: "TOKEN_VALUE" } }
     * Is needed by the Ktor server in order to send Firebase Cloud Messages to this device.
     *
     * @param token The new token.
     */
    private fun sendRegistrationToServer(token: String?) {

        println("In send to registration Token")
        Log.d(TAG, "sendRegistrationTokenToServer($token)")
        val database = Firebase.database
        val myRef = database.getReference("users")
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            myRef.child(user.uid.toLowerCase(Locale.ROOT)).child("token").setValue(token).addOnCompleteListener{
                if(it.isSuccessful){
                    println("Succesful wrote to database")
                }
                else{
                    println("Failed because ${it.exception}")
                }
            }
        }else println("User is null")

    }

    /**
     * Create and show a simple notification containing the received FCM message.
     *
     * @param messageBody FCM message body received.
     */
    private fun sendNotification(messageBody: String) {
        val intent = Intent(this, LoginPage::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
            PendingIntent.FLAG_ONE_SHOT)

        val channelId = getString(R.string.default_notification_channel_id)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setLargeIcon(BitmapFactory.decodeResource(resources, asset1ldpi))
            .setSmallIcon(asset1ldpi)
            .setContentTitle("TapIn In Message")
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)


        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build())
    }

    companion object {

        private const val TAG = "MyFirebaseMsgService"
    }
}

