package xmpp

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner

import xmpp.BackGroundDetector.isInBackground

/**
 * Class to manage if the application is in the foreground or background.
 * Class is started as a service by Android in the Android Manifest.
 * Will update the boolean stored in the Kotlin object (forced singleton by
 * language constructs) @see [BackGroundDetector]. Used in the @see [XmppTapIn] class
 * to determine if a notification should be shown for a message.
 */
class AppLifecycleDetector : Application(), LifecycleObserver {

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * Function called when the app is in the Background.
     * Will update the Boolean in @see [BackGroundDetector]
     *
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        isInBackground = true
        println("APP IN BACKGROUND")
        Log.d("MyApp", "App in background")
    }

    /**
     * Function called when the app is in the Foreground.
     * Will update the Boolean in @see [BackGroundDetector]
     *
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        isInBackground = false
        println("APP IN FOREGROUND")
        Log.d("MyApp", "App in foreground")
    }

    companion object{
        @Volatile
        private var INSTANCE: AppLifecycleDetector? = null

        fun getAppLifeCycle() :AppLifecycleDetector {
            val tempInstance = INSTANCE
            if(tempInstance != null) return tempInstance

            synchronized(this){
                val instance = AppLifecycleDetector()
                INSTANCE = instance
                return instance
            }
        }
    }
}

/**
 * Kotlin Object to store the boolean to determine if the app is in the foreground
 * or background. By definition, Kotlin objects are singletons. @see [AppLifecycleDetector]
 */
object BackGroundDetector{
    var isInBackground = false
}