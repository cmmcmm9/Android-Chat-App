package database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import java.security.AccessControlContext

/**
 * Android Room database class representation. Defines
 * the SQLite database. If one does not exist, it will create
 * it (normally on first app launch, but if the user deletes the app
 * data then this will be triggered on the next app launch.)
 * Also defines all of the DOA's to interact with every table.
 *
 * Current version = 12
 *
 */
@Database(entities = [User::class,
    UserTimeAvailable::class,
    UserStatus::class,
    UserSettings::class,
    ChatSession::class,
    Messages::class,
    MessageStatus::class,
    GroupChat::class,
    Contact::class,
    ContactTimeAvailable::class,
    ContactStatus::class]
    , version = 12, exportSchema = true)
@TypeConverters(Converters::class)
abstract class TapInDatabase : RoomDatabase(){

    abstract fun userDao(): UserDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun userAndUserSettingsDao() : UserandUserSettingsDao
    abstract fun userStatusDao(): UserStatusDao
    abstract fun userAndUserStatusDao(): UserAndUserStatusDao
    abstract fun userTimeAvailableDao(): UserTimeAvailableDao
    abstract fun userAndUserTimeAvailDao(): UserAndUserTimeAvailDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun userAndChatSessionDao(): UserAndChatSessionDao
    abstract fun chatSessionAndMessagesDao(): ChatSessionAndMessagesDao
    abstract fun groupChatAndChatSessioDao(): GroupChatandChatSessionDao
    abstract fun messagesDao(): MessagesDao
    abstract fun messageStatusDao(): MessageStatusDao
    abstract fun groupChatDao(): GroupChatDao
    abstract fun contactAndGroupChatDao(): ContactAndGroupChatDao
    abstract fun contactDao(): ContactDao
    abstract fun contactAndContactTimeAvailDao(): ContactAndContactTimeAvailDao
    abstract fun contactTimeAvailableDao(): ContactTimeAvailableDao
    abstract fun contactAndContactStatusDao(): ContactAndContactStatusDao
    abstract fun contactStatusDao(): ContactStatusDao


    //singleton logic
    companion object {

        @Volatile
        private var INSTANCE: TapInDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope):TapInDatabase{
            val tempInstance = INSTANCE
            if(tempInstance != null) return tempInstance

            synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TapInDatabase::class.java,
                    "TapIn_Database"
                ).enableMultiInstanceInvalidation()
                    .addMigrations(migration_11_12)
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}

/**
 * Below are all of the various migrations performed. If this is not done, then
 * the migration was destructive. For example: migration to version 1 to 2, all
 * of the data stored in version 1 was deleted.
 */
val migration_8_to_9 = object: Migration(8, 9) {
    /**
     * Should run the necessary migrations.
     * Add the isContactTyping column and contactTypingName fields to the ChatSession table
     *
     * This class cannot access any generated Dao in this method.
     *
     *
     * This method is already called inside a transaction and that transaction might actually be a
     * composite transaction of all necessary `Migration`s.
     *
     * @param database The database instance
     */
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("BEGIN TRANSACTION;")

        database.execSQL("ALTER TABLE ChatSession ADD COLUMN isContactTyping INTEGER NOT NULL DEFAULT 0;")

        database.execSQL("ALTER TABLE ChatSession ADD COLUMN contactTypingName TEXT;")

        database.execSQL("COMMIT;")
    }


}
val migration_9_to_10 = object : Migration(9, 10) {
    /**
     * Should run the necessary migrations.
     * Add the isMediaMessage value to the Messages table
     *
     * This class cannot access any generated Dao in this method.
     *
     *
     * This method is already called inside a transaction and that transaction might actually be a
     * composite transaction of all necessary `Migration`s.
     *
     * @param database The database instance
     */
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("BEGIN TRANSACTION;")

        database.execSQL("ALTER TABLE Messages ADD COLUMN isMediaMessage INTEGER NOT NULL DEFAULT 0;")

        database.execSQL("COMMIT;")
    }

}

val migration_10_11 = object : Migration(10, 11){
    /**
     * Should run the necessary migrations.
     * Add the groupChatAvatarURI field to the GroupChat table
     *
     * This class cannot access any generated Dao in this method.
     *
     *
     * This method is already called inside a transaction and that transaction might actually be a
     * composite transaction of all necessary `Migration`s.
     *
     * @param database The database instance
     */
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("BEGIN TRANSACTION;")

        database.execSQL("ALTER TABLE GroupChat ADD COLUMN groupChatAvatarURI TEXT;")

        database.execSQL("COMMIT;")
    }

}

val migration_11_12 = object :Migration(11, 12){

    /**
     * Should run the necessary migrations.
     * Add the contactPublicKey field to the Contact table.
     *
     * This class cannot access any generated Dao in this method.
     *
     *
     * This method is already called inside a transaction and that transaction might actually be a
     * composite transaction of all necessary `Migration`s.
     *
     * @param database The database instance
     */
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("BEGIN TRANSACTION;")

        database.execSQL("ALTER TABLE Contact ADD COLUMN contactPublicKey TEXT;")

        database.execSQL("COMMIT;")
    }

}