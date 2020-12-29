package database

import androidx.room.TypeConverter

/**
 * Type converter for the @see[GroupChat.occupants] field.
 * Android room cannot handle the List<String> type, and
 * needs to convert the Kotlin list to a single string, separated by
 * a ','. Note: the stringToList() function is never called by android room
 * because a List<String> is a valid return type. The work around is
 * the extension function @see[String.toList].
 *
 */
class Converters {

    /**
     * Convert a List<String> to a single comma separated string
     *
     * @param occupants : List<String> of all of the occupants in a chat session (their JID's)
     * @return String: a single comma separated string of the passed list
     */
    @TypeConverter
    fun toString(occupants: List<String>) :String{
        return occupants.joinToString(",")
    }

    /**
     * function to convert the comma separated string to a list.
     * DOES NOTE WORK
     *
     * @param inputString
     * @return
     */
    @Deprecated("Android Room cannot handle this automatically. Used extension function String.toList()",
        ReplaceWith("String.toList()")
    )
    @TypeConverter
    fun stringToList(inputString: String) :List<String>{
        return inputString.split(",").map { it.trim() }
    }
}

/**
 * Extension Function to convert a comma separated string to a list
 *
 * @return List<String> : list representation of the passed string.
 */
fun String.toList() :List<String>{
    return this.split(",").map { it.trim() }
}