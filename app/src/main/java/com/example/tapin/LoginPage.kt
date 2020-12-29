package com.example.tapin


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import database.TapInDatabase
import kotlinx.android.synthetic.main.activity_main.*
import viewmodel.TapInViewModel
import java.util.concurrent.Executor

/**
 * Activity for the user to login. The user will be first logged in via Firebaseauth.
 * After this has been completed, a Firebase ID token for the user (JWT) will be gerneated
 * and send to Openfire where the user will be authenticated. Note: if the user has not confirmed their email,
 * the login will fail with Openfire.
 *
 */
class LoginPage : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: TapInDatabase
    private lateinit var dialog: AlertDialog
    private lateinit var dialogBuilder: AlertDialog.Builder
    private lateinit var layoutView: View
    private lateinit var tapInViewModel: TapInViewModel
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo


    /**
     * Set up activity layout, and add click listeners.
     *
     * @param savedInstanceState
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false)
        if (supportActionBar != null)
            supportActionBar?.hide()


        tapInViewModel = ViewModelProvider(this).get(TapInViewModel::class.java)


        layoutView = layoutInflater.inflate(R.layout.loading_dialog, null)
        dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setView(layoutView)
        dialog = dialogBuilder.create()

        val registerUser = register
        val loginBtn = this.findViewById<Button>(R.id.login_btn)

        auth = Firebase.auth

        //signOut()
        //handle login button click
        loginBtn.setOnClickListener()
         {
            if(validateForm()){
                val email = email_address.text.toString()
                val pass = password.text.toString()
                signIn(email, pass)
            }else Toast.makeText(
                baseContext,
                "Please fill out the required fields.",
                Toast.LENGTH_SHORT
            ).show()

        }

        //handle register use click
        registerUser.setOnClickListener {
            val intent = Intent(this, register_user::class.java)
            startActivity(intent)
        }

        //handle rest password click
        forgot_password.setOnClickListener {
            resetPasswordDialog()
        }

        //ensure that the app has sufficient permissions to storage and contacts
        setupPermissions()




    }

    /**
     * Function to request permissions to the user's contacts and phone storage
     * if the app does not already have these permissions.
     *
     */
    private fun setupPermissions() {
        val permissionReadContacts = ContextCompat.checkSelfPermission(this,
        Manifest.permission.READ_CONTACTS)

        val permissionReadStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        val permissionWriteStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (permissionReadContacts != PackageManager.PERMISSION_GRANTED && permissionReadStorage != PackageManager.PERMISSION_GRANTED && permissionWriteStorage != PackageManager.PERMISSION_GRANTED) {
        makeRequest()
        }
    }

    /**
     * Function to show the request permissions dialog for access to contacts and phone storage.
     *
     */
    private fun makeRequest() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
            0)
    }

    /**
     * Upon app startup, first check if the user has
     * checked the setting to lock this app with Biometric Data.
     * If so, show Biometric Prompt authorication. Otherwise, so long as the user
     * is logged in (Firebase cached user) , start the @see[com.example.tapin.main_page] activity.
     *
     */
    public override fun onStart(){
        super.onStart()
//        signOut()
        if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("lockApp", false)){
            showBioMetricLockOrPin()
        }
        else if(auth.currentUser != null){
            val intent = Intent(this, main_page::class.java)
                signInXMPP(auth.currentUser!!)
                //println("Current user is ${currentUser.uid} and email is verified ${currentUser.isEmailVerified}")
                startActivity(intent)

        }

    }

    /**
     * Function to sign in the user to Firebase, retrieve a valid Firebase ID token, and
     * use this to log into Openfire. If the user is not authenticated with Firebase,
     * a toast notification will be shown
     *
     * @param email : user email entered
     * @param password : user password entered
     */
    private fun signIn(email: String, password: String){
        showLoadingDialog()
        //showProgressSpinner()
        //show some progress bar
        //make couRoutine for login
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this){ task ->
            if(task.isSuccessful){
                //Log.d(TAG1, "signInWithEmail:success")
                val user = auth.currentUser
                Toast.makeText(baseContext, "Authentication Successful", Toast.LENGTH_SHORT).show()
                //val mUser = FirebaseAuth.getInstance().currentUser
                user!!.getIdToken(!auth.currentUser!!.isEmailVerified)
                    .addOnCompleteListener { firebaseIDTokenTask ->
                        if (firebaseIDTokenTask.isSuccessful) {
                            val idToken = firebaseIDTokenTask.result!!.token
                            println(idToken)
                            println(user.uid)
                            tapInViewModel.loginXmpp(user.uid, idToken)
                            // Send token to your backend via HTTPS
                            // ...

                        }
                        else {
                            Toast.makeText(baseContext, "Authentication Failed.", Toast.LENGTH_LONG).show()
                            dismissLoadingDialog()
                        }
                    }

                dismissLoadingDialog()
                val intent = Intent(this, main_page::class.java)
                startActivity(intent)
                    //updateUI("authorized")

            }
            else{
                dismissLoadingDialog()
                Toast.makeText(baseContext, "Authentication Failed.", Toast.LENGTH_LONG).show()
            }

        }
    }


    /**
     * Function to sign the user in with Openfire if the user
     * is already signed in (Firebase cached user).
     *
     * @param user
     */
    private fun signInXMPP(user: FirebaseUser){
        //showLoadingDialog()
        user.getIdToken(false).addOnCompleteListener {
            if(it.isSuccessful){
                tapInViewModel.loginXmpp(user.uid, it.result?.token)
            }
            else{
                Toast.makeText(baseContext, "Authentication Failed.", Toast.LENGTH_LONG).show()
            }
        }


    }

    /**
     * Function to sign out the user from Firebase. Used in development.
     *
     */
    private fun signOut(){
        auth.signOut()
    }

    /**
     * Function to reload the user from Firebase. Used in development.
     *
     */
    private fun reload(){
        auth.currentUser!!.reload().addOnCompleteListener { task ->
            if(task.isSuccessful){
                //updateUI
                Toast.makeText(baseContext, "Reload Success!", Toast.LENGTH_SHORT)
                    .show()

            }else{
                //log reason
                Toast.makeText(this@LoginPage, "Failed to reload User", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    /**
     * Function the ensure that the "email" and "password" fields are filled out properly.
     *
     * @return Boolean: true if the form is filled out, otherwise false
     */
    private fun validateForm(): Boolean{
        var isValid = true

        val email = email_address.text.toString()
        val pass = password.text.toString()

        if(TextUtils.isEmpty(email)) isValid = false
        if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) isValid=false
        if(TextUtils.isEmpty(pass)) isValid = false

        return isValid
    }

    /**
     * Function to show the loading dialog when attempting to sign the user on.
     *
     */
    private fun showLoadingDialog(){
        dialog.show()
    }

    /**
     * Function to dismiss the loading dialog when attempting to sign the user on.
     *
     */
    private fun dismissLoadingDialog(){
        dialog.dismiss()
    }


    /**
     * Function to show the biometric prompt to the user. If unsuccessful, the
     * user will need to sign in via email and password. If successful, the user is allowed
     * to enter the application.
     *
     */
    private fun showBioMetricLockOrPin(){
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int,
                                                   errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext,
                        "Authentication error: $errString", Toast.LENGTH_SHORT)
                        .show()
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(applicationContext,
                        "Authentication succeeded!", Toast.LENGTH_SHORT)
                        .show()
                    val intent = Intent(this@LoginPage, main_page::class.java)
                    //TODO TAKE THIS AWAY WHEN THE SERVER IS UP
                    signInXMPP(auth.currentUser!!)
                    //println("Current user is ${currentUser.uid} and email is verified ${currentUser.isEmailVerified}")
                    startActivity(intent)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Authentication failed",
                        Toast.LENGTH_SHORT)
                        .show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for TapinApp")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Use account password")
            .build()

        biometricPrompt.authenticate(promptInfo)

    }

    /**
     * Function to show the password reset dialog. The user will enter their email,
     * and if it is a valid account, a Firebase link will be sent to the user to reset their password.
     *
     */
    private fun resetPasswordDialog(){

        val dialogBuilder = android.app.AlertDialog.Builder(this)
        val layoutView = layoutInflater.inflate(R.layout.reset_password_layout, null) as View
        val resetEmailAddress = layoutView.findViewById(R.id.reset_email_edit_text) as EditText
        val resetPasswordButton = layoutView.findViewById(R.id.confirm_reset_email) as Button
        val cancelResetButton = layoutView.findViewById(R.id.cancel_reset_button) as Button
        dialogBuilder.setView(layoutView)
        val alertDialog = dialogBuilder.create()

        resetPasswordButton.setOnClickListener {
            if(resetEmailAddress.text.toString().isNotEmpty()){
                auth.sendPasswordResetEmail(resetEmailAddress.text.toString()).addOnCompleteListener { task ->
                    if(task.isSuccessful){
                        Toast.makeText(this, "Reset Email Has Been Sent.", Toast.LENGTH_LONG).show()
                        alertDialog.dismiss()
                    }
                }
            }
            else {
                Toast.makeText(this, "Invalid Email", Toast.LENGTH_LONG).show()
            }

        }

        cancelResetButton.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()


    }
}




