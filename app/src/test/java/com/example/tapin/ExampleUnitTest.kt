package com.example.tapin
//
//import android.content.Context
//import androidx.test.core.app.ApplicationProvider
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import database.TapInDatabase
//import database.TimeDateManager
//import database.UserDao
//import org.junit.After
//import org.junit.Test
//
//import org.junit.Assert.*
//import org.junit.Before
//import org.junit.runner.RunWith
//import java.io.IOException
//
///**
// * Example local unit test, which will execute on the development machine (host).
// *
// * See [testing documentation](http://d.android.com/tools/testing).
// */
//@RunWith(AndroidJUnit4::class)
//class ExampleUnitTest {
//
//    private lateinit var userDao: UserDao
//    private lateinit var db: TapInDatabase
//    private lateinit var timeManager: TimeDateManager
//
//    @Before
//    fun createDb() {
//        val context = ApplicationProvider.getApplicationContext<Context>()
////        db = Room.inMemoryDatabaseBuilder(
////            context, TapInDatabase::class.java).build()
//        db = TapInDatabase.getDatabase(context)
//        timeManager = TimeDateManager()
//        userDao = db.userDao()
//
//    }
//
//    @After
//    @Throws(IOException::class)
//    fun closeDb() {
//        db.close()
//    }
//    @Test
//    fun addition_isCorrect() {
//        assertEquals(4, 2 + 2)
//    }
//
////    @Test
////    fun testUserInsertion(){
////        val user = User(
////            userJID = "someJID",
////            email = "cmmcmm9@gmail.com",
////            phoneNumber = "19788798807",
////            password = "Spooz!!",
////            salt = "blah",
////            firstName = "Bob",
////            lastName = "Saget",
////            dateCreated = timeManager.getDate()
////        )
////
////        GlobalScope.launch {
////            val x = userDao.insertUser(user)
////             }
////        suspend { val result = userDao.getAll()
////            assertEquals("dabs", result[0]) }
////
////
////
////
////
////    }
//
//
//
//}
