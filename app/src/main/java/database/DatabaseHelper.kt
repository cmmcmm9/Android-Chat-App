package database

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Helper dataclass to assist in updating a contact's information
 * stored in Contacts, ContactStatus, and ContactAvailableTime
 * Values are mutable to allow for changes.
 *
 * @property contactJID
 * @property rowId
 * @property isOnline
 * @property isAvailable
 * @property lastOnlineTime
 * @property lastOnlineDate
 * @property isTyping
 * @property isBlocked
 * @property isMuted
 * @property timeAvailableStart
 * @property timeAvailableEnd
 */
data class UpdateContactHelper(
    val contactJID: String,
    val rowId: Int,
    var isOnline: Boolean,
    var isAvailable: Boolean,
    var lastOnlineTime: String,
    var lastOnlineDate: String,
    var isTyping: Boolean,
    var isBlocked: Boolean,
    var isMuted: Boolean,
    var timeAvailableStart: String,
    var timeAvailableEnd: String,

)

/**
 * Kotlin utility object (singleton class) to assist with various
 * Time and Date needs. Can get the current Local data and time (and return as a string),
 * convert military time to a regular time @sample[01:00 to 1:00 AM],
 * get the user's available time in stored in Android Preferences, and
 * convert UTC time-date to local time-date.
 */
object TimeDateManager{

    /**
     * Function to get the current local time represented as a string.
     * Used mostly in the database.
     *
     * @return : Current local time as a string
     */
    fun getTime() :String{
        val currentTime = LocalTime.now()

        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        return currentTime.format(formatter).toString()
    }

    /**
     * Function to convert a given military time to a
     * regular AM or PM time. @sample[01:00 goes to 1:00 AM]
     * Mostly used to display a normal time to the user instead of the military time stored
     * in the database.
     * @param militaryTime : the military time to convert
     * @return RegularTime: the converted military time as AM or PM time value
     */
    fun getPrettyTime(militaryTime: String) :String{

        if(militaryTime > "12:59"){
            val hour = "${militaryTime[0]}${militaryTime[1]}"
            val prettyHour = hour.toInt() - 12

            return "$prettyHour${militaryTime.substring(2)} PM"

        }

        else if(militaryTime[0] == '0') return "${militaryTime.substring(1)} AM"
        return "$militaryTime PM"



    }

    /**
     * Convert a given UTC date-time string to a Local Date.
     *
     * @param utcDateTime : UTC date-time string
     * @return LocalDate : local data of the UTC date
     */
    fun convertUTCtoLocalDate(utcDateTime: String) :String{
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        simpleDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val outputDate = SimpleDateFormat("MM-dd-yyyy", Locale.US)
        val localDate :Date = simpleDateFormat.parse(utcDateTime)
        return outputDate.format(localDate)
    }

    /**
     * Convert a given UTC date-time string to a Local Time.
     *
     * @param utcDateTime : UTC date-time string
     * @return LocalDate : local time of the UTC time
     */
    fun convertUTCtoLocalTime(utcDateTime: String) :String{
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        simpleDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val outputDate = SimpleDateFormat("HH:mm:ss", Locale.US)
        val localDate :Date = simpleDateFormat.parse(utcDateTime)
        return outputDate.format(localDate)
    }

    /**
     * Function used to determine the number of seconds since the last
     * group chat message was received by a user. Used to request history
     * when joining a Group Chat Room. If no messages are present (typically a new
     * group chat where the user just joined) it will request the last 30 days of messages.
     *
     * @param localDate : local Date string of the last message from the group chat to join
     * @param localTime : local Time string of the last message from the group chat to join
     * @return Int: int value representing the number of seconds between the passed date-time values and
     * the current date time values
     */
    fun getSecondsSinceLastMessage(localDate: String?, localTime: String?) :Int {
        if(localDate.isNullOrEmpty() || localTime.isNullOrEmpty()) return 60*60*24*30
        val localDateTime = "$localDate $localTime"
        val formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss")
        val dateTime = LocalDateTime.parse(localDateTime, formatter)
        return ChronoUnit.SECONDS.between(dateTime, LocalDateTime.now()).toInt()
    }

    /**
     * Function to determine if a user is available given
     * their start time and end time (available times) for the current day.
     *
     * @param startTime : start time for the user for the current day
     * @param endTime : end time for the user for the current day
     * @return Boolean : boolean value to signify if the user is available given their available times. True if they are available
     */
    fun isAvailable(startTime: String, endTime: String) :Boolean{
        return getTime() in startTime..endTime

    }

    /**
     * Get the current local date represented as a string. Used for database fields.
     *
     * @return dateString: local date represented as a string
     */
    fun getDate() :String{
        val currentDate = LocalDate.now()

        val formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")

        return currentDate.format(formatter).toString()
    }

    /**
     * Get the current day of the week represent as an Int value.
     * 1 - Sunday
     * 7 - Saturday
     *
     * @return dayOfTheWeek : day of the week represented as an int
     */
    fun getDayOfWeek() :Int{
        return Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    }

    fun getPrettyDayOfWeek(dow: Int) :String{

        var prettyDow = ""
        when(dow){
            1 -> prettyDow= "Sunday"
            2 -> prettyDow= "Monday"
            3 -> prettyDow= "Tuesday"
            4 -> prettyDow= "Wednesday"
            5 -> prettyDow= "Thursday"
            6 -> prettyDow= "Friday"
            7 -> prettyDow= "Saturday"
        }
        return prettyDow
    }

    /**
     * Get the current "DAY_Start" and "DAY_End" pair for the current day in order to query
     * the times stored in android preferences.
     *
     * @return : map of "DAY_Start" and "DAY_End" pairs. Example: map["startTime"] = "sundayStart"
     */
    fun getDayForPreferences() :Map<String, String> {
        return when(getDayOfWeek()){
            1 -> mapOf(Pair("startTime", "sundayStart"), Pair("endTime", "sundayEnd"))
            2 -> mapOf(Pair("startTime", "mondayStart"), Pair("endTime", "mondayEnd"))
            3 -> mapOf(Pair("startTime", "tuesdayStart"), Pair("endTime","tuesdayEnd" ))
            4 -> mapOf(Pair("startTime", "wednesdayStart"), Pair("endTime","wednesdayEnd"))
            5 -> mapOf(Pair("startTime", "thursdayStart"), Pair("endTime", "thursdayEnd"))
            6 -> mapOf(Pair("startTime", "fridayStart"), Pair("endTime", "fridayEnd"))
            7 -> mapOf(Pair("startTime", "saturdayStart"), Pair("endTime", "saturdayEnd"))
//            1,7 -> mapOf(Pair("startTime", "weekEndStartTime"), Pair("endTime", "weekEndEndTime"))
//            2,3,4,5,6 -> mapOf(Pair("startTime", "weekDayStartTime"), Pair("endTime", "weekDayEndTime"))
            else -> mapOf(Pair("startTime", "weekDayStartTime"), Pair("endTime", "weekDayEndTime"))
        }

    }
    /**
     * Get the current "DAY_Start" and "DAY_End" pair for the passed in day (int value) in order to query
     * the times stored in android preferences.
     *
     * @param  dayOfWeek : int value corresponding to the day pair to query for
     * @return : map of "DAY_Start" and "DAY_End" pairs. Example: map["startTime"] = "sundayStart"
     */
    fun getDayForPreferences(dayOfWeek: Int) :Map<String, String>{
        return when(dayOfWeek){
            1 -> mapOf(Pair("startTime", "sundayStart"), Pair("endTime", "sundayEnd"))
            2 -> mapOf(Pair("startTime", "mondayStart"), Pair("endTime", "mondayEnd"))
            3 -> mapOf(Pair("startTime", "tuesdayStart"), Pair("endTime","tuesdayEnd" ))
            4 -> mapOf(Pair("startTime", "wednesdayStart"), Pair("endTime","wednesdayEnd"))
            5 -> mapOf(Pair("startTime", "thursdayStart"), Pair("endTime", "thursdayEnd"))
            6 -> mapOf(Pair("startTime", "fridayStart"), Pair("endTime", "fridayEnd"))
            7 -> mapOf(Pair("startTime", "saturdayStart"), Pair("endTime", "saturdayEnd"))
            else -> mapOf(Pair("startTime", "weekDayStartTime"), Pair("endTime", "weekDayEndTime"))
        }
    }




}

/**
 * Data class to store the data to
 * display for the @see[list.adapter.ChatSessionListAdapter].
 * Helps with the returning queries.
 *
 * @property groupName
 * @property groupID
 * @property time
 * @property date
 * @property dateCreated
 * @property isOutgoing
 * @property contactAvatar
 * @property messageBody
 * @property isGroupChat
 * @property isMediaMessage
 * @property isSilenced
 */
data class ChatSessionRecyclerData(
    val groupName: String,
    val groupID: String,
    val time: String?,
    val date: String?,
    val dateCreated: String,
    val isOutgoing: Boolean,
    val contactAvatar: ByteArray?,
    val messageBody: String?,
    val isGroupChat: Boolean,
    val isMediaMessage: Boolean,
    val isSilenced: Boolean

)

/**
 * Data class to store the data to
 * display for the @see[list.adapter.DisplayContactsListAdapter].
 * Helps with the returning queries.
 *
 * @property contactName
 * @property contactJID
 * @property contactEmail
 * @property contactAvatar
 * @property isOnline
 * @property isAvailable
 * @property timeAvailableStart
 * @property timeAvailableEnd
 */
data class DisplayContactsRecyclerData(
    val contactName: String,
    val contactJID: String,
    val contactEmail: String?,
    val contactAvatar: ByteArray?,
    val isOnline: Boolean,
    val isAvailable: Boolean,
    val timeAvailableStart: String,
    val timeAvailableEnd: String
)

/**
 * Data class to store the data to
 * display for the @see[list.adapter.MessagesListAdapter].
 * Helps with the returning queries.
 *
 * @property contactName
 * @property contactJID
 * @property contactAvatar
 * @property isContactTyping
 * @property contactTypingName
 * @property createdBy
 * @property isRead
 * @property isReceived
 * @property isSent
 * @property isDraft
 * @property isGroupChat
 * @property isSilenced
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
data class DisplayMessagesData(
    val contactName: String?,
    val contactJID: String?,
    val contactAvatar: ByteArray?,
    val isContactTyping: Boolean?,
    val contactTypingName: String?,

    val createdBy: String,

    val isRead: Boolean,
    val isReceived: Boolean,
    val isSent: Boolean,
    val isDraft: Boolean,

    val isGroupChat: Boolean,
    val isSilenced: Boolean,

 //   val rowId: Int,
    val messageID: String,
    val chatSessionID: Int,
    val messageFrom: String,
    val messageTo: String,
    val isIncoming: Boolean,
    val isOutgoing: Boolean,
    val date: String,
    val time: String,
    val messageBody: String,
    val isEncrypted: Boolean,
    val isMediaMessage: Boolean

)
