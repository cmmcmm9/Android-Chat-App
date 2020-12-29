package com.example.tapin

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tapin.ui.ImageViewFragment
import com.google.firebase.auth.FirebaseAuth
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_in_chats.*
import ktorClient.secureAvatarURL
import list.adapter.MessagePictureClickListener
import list.adapter.MessagesListAdapter
import list.adapter.SettingsSpinnerArrayAdapter
import viewmodel.MessageViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.properties.Delegates

private const val DRAWABLE = "drawable"
private const val CONTACTJID = "CONTACTJID"
private const val DISPLAYNAME = "DISPLAYNAME"
private const val ISGROUPCHAT = "ISGROUPCHAT"
const val RESULT_LOAD_IMAGE_TO_SEND = 0
const val RESULT_LOAD_IMAGE_FOR_GROUPCHAT_AVATAR = 1
private lateinit var contactJID: String
private lateinit var displayName: String
private lateinit var messageViewModel: MessageViewModel


private var isGroupChat by Delegates.notNull<Boolean>()

private val settingsList = mutableListOf("", "View Members", "Mute Conversation", "Delete Conversation", "Change Group Avatar" )

class in_chats : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_chats)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val oldFragment = supportFragmentManager.findFragmentByTag("imageFragment")
        oldFragment?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }

        if(savedInstanceState != null && intent.extras?.getString(CONTACTJID).isNullOrBlank()){
            displayName = savedInstanceState.getString(DISPLAYNAME).toString()
            contactJID = savedInstanceState.getString(CONTACTJID).toString()
            isGroupChat = savedInstanceState.getBoolean(ISGROUPCHAT)
        }
        else{
            isGroupChat = intent.extras?.getBoolean(ISGROUPCHAT)!!
            displayName = intent.extras?.getString(DISPLAYNAME).toString()
            if(!displayName.isBlank()){
                if(displayName.length >= 10) displayName = "${displayName.subSequence(0, 7)}..."
            }
            contactJID = intent.extras?.getString(CONTACTJID).toString()
        }

        val imageClickListener = object : MessagePictureClickListener {
            override fun onImageClicked(drawable: Drawable) {
                //showFullScreenImage(drawable)
            }

            override fun onImageClickedWithUri(messagesUri: String) {
                val fragment = ImageViewFragment()
                val bundle = getBundle()
                bundle.putString(DRAWABLE, messagesUri)
                fragment.arguments = bundle
                setContentView(R.layout.activity_in_chats)
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.in_chats_layout, fragment)
                    .addToBackStack("imageFragment")
                    .commit()
                supportActionBar?.setDisplayHomeAsUpEnabled(true)

            }
        }
        


        val displayContactName = contact_name
        displayContactName.text = displayName



        val typingAnimation = typing_indicator_lottie
        val typingDisplayName = typing_status_display_name

        val messageRecyclerView = recyclerview_message_list
        val messageListAdapter = MessagesListAdapter(this, imageClickListener)
        messageRecyclerView.adapter = messageListAdapter
        val layoutManager = LinearLayoutManager(this).apply {
            this.stackFromEnd = true
        }
        messageRecyclerView.layoutManager =  layoutManager


        val userUID = FirebaseAuth.getInstance().currentUser?.uid


        messageViewModel = ViewModelProvider(this).get(MessageViewModel::class.java)

        if (contactJID.isNotEmpty() && userUID != null) {
            Picasso.get().load("$secureAvatarURL${contactJID.substringBefore('@')}").into(in_chat_avatar)
            messageViewModel.setMessagesToDisplay(contactJID, userUID, isGroupChat)
            messageViewModel.setContactDisplayNames(contactJID)
            println("GROUP JID is : $contactJID")
            messageViewModel.messagesToDisplay.observe(this, { messagesToDisplay ->
                messagesToDisplay?.let {
                    //println("Data in messages is $messagesToDisplay")
                    //println("data set changed")
                    messageListAdapter.setDisplayMessagesData(messagesToDisplay)
                    val position =
                        (messageRecyclerView.adapter as MessagesListAdapter).itemCount - 1
                    messageRecyclerView.scrollToPosition(position)

                    if (messagesToDisplay.isNotEmpty()) {

                        if(messagesToDisplay[0].isSilenced) settingsList[2] = "UnMute Conversation"
                        else settingsList[2] = "Mute Conversation"
                        if(messagesToDisplay[0].createdBy.toLowerCase(Locale.ROOT) != FirebaseAuth.getInstance().currentUser!!.uid.toLowerCase(Locale.ROOT)){
                            settingsList.remove("Change Group Avatar")
                        }


                        when (messagesToDisplay[0].isContactTyping) {
                            true -> messagesToDisplay[0].contactTypingName?.let { contactTypingName ->
                                showTypingIndicator(contactTypingName)
                            }
                            false -> hideTypingIndicator()
                        }

                        messagesToDisplay.forEach {
                            if (it.isIncoming && !it.isRead) messageViewModel.sendActiveState(
                                contactJID,
                                isGroupChat
                            )
                        }
                    }
                }

            })
        }

        val sendButton = button_chatbox_send
        val chatBox = edittext_chatbox
        sendButton.setOnClickListener {
            if(chatBox.text.isNullOrEmpty()) {
                Toast.makeText(this, "Cannot Send Empty Message", Toast.LENGTH_SHORT).show()
            }
            else{
                val messageBody = chatBox.text.toString()
                if(contactJID.isNotEmpty()) {
                    messageViewModel.sendMessage(contactJID, messageBody)
                }
                chatBox.text.clear()

            }
        }

        var isTyping by Delegates.observable(false){ property, oldValue, newValue ->
            if(oldValue == newValue) return@observable
            if(newValue) messageViewModel.sendTypingStatus(contactJID, isGroupChat)
            else messageViewModel.sendPausedState(contactJID, isGroupChat)
        }

        chatBox.addTextChangedListener(object : TextWatcher {
            var countDownTimer: CountDownTimer? = null

            /**
             * This method is called to notify you that, within `s`,
             * the `count` characters beginning at `start`
             * are about to be replaced by new text with length `after`.
             * It is an error to attempt to make changes to `s` from
             * this callback.
             */
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            /**
             * This method is called to notify you that, within `s`,
             * the `count` characters beginning at `start`
             * have just replaced old text that had length `before`.
             * It is an error to attempt to make changes to `s` from
             * this callback.
             */
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                countDownTimer?.cancel()
                isTyping = true

                countDownTimer = object : CountDownTimer(1500, 1000) {
                    /**
                     * Callback fired on regular interval.
                     * @param millisUntilFinished The amount of time until finished.
                     */
                    override fun onTick(millisUntilFinished: Long) {

                    }

                    /**
                     * Callback fired when the time is up.
                     */
                    override fun onFinish() {
                        println("Stopped Typing")
                        isTyping = false
                    }

                }.start()

            }


            /**
             * This method is called to notify you that, somewhere within
             * `s`, the text has been changed.
             * It is legitimate to make further changes to `s` from
             * this callback, but be careful not to get yourself into an infinite
             * loop, because any changes you make will cause this method to be
             * called again recursively.
             * (You are not told where the change took place because other
             * afterTextChanged() methods may already have made other changes
             * and invalidated the offsets.  But if you need to know here,
             * you can use [Spannable.setSpan] in [.onTextChanged]
             * to mark your place and then look up from here where the span
             * ended up.
             */
            override fun afterTextChanged(s: Editable?) {

            }

        })

        val backButtonToolBar = toolbar_button_in_chats
        backButtonToolBar.setOnClickListener {
            if(contactJID.isNotEmpty()) messageViewModel.sendGoneState(contactJID, isGroupChat)
            val intent = Intent(this, main_page::class.java)
            startActivity(intent)
        }

        val spinner = inChatsSpinner
        val spinnerAdapter = SettingsSpinnerArrayAdapter(this, settingsList)
        spinner.adapter = spinnerAdapter
        spinner.setSelection(0)
        spinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            /**
             *
             * Callback method to be invoked when an item in this view has been
             * selected. This callback is invoked only when the newly selected
             * position is different from the previously selected position or if
             * there was no selected item.
             *
             * Implementers can call getItemAtPosition(position) if they need to access the
             * data associated with the selected item.
             *
             * @param parent The AdapterView where the selection happened
             * @param view The view within the AdapterView that was clicked
             * @param position The position of the view in the adapter
             * @param id The row id of the item that is selected
             */
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                when(settingsList[position]){
                    "View Members" -> {
                        messageViewModel.contactDisplayNames.observe(this@in_chats) { contactsDisplayNames ->
                            contactsDisplayNames?.let {
                                showChatMembersDialog(it)
                            }

                        }

                    }
                    "Mute Conversation" -> muteChatDialog(contactJID)

                    "UnMute Conversation" -> unMuteChatDialog(contactJID)
                    "Delete Conversation" -> deleteChatDialog(contactJID)
                    "Change Group Avatar" -> startPhotoPickerIntent(RESULT_LOAD_IMAGE_FOR_GROUPCHAT_AVATAR)
                }
                spinner.setSelection(0)
            }

            /**
             * Callback method to be invoked when the selection disappears from this
             * view. The selection can disappear for instance when touch is activated
             * or when the adapter becomes empty.
             *
             * @param parent The AdapterView that now contains no selected item.
             */
            override fun onNothingSelected(parent: AdapterView<*>?) {
                spinner.setSelection(0)
            }

        }

        val imageSelector = select_image_button
        imageSelector.setOnClickListener {
            startPhotoPickerIntent(RESULT_LOAD_IMAGE_TO_SEND)
        }

//        val searchView = search_in_chat
//        searchView.setOnClickListener {
//            text_to_search_in_chats.isGone = false
//            text_to_search_in_chats.isVisible = true
//        }
//        text_to_search_in_chats.doOnTextChanged { text, start, before, count ->
//            messageViewModel.messagesToDisplay.observe(this){ messageDisplayed ->
//                messageDisplayed?.let { messages ->
//                    messages.forEachIndexed { index, displayMessagesData ->
//                        if(displayMessagesData.messageBody.contains(
//                                text.toString(),
//                                ignoreCase = true
//                            )){
//                            messageRecyclerView.scrollToPosition(index)
//                        }
//                    }
//                }
//            }
//        }








    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(resultCode != RESULT_OK){
            println("Invalid Image option")
            return
        }

            when(requestCode){
                RESULT_LOAD_IMAGE_TO_SEND -> {
                    println("Got a valid image selected")
                    data?.data?.let { uri ->
                        val folderName = "TapinApp Media"
                        val dir = applicationContext.getDir(folderName, Context.MODE_PRIVATE)
                        if(!dir.exists()) dir.mkdirs()
                        val targetFile = File("${dir.absolutePath}/Temp${uri.lastPathSegment}")
                        val outputStream = FileOutputStream(targetFile)
                        contentResolver.openInputStream(uri)?.copyTo(outputStream)
                        messageViewModel.sendFile(
                            groupJID = contactJID,
                            fileToSend = targetFile,
                            isGroupChat
                        )
                        outputStream.close()


                    }
                }

                RESULT_LOAD_IMAGE_FOR_GROUPCHAT_AVATAR -> {
                    data?.data?.let { uri ->
                        val folderName = "TapinApp Media"
                        val dir = applicationContext.getDir(folderName, Context.MODE_PRIVATE)
                        if (!dir.exists()) dir.mkdirs()
                        val targetFile = File("${dir.absolutePath}/Temp")
                        val outputStream = FileOutputStream(targetFile)
                        contentResolver.openInputStream(uri)?.copyTo(outputStream)
                        outputStream.close()
                        val filePair = Pair(contactJID.substringBefore('@') , targetFile)
                        val avatar = in_chat_avatar
                        val groupJID = contactJID
                            FirebaseAuth.getInstance().currentUser!!.uid.toLowerCase(Locale.ROOT)

                        messageViewModel.uploadAvatarForGroupChat(
                            mapOf(filePair),
                            mapOf(Pair("is-group-chat", "true")),
                            avatar,
                            groupJID
                        )
                    }
                }
            }
    }

    private fun startPhotoPickerIntent(resultCode: Int){
        val pickPhotoIntent = Intent(Intent.ACTION_PICK)
        pickPhotoIntent.type = "image/*"
        startActivityForResult(pickPhotoIntent, resultCode)
    }
    private fun deleteChatDialog(contactJID: String){
        val titleText = "Delete Conversation"
        val dialogBuilder = AlertDialog.Builder(this)
        val layoutView = layoutInflater.inflate(R.layout.delete_content_dialog, null) as View
        val title = layoutView.findViewById(R.id.delete_content_title_dialog) as TextView
        title.text = titleText
        val deleteContactButton = layoutView.findViewById(R.id.confirmDeleteButton) as Button
        val cancelDeleteContactButton = layoutView.findViewById(R.id.cancelDeleteButton) as Button
        dialogBuilder.setView(layoutView)
        val alertDialog = dialogBuilder.create()
        alertDialog.show()
        deleteContactButton.setOnClickListener {
            messageViewModel.deleteChatCascade(contactJID)
            alertDialog.dismiss()
            startActivity(Intent(this, main_page::class.java))
        }
        cancelDeleteContactButton.setOnClickListener {
            alertDialog.dismiss()
        }
    }

    private fun muteChatDialog(contactJID: String){
        val titleText = "Mute Conversation"
        val dialogBuilder = AlertDialog.Builder(this)
        val layoutView = layoutInflater.inflate(R.layout.delete_content_dialog, null) as View
        val title = layoutView.findViewById(R.id.delete_content_title_dialog) as TextView
        title.text = titleText
        val muteContactButton = layoutView.findViewById(R.id.confirmDeleteButton) as Button
        val confirm = "Confirm"
        muteContactButton.text = confirm
        val cancelDialogButton = layoutView.findViewById(R.id.cancelDeleteButton) as Button
        dialogBuilder.setView(layoutView)
        val alertDialog = dialogBuilder.create()
        alertDialog.show()
        muteContactButton.setOnClickListener {
            messageViewModel.updateSilenceGroupChat(true, contactJID)
            alertDialog.dismiss()
        }
        cancelDialogButton.setOnClickListener {
            alertDialog.dismiss()
        }
    }

    private fun unMuteChatDialog(contactJID: String){
        val titleText = "UnMute Conversation"
        val dialogBuilder = AlertDialog.Builder(this)
        val layoutView = layoutInflater.inflate(R.layout.delete_content_dialog, null) as View
        val title = layoutView.findViewById(R.id.delete_content_title_dialog) as TextView
        title.text = titleText
        val muteContactButton = layoutView.findViewById(R.id.confirmDeleteButton) as Button
        val confirm = "Confirm"
        muteContactButton.text = confirm
        val cancelDialogButton = layoutView.findViewById(R.id.cancelDeleteButton) as Button
        dialogBuilder.setView(layoutView)
        val alertDialog = dialogBuilder.create()
        alertDialog.show()
        muteContactButton.setOnClickListener {
            messageViewModel.updateSilenceGroupChat(false, contactJID)
            alertDialog.dismiss()
        }
        cancelDialogButton.setOnClickListener {
            alertDialog.dismiss()
        }

    }

    private fun showChatMembersDialog(listOfMembers: List<String>){
        println("Show chat memebers got $listOfMembers")
        val dialogBuilder = AlertDialog.Builder(this)
        val layoutView = layoutInflater.inflate(R.layout.display_chat_members, null) as View
        val adapter = ArrayAdapter(
            this,
            R.layout.display_member_names_list_litem_layout,
            listOfMembers
        )
        val listView = layoutView.findViewById(R.id.membersOfChatList) as ListView
        listView.adapter = adapter
        val dismissButton = layoutView.findViewById(R.id.dismissDisplayChatMembersDialog) as Button
        dialogBuilder.setView(layoutView)
        val alertDialog = dialogBuilder.create()
        alertDialog.show()
        dismissButton.setOnClickListener {
            alertDialog.dismiss()
        }

    }

    private fun showTypingIndicator(contactTypingName: String){
        val typingAnimation = typing_indicator_lottie
        val typingDisplayName = typing_status_display_name
        typingAnimation.isVisible = true
        typingAnimation.playAnimation()
        val formatContactTyping = "$contactTypingName is typing..."
        typingDisplayName.text = formatContactTyping
        typingDisplayName.isVisible = isGroupChat
    }

    private fun hideTypingIndicator(){
        val typingAnimation = typing_indicator_lottie
        val typingDisplayName = typing_status_display_name
        typingDisplayName.isGone = true
        typingAnimation.isGone = true
        typingAnimation.cancelAnimation()
    }
    override fun onStart() {
        super.onStart()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if(contactJID.isNotEmpty()) messageViewModel.sendGoneState(contactJID, isGroupChat)
        val intent = Intent(this, main_page::class.java)
        startActivity(intent)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(CONTACTJID, contactJID)
        outState.putBoolean(ISGROUPCHAT, isGroupChat)
        outState.putString(DISPLAYNAME, displayName)
        super.onSaveInstanceState(outState)
    }

    private fun getBundle() :Bundle {
        val bundle = Bundle()
        bundle.putString(CONTACTJID, contactJID)
        bundle.putBoolean(ISGROUPCHAT, isGroupChat)
        bundle.putString(DISPLAYNAME, displayName)
        return bundle
    }

//    private fun showFullScreenImage(drawable: Drawable){
//        val dialog = Dialog(this,android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
//        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
//        dialog.setCancelable(true)
//        dialog.setContentView(R.layout.view_image_fullscreen)
//        val image = dialog.findViewById<ImageView>(R.id.enlarge_photo_dialog)
//        image.setImageDrawable(drawable)
//        dialog.show()
//
//    }

}
