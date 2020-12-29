package com.example.tapin

import android.content.Intent
import android.database.DataSetObserver
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.MultiAutoCompleteTextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.hootsuite.nachos.NachoTextView
import com.hootsuite.nachos.chip.Chip
import kotlinx.android.synthetic.main.activity_create_new_m_u_c.*
import kotlinx.android.synthetic.main.activity_display_contacts.*
import list.adapter.*
import viewmodel.TapInViewModel
import java.util.*

private lateinit var tapInViewModel: TapInViewModel
private lateinit var firebaseAuth: FirebaseAuth

// Constant string values used throughout activity
private const val CONTACTJID = "CONTACTJID"
private const val DISPLAYNAME = "DISPLAYNAME"
private const val ISGROUPCHAT = "ISGROUPCHAT"
private const val domain = "tapinapp.com"
private const val conference = "conference"

/**
 * Activity used to create a new group chat. It will display
 * a Group title text field, where users can enter their
 * Group Chat Name, and below that enter their desired contacts
 * that will be added to the group. Upon successful creation
 * of a new group chat, the group chat will automatically be opened @see[com.example.tapin.in_chats]
 *
 */
class CreateNewMUCActivity : AppCompatActivity() {
    /**
     * Set up UI, add click listeners to
     * all of the text fields. Set up the recycler
     * view to display the user's contacts.
     *
     * @param savedInstanceState : android bundled saved instance
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_new_m_u_c)

        firebaseAuth = FirebaseAuth.getInstance()


        val nachoTextView = group_members_text_view
        val recyclerView = createNewMUCRecyclerView
        val contactClickedListener = object : TapInRecyclerViewClickListener {
            override fun onClick(view: View, contactJID: String, displayName: String) {
                val chip = object :Chip{
                    /**
                     * @return the text represented by this Chip
                     */
                    override fun getText(): CharSequence {
                        TODO("Not yet implemented")
                    }

                    /**
                     * @return the data associated with this Chip or null if no data is associated with it
                     */
                    override fun getData(): Any? {
                        TODO("Not yet implemented")
                    }

                    /**
                     * @return the width of the Chip or -1 if the Chip hasn't been given the chance to calculate its width
                     */
                    override fun getWidth(): Int {
                        TODO("Not yet implemented")
                    }

                    /**
                     * Sets the UI state.
                     *
                     * @param stateSet one of the state constants in [android.view.View]
                     */
                    override fun setState(stateSet: IntArray?) {
                        TODO("Not yet implemented")
                    }

                }

            }
        }
        val contactLongPressedListener = object : TapInRecyclerViewLongPressListener {

            override fun onLongPress(view: View, contactJID: String, displayName: String) {
            }

        }
        //set up predictive list array for contact suggestions
        val adapter = DisplayContactsListAdapter(this, contactClickedListener, contactLongPressedListener)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        tapInViewModel = ViewModelProvider(this).get(TapInViewModel::class.java)

        tapInViewModel.displayContacts.observe(this, { chatsToDisplay ->
            chatsToDisplay?.let {
                adapter.setDisplayContactData(chatsToDisplay)
            }

            val suggestion = mutableListOf<ContactsForGroupChat?>()


            chatsToDisplay.forEach {
                suggestion.add(ContactsForGroupChat(displayName = it.contactName, email = it.contactEmail, contactJID = it.contactJID))
            }


            val nachoAdapter = AutoCompleteGroupChatAdapter(this, suggestion.toList())
            nachoTextView.setAdapter(nachoAdapter)
        })

        //create group fab button click listener
        createGroupFab.setOnClickListener {
            if(!validateGroupNameAndMembers()){
                Toast.makeText(this, "Please Enter Group Name and Members", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val groupName = groupNameEditText.text.toString()
            val contactsJID = mutableListOf<String>()

            println("In here")
            nachoTextView.getAllChips().forEach {
                val chipContact = it.data as ContactsForGroupChat
                contactsJID.add(chipContact.contactJID)

            }
            //launch the viewmodel scope to create a group chat
            val groupJID = "${UUID.randomUUID().toString().replace("-", "")}@$conference.$domain"
            tapInViewModel.createGroupChat(contactsJID.distinct(), groupName, groupJID)
            val intent = Intent(applicationContext, in_chats::class.java)


            //while chat is being created on viewmodel scope, launch in_chats activity to display the chat
            intent.putExtra(CONTACTJID, groupJID)
            intent.putExtra(DISPLAYNAME, groupName)
            intent.putExtra(ISGROUPCHAT, true)
            startActivity(intent)

        }





    }

    /**
     * Function to determine if the required fields of Group Name
     * and Group Members are filled out.
     *
     * @return Boolean: true if the fields are filled, otherwise false
     */
    private fun validateGroupNameAndMembers() :Boolean {
        return !groupNameEditText.text.isNullOrEmpty() && (group_members_text_view.allChips.size != 0)
    }

}
