package com.example.tapin

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Spanned
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import com.squareup.picasso.Picasso
import database.TimeDateManager
import kotlinx.android.synthetic.main.activity_in_chats.*
import kotlinx.android.synthetic.main.activity_view_profile.*
import ktorClient.*
import viewmodel.TapInViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.*

private lateinit var tapInViewModel: TapInViewModel
private lateinit var availableTimePreferenceManager: AvailableTimePreferenceManager
private lateinit var dialogView: View
private lateinit var startTimeTextView: TextView
private lateinit var endTimeTextView: TextView

/**
 * Activity to display the user's profile and allow them to update
 * their info (avatar and available times)
 *
 */
class ViewProfile : AppCompatActivity() {
    /**
     * Setup the UI and click listeners
     *
     * @param savedInstanceState
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_profile)

        dialogView = layoutInflater.inflate(R.layout.change_availlable_times_dialog,  null) as View
        startTimeTextView = dialogView.findViewById(R.id.dialog_start_time) as TextView
        endTimeTextView = dialogView.findViewById(R.id.dialog_end_time) as TextView
        availableTimePreferenceManager = AvailableTimePreferenceManager(this)
        setUI()

        tapInViewModel = ViewModelProvider(this).get(TapInViewModel::class.java)

        val changeAvatarButton = change_avatar_button

        changeAvatarButton.setOnClickListener{
            val pickPhotoIntent = Intent(Intent.ACTION_PICK)
            pickPhotoIntent.type = "image/*"
            startActivityForResult(pickPhotoIntent, RESULT_LOAD_IMAGE_TO_SEND)
        }

        val avatar = user_profile_image_view
        val userUID = FirebaseAuth.getInstance().currentUser!!.uid.toLowerCase(Locale.ROOT)

        Picasso.get().load("$secureAvatarURL$userUID".toUri()).placeholder(R.drawable.progress_animation).error(
            R.drawable.person_def_avatar
        ).into(avatar)



    }

    /**
     * Handle the response from the requested "change avatar" request.
     * Once the user returns to the activty, this function will be called.
     * Either the user selected an image (success) or not (failure)
     *
     * @param requestCode : request code @see[RESULT_LOAD_IMAGE_TO_SEND]
     * @param resultCode : result code for success or failure
     * @param data : image selected or null if not selected
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(resultCode == RESULT_OK && requestCode == RESULT_LOAD_IMAGE_TO_SEND){
            println("Got a valid image selected")
            data?.data?.let { uri ->
                val folderName = "TapinApp Media"
                val dir = applicationContext.getDir(folderName, Context.MODE_PRIVATE)
                if(!dir.exists()) dir.mkdirs()
                val targetFile = File("${dir.absolutePath}/Temp")
                val outputStream = FileOutputStream(targetFile)
                contentResolver.openInputStream(uri)?.copyTo(outputStream)
                outputStream.close()
                val filePair = Pair(
                    FirebaseAuth.getInstance().currentUser!!.uid.toLowerCase(Locale.ROOT),
                    targetFile
                )
                val avatar = user_profile_image_view
                val userUID = FirebaseAuth.getInstance().currentUser!!.uid.toLowerCase(Locale.ROOT)

                tapInViewModel.uploadAvatar(
                    mapOf(filePair),
                    mapOf(Pair("dabs", "dabs")),
                    avatar,
                    userUID
                )


            }

        }
        else{
            println("Not valid image selected")
        }
    }

    /**
     * Helper function to set up the view and add click listeners.
     *
     */
    private fun setUI(){

        val onDayClickedListener = OnDayTileClickedListener()

        val sundayTimes = availableTimePreferenceManager.getTimeInPreferences(1)
        val mondayTimes = availableTimePreferenceManager.getTimeInPreferences(2)
        val tuesdayTimes = availableTimePreferenceManager.getTimeInPreferences(3)
        val wednesdayTimes = availableTimePreferenceManager.getTimeInPreferences(4)
        val thursdayTimes = availableTimePreferenceManager.getTimeInPreferences(5)
        val fridayTimes = availableTimePreferenceManager.getTimeInPreferences(6)
        val saturdayTimes = availableTimePreferenceManager.getTimeInPreferences(7)

        user_view_profile_display_name.text = FirebaseAuth.getInstance().currentUser?.displayName
        sunday_text_view_time.text = getFormattedDisplayTimeAsHtml(sundayTimes[startTime], sundayTimes[endTime])
        monday_text_view_time.text = getFormattedDisplayTimeAsHtml(mondayTimes[startTime], mondayTimes[endTime])
        tuesday_text_view_time.text = getFormattedDisplayTimeAsHtml(tuesdayTimes[startTime], tuesdayTimes[endTime])
        wednesday_text_view_time.text = getFormattedDisplayTimeAsHtml(wednesdayTimes[startTime], wednesdayTimes[endTime])
        thursday_text_view_time.text = getFormattedDisplayTimeAsHtml(thursdayTimes[startTime], thursdayTimes[endTime])
        friday_text_view_time.text = getFormattedDisplayTimeAsHtml(fridayTimes[startTime], fridayTimes[endTime])
        saturday_text_view_time.text = getFormattedDisplayTimeAsHtml(saturdayTimes[startTime], saturdayTimes[endTime])

        if(!sunday_layout.hasOnClickListeners()){

            sunday_layout.setOnClickListener(onDayClickedListener)
            monday_layout.setOnClickListener(onDayClickedListener)
            tuesday_layout.setOnClickListener(onDayClickedListener)
            wednesday_layout.setOnClickListener(onDayClickedListener)
            thursday_layout.setOnClickListener(onDayClickedListener)
            friday_layout.setOnClickListener(onDayClickedListener)
            saturday_layout.setOnClickListener(onDayClickedListener)

            sunday_text_view_time.setOnClickListener(onDayClickedListener)
            monday_text_view_time.setOnClickListener(onDayClickedListener)
            tuesday_text_view_time.setOnClickListener(onDayClickedListener)
            wednesday_text_view_time.setOnClickListener(onDayClickedListener)
            thursday_text_view_time.setOnClickListener(onDayClickedListener)
            friday_text_view_time.setOnClickListener(onDayClickedListener)
            saturday_text_view_time.setOnClickListener(onDayClickedListener)
        }

    }

    /**
     * Helper function to format the display time into three rows:
     * @sample
     * "8:00 AM
     *     to
     *  5:00 PM"
     *
     * @param startTime : start time to format
     * @param endTime : end time to format
     * @return : Spanned html format to display
     */
    private fun getFormattedDisplayTimeAsHtml(startTime: String?, endTime: String?) : Spanned {
            val htmlString = "${startTime?.let { TimeDateManager.getPrettyTime(it) }}<br/>to<br/>${endTime?.let { TimeDateManager.getPrettyTime(it) }}"
            return HtmlCompat.fromHtml(htmlString, HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    /**
     * Function to display the "Change Times" dialog to allow a user to change their times.
     *
     * @param dayOfWeek
     */
    private fun showChangeTimesDialog(dayOfWeek: Int){
        val preferenceManager = PreferenceManager.getDefaultSharedPreferences(this)
        val startTimeInPreferences = preferenceManager.getString(TimeDateManager.getDayForPreferences(dayOfWeek)["startTime"], "08:00")
        val endTimeInPreferences = preferenceManager.getString(TimeDateManager.getDayForPreferences(dayOfWeek)["endTime"], "17:00")
        val dialogBuilder = android.app.AlertDialog.Builder(this)

        val dayToDisplay = dialogView.findViewById(R.id.day_to_update_times_for) as TextView

        startTimeTextView.text = startTimeInPreferences
        endTimeTextView.text = endTimeInPreferences
        startTimeTextView.setOnClickListener {
            showPreferenceDialogForStartTime(dayOfWeek)
        }
        endTimeTextView.setOnClickListener {
            showPreferenceDialogForEndTime(dayOfWeek)
        }
        dayToDisplay.text = TimeDateManager.getPrettyDayOfWeek(dayOfWeek)

        dialogBuilder.setPositiveButton("Ok") { view, dialog ->
            availableTimePreferenceManager.updateAvailableTimeValues()
            setUI()

        }

        dialogBuilder.setView(dialogView)
        val alertDialog = dialogBuilder.create()
        alertDialog.show()

    }

    /**
     * Function to update the displayed time on the current "Change Time Dialog"
     *
     * @param dayOfWeek : day of the week to update
     */
    private fun updateDialogTime(dayOfWeek: Int){
        val preferenceManager = PreferenceManager.getDefaultSharedPreferences(this)
        val startTimeInPreferences = preferenceManager.getString(TimeDateManager.getDayForPreferences(dayOfWeek)["startTime"], "08:00")
        val endTimeInPreferences = preferenceManager.getString(TimeDateManager.getDayForPreferences(dayOfWeek)["endTime"], "17:00")
        startTimeTextView.invalidate()
        endTimeTextView.invalidate()
        startTimeTextView.text = startTimeInPreferences
        endTimeTextView.text = endTimeInPreferences
    }

    /**
     * Show list dialog to select a start time for the day.
     *
     * @param dayOfWeek : day of the week this change corresponds to
     */
    private fun showPreferenceDialogForStartTime(dayOfWeek: Int){

        val prettyDay = TimeDateManager.getPrettyDayOfWeek(dayOfWeek)
        val dayForPrefernce = TimeDateManager.getDayForPreferences(dayOfWeek)
        val preferenceManager = PreferenceManager.getDefaultSharedPreferences(this)
        val currentStartTime = preferenceManager.getString(dayForPrefernce["startTime"], "08:00")
        var newStartTime = currentStartTime

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select A Starting Time For $prettyDay")

        val startTimeDisplayEntries = resources.getStringArray(R.array.start_time_entries)
        val startTimeValues = resources.getStringArray(R.array.start_time_values)
        val checkedItem = currentStartTime?.let { startTimeValues.indexOf(currentStartTime) }
        if (checkedItem != null) {
            builder.setSingleChoiceItems(startTimeDisplayEntries, checkedItem) { dialog, indexOfSelection ->
                newStartTime = startTimeValues[indexOfSelection]
            }
        }
        else builder.setSingleChoiceItems(startTimeDisplayEntries, 7) { dialog, indexOfSelection ->
            newStartTime = startTimeValues[indexOfSelection]
        }


// add OK and Cancel buttons
        builder.setPositiveButton("OK") { dialog, which ->
            if(newStartTime == currentStartTime) return@setPositiveButton
            preferenceManager.edit().putString(dayForPrefernce["startTime"], newStartTime).apply()
            updateDialogTime(dayOfWeek)
            tapInViewModel.updateVcard()
        }
        builder.setNegativeButton("Cancel", null)

// create and show the alert dialog
        val dialog = builder.create()
        dialog.show()

    }

    /**
     * Show list dialog to select a end time for the day.
     *
     * @param dayOfWeek : day of the week this change corresponds to
     */
    private fun showPreferenceDialogForEndTime(dayOfWeek: Int){

        val prettyDay = TimeDateManager.getPrettyDayOfWeek(dayOfWeek)
        val dayForPrefernce = TimeDateManager.getDayForPreferences(dayOfWeek)
        val preferenceManager = PreferenceManager.getDefaultSharedPreferences(this)
        val currentEndTime = preferenceManager.getString(dayForPrefernce["endTime"], "17:00")
        var newEndTime = currentEndTime

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select An Ending Time For $prettyDay")

        val endTimeDisplayEntries = resources.getStringArray(R.array.end_time_entries)
        val endTimeValues = resources.getStringArray(R.array.end_time_values)
        val checkedItem = currentEndTime?.let { endTimeDisplayEntries.indexOf(currentEndTime) } // cow
        if (checkedItem != null) {
            builder.setSingleChoiceItems(endTimeDisplayEntries, checkedItem) { dialog, indexOfSelection ->
                newEndTime = endTimeValues[indexOfSelection]
            }
        }
        else builder.setSingleChoiceItems(endTimeDisplayEntries, 7) { dialog, indexOfSelection ->
            newEndTime = endTimeValues[indexOfSelection]
        }


// add OK and Cancel buttons
        builder.setPositiveButton("OK") { dialog, which ->
            if(newEndTime == currentEndTime) return@setPositiveButton
            preferenceManager.edit().putString(dayForPrefernce["endTime"], newEndTime).apply()
            updateDialogTime(dayOfWeek)
            tapInViewModel.updateVcard()
        }
        builder.setNegativeButton("Cancel", null)

// create and show the alert dialog
        val dialog = builder.create()
        dialog.show()

    }


    /**
     * Inner class to handle an available time display block clicked
     * Will open the "Change Time" dialog
     *
     */
    inner class OnDayTileClickedListener :View.OnClickListener {
        /**
         * Called when a view has been clicked.
         *
         * @param view The view that was clicked.
         */
        override fun onClick(view: View?) {
            println("In on day clicked")
            when(view?.id){
                R.id.sunday_layout, R.id.sunday_text_view, R.id.sunday_text_view_time -> showChangeTimesDialog(1)
                R.id.monday_layout, R.id.monday_text_view, R.id.monday_text_view_time -> showChangeTimesDialog(2)
                R.id.tuesday_layout, R.id.tuesday_text_view, R.id.tuesday_text_view_time -> showChangeTimesDialog(3)
                R.id.wednesday_layout, R.id.wednesday_text_view, R.id.wednesday_text_view_time -> showChangeTimesDialog(4)
                R.id.thursday_layout, R.id.thursday_text_view, R.id.thursday_text_view_time -> showChangeTimesDialog(5)
                R.id.friday_layout, R.id.friday_text_view, R.id.friday_text_view_time -> showChangeTimesDialog(6)
                R.id.saturday_layout, R.id.saturday_text_view, R.id.saturday_text_view_time -> showChangeTimesDialog(7)
                else -> println("It didnt work in on click with view IDS")
            }
        }

    }


}

/**
 * Class to manage the available times stored in the android preferences.
 * Will always have the latest copy of available times
 *
 * @constructor
 *
 *
 * @param context : application context
 */
class AvailableTimePreferenceManager(context: Context) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val allAvailableTimes: MutableMap<String?, String?> = mutableMapOf()
    private val dailyTimesList: MutableList<DayTimes> = mutableListOf()

    init {
        updateAvailableTimeValues()
    }

    /**
     * Called to refresh the available time data upon a new change
     *
     */
    fun updateAvailableTimeValues(){
        dailyTimesList.clear()
        allAvailableTimes.clear()
        for(dayOfTheWeek in 1..7){
            val dayTimePair = TimeDateManager.getDayForPreferences(dayOfTheWeek)
            val startTimeForDay = sharedPreferences.getString(dayTimePair["startTime"], "08:00")
            val endTimeForDay = sharedPreferences.getString(dayTimePair["endTime"], "17:00")
            allAvailableTimes[dayTimePair[startTime]] = startTimeForDay
            allAvailableTimes[dayTimePair[endTime]] = endTimeForDay
            dailyTimesList.add(DayTimes(
                dayOfWeek = dayOfTheWeek,
                startTime = startTimeForDay,
                endTime = endTimeForDay
            ))
        }
//            displayTimesLiveData = MutableLiveData()
//            displayTimesLiveData.value = dailyTimesList

    }

    /**
     * Function to get the available time in preferences corresponding to the day
     * of the week.
     *
     * @param dayOfTheWeek : day of the week as an int @see[TimeDateManager.getDayOfWeek]
     * @return map of time in preferences stored in preferences
     */
    fun getTimeInPreferences(dayOfTheWeek: Int) :Map<String, String?> {
        return when(dayOfTheWeek) {
            1 -> { mapOf(startTime to allAvailableTimes[sundayStartTime], endTime to allAvailableTimes[sundayEndTime]) }
            2 -> { mapOf(startTime to allAvailableTimes[mondayStartTime], endTime to allAvailableTimes[mondayEndTime]) }
            3 -> { mapOf(startTime to allAvailableTimes[tuesdayStartTime], endTime to allAvailableTimes[tuesdayEndTime]) }
            4 -> { mapOf(startTime to allAvailableTimes[wednesdayStartTime], endTime to allAvailableTimes[wednesdayEndTime]) }
            5 -> { mapOf(startTime to allAvailableTimes[thursdayStartTime], endTime to allAvailableTimes[thursdayEndTime]) }
            6 -> { mapOf(startTime to allAvailableTimes[fridayStartTime], endTime to allAvailableTimes[fridayEndTime]) }
            7 -> { mapOf(startTime to allAvailableTimes[saturdayStartTime], endTime to allAvailableTimes[saturdayEndTime]) }
            else -> { println("GetTimeInPrefernces() failed ")
                mapOf(startTime to allAvailableTimes[mondayStartTime], endTime to allAvailableTimes[mondayEndTime]) }
        }

    }

}

/**
 * Helper data class to store the
 * available times for a given day
 *
 * @property dayOfWeek
 * @property startTime
 * @property endTime
 */
data class DayTimes(
    val dayOfWeek: Int,
    val startTime: String?,
    val endTime: String?
)