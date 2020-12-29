package com.example.tapin

import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_display_contacts.*
import kotlinx.android.synthetic.main.delete_content_dialog.*
import list.adapter.DisplayContactsListAdapter
import list.adapter.TapInRecyclerViewClickListener
import list.adapter.TapInRecyclerViewLongPressListener
import viewmodel.MessageViewModel
import viewmodel.TapInViewModel

private lateinit var tapInViewModel: TapInViewModel
private lateinit var firebaseAuth: FirebaseAuth
private lateinit var messageViewModel: MessageViewModel

// Constant string values used throughout activity
private const val CONTACTJID = "CONTACTJID"
private const val DISPLAYNAME = "DISPLAYNAME"

/**
 * Activity used to display the user's contacts,
 * their available times, and their current in app status (offline, online, away).
 * If a user clicks on a single contact, it will create a new chat with this user if one does
 * not exists, or simply open the existing chat. Long Press Will show
 * the "mute contact" dialog to mute a contact (for a one to one chat)
 *
 */
class DisplayContactsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_contacts)

        firebaseAuth = FirebaseAuth.getInstance()
        val currentUser = firebaseAuth.currentUser
        messageViewModel = ViewModelProvider(this).get(MessageViewModel::class.java)


        //handle contact being clicked
        val recyclerView = displayContactsRecyclerActivity
        val contactClickedListener = object : TapInRecyclerViewClickListener {
            override fun onClick(view: View, contactJID: String, displayName: String) {

                val intent = Intent(applicationContext, in_chats::class.java)
                intent.putExtra(CONTACTJID, contactJID)
                intent.putExtra(DISPLAYNAME, displayName)
                startActivity(intent)
            }
        }

        //handle long press on contact
        val contactLongPressedListener = object : TapInRecyclerViewLongPressListener{

            override fun onLongPress(view: View, contactJID: String, displayName: String) {
                view.background = ContextCompat.getDrawable(this@DisplayContactsActivity, R.drawable.light_blue)
                muteChatDialog(contactJID)
                view.background = ContextCompat.getDrawable(this@DisplayContactsActivity, android.R.color.transparent)
            }

        }

        //set the LiveData for the recycler view to dispay the user's contacts
        val adapter = DisplayContactsListAdapter(this, contactClickedListener, contactLongPressedListener)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        tapInViewModel = ViewModelProvider(this).get(TapInViewModel::class.java)

        tapInViewModel.displayContacts.observe(this, { contactsToDisplay ->
                contactsToDisplay?.let {
                    adapter.setDisplayContactData(contactsToDisplay)
                }

        })


        //swipe refresh, trigger contact sync
        swiperefreshContacts.setOnRefreshListener{
            currentUser?.getIdToken(false)?.addOnCompleteListener { getToken ->
                if(getToken.isSuccessful) getToken.result?.token?.let {token ->
                    tapInViewModel.syncContacts(currentUser.uid, token, application)
                }
            }
            swiperefreshContacts.isRefreshing = false

        }

        val createNewMUC = createNewMULinearLayout

        createNewMUC.setOnClickListener {
            startActivity(Intent(this, CreateNewMUCActivity::class.java))
        }


    }

    /**
     * Function to show the Mute Chat Dialog.
     * If the user confirms, the chat/contact will be muted
     *
     * @param contactJID : contactJID of the contact to mute
     */
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

    /**
     * Testing function to test the "Sync Contacts" functionality.
     * If the contact was deleted, a "swipe refresh" would bring the
     * contact back into the database.
     *
     * @param contactJID
     */
    private fun deleteContactDialog(contactJID: String){
        val titleText = "Delete Contact"
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
            tapInViewModel.deleteContact(contactJID)
            alertDialog.dismiss()
        }
        cancelDeleteContactButton.setOnClickListener {
            alertDialog.dismiss()
        }
    }


}