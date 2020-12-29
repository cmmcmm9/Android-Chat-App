package com.example.tapin

//import com.google.android.material.snackbar.Snackbar

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_in_chats.*
import kotlinx.android.synthetic.main.activity_main_page.*
import kotlinx.android.synthetic.main.delete_content_dialog.*
import kotlinx.android.synthetic.main.fragment_display_chat_sessions.*
import kotlinx.android.synthetic.main.fragment_display_chat_sessions.fab
import kotlinx.android.synthetic.main.fragment_display_chat_sessions.recyclerViewInChatSessionPage
import kotlinx.coroutines.GlobalScope
import list.adapter.ChatSessionListAdapter
import list.adapter.SettingsSpinnerArrayAdapter
import list.adapter.TapInRecyclerViewClickListener
import list.adapter.TapInRecyclerViewLongPressListener
import viewmodel.TapInViewModel
import xmpp.AppLifecycleDetector

private lateinit var firebaseAuth: FirebaseAuth

private const val CONTACTJID = "CONTACTJID"
private const val DISPLAYNAME = "DISPLAYNAME"
private const val ISGROUPCHAT = "ISGROUPCHAT"

private val settingsList = listOf("", "Settings", "View Profile")

/**
 * "Main page" for the app. This is where the user can see all of their conversations or
 * chat sessions (if they have any). User's can also launch the settings activity or view their profile,
 * click on a chat session to view the messages, or launch the display contacts activity via the fab button.
 *
 */
class main_page : AppCompatActivity() {

    private lateinit var tapInViewModel: TapInViewModel

    /**
     * Set up the UI layout and set up the click listeners.
     *
     * @param savedInstanceState
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_page)
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)


        firebaseAuth = FirebaseAuth.getInstance()
        firebaseAuth.currentUser


        val recyclerView = recyclerViewInChatSessionPage

        //click listener for a chat session, will open the selected chat
        val chatSessionClickedListener = object : TapInRecyclerViewClickListener{
            override fun onClick(view: View, contactJID: String, displayName: String) {
                val intent = Intent(applicationContext, in_chats::class.java)
                intent.putExtra(CONTACTJID, contactJID)
                intent.putExtra(DISPLAYNAME, displayName)
                if(contactJID.contains("conference")) intent.putExtra(ISGROUPCHAT, true)
                else intent.putExtra(ISGROUPCHAT, false)
                startActivity(intent)
            }

        }

        //long press chat session listener, will trigger the "delete chat session" dialog.
        val chatSessionLongPressListener = object : TapInRecyclerViewLongPressListener{
            override fun onLongPress(view: View, contactJID: String, displayName: String) {
                view.background = ContextCompat.getDrawable(this@main_page, R.drawable.light_blue)
                deleteChatDialog(contactJID)
                view.background = ContextCompat.getDrawable(this@main_page, android.R.color.transparent)
            }

        }

        //set up the recycler view to display the chat sessions, and pass in the LiveData to the adapter
        val adapter = ChatSessionListAdapter(this, chatSessionClickedListener, chatSessionLongPressListener)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        tapInViewModel = ViewModelProvider(this).get(TapInViewModel::class.java)


        tapInViewModel.displayedChatSession.observe(this, { chatsToDisplay ->

            chatsToDisplay?.let {
                adapter.setChatSessionData(chatsToDisplay)
            }
        })


        //show the display contacts activity
        fab.setOnClickListener { view ->
            val intent = Intent(this, DisplayContactsActivity::class.java)
            startActivity(intent)

        }

        //handle settings spinner, and set up selection listeners
        val spinner = main_page_settings_spinner
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
                    "Settings" -> {
                        spinner.setSelection(0)
                        startActivity(Intent(this@main_page, SettingsActivity::class.java))
//                        supportFragmentManager
//                            .beginTransaction()
//                            .replace(android.R.id.content, SettingsFragment())
//                            .commit()
                    }
                    "View Profile" -> {
                        spinner.setSelection(0)
                        startActivity(Intent(this@main_page, ViewProfile::class.java))
                    }
                    }

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
    }

    /**
     * Show the dialog to the user if they wish to delete a chat session. Will be triggered
     * on a long press of a chat session. If the user selects confirm, the chat will be deleted,
     * along with all of its messages. This is unrecoverable.
     *
     * @param contactJID : JID of the chat session, either contactJID for a one-to-one chat, or groupJID for a group chat
     */
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
            tapInViewModel.deleteChatCascade(contactJID)
            alertDialog.dismiss()
        }
        cancelDeleteContactButton.setOnClickListener {
            alertDialog.dismiss()
        }
    }




}

