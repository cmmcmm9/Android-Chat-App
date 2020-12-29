package com.example.tapin

import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.firebase.messaging.FirebaseMessaging
import database.TimeDateManager
import database.User

import kotlinx.android.synthetic.main.activity_register_users.*
import kotlinx.android.synthetic.main.activity_register_users.email_address
import kotlinx.android.synthetic.main.activity_register_users.password
import viewmodel.TapInViewModel
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

/**
 * Activity to handle user Registration. Using two factor authentication,
 * phone and email. If phone authentication fails, the user is still registered but will
 * hinder the contact sync functionality.
 *
 */
class register_user : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var phoneAuth: PhoneAuthProvider
    private lateinit var tapInViewModel: TapInViewModel
    private var isPhoneVerified = false

    /**
     * Set up UI layout and click listeners
     *
     * @param savedInstanceState
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_users)
        if (supportActionBar != null)
            supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        phoneAuth = PhoneAuthProvider.getInstance()
        tapInViewModel = ViewModelProvider(this).get(TapInViewModel::class.java)

        //db = TapInDatabase.getDatabase(this, this)

        val regButton = registerButton

        //handle registration button click
        regButton.setOnClickListener{
            if(validateForm()){
                registerUser()
            }
        }

    }


    /**
     * Function to register a user with Firebase, then Openfire,
     * and insert them into the database (upon valid registration).
     * If unsuccessful, will show an error dialog to the user.
     *
     */
    private fun registerUser(){
        showProgressSpinner()

        val fullName = fulllName.text.toString()
        var phoneNumber = phoneNumber.text.toString()
        if(phoneNumber.length == 10) phoneNumber = "+1$phoneNumber"
        if(phoneNumber.length == 11) phoneNumber = "+$phoneNumber"
        val email = email_address.text.toString()
        val pass = password.text.toString()
        //val timeManager = TimeDateManager

        //firebase registration
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(this){ task ->
            if(task.isSuccessful){
                println("Got into here on successful")

                Toast.makeText(baseContext, "Account Successfully Made!", Toast.LENGTH_SHORT).show()
                sendEmailVerification()
                val user = auth.currentUser
                if (user != null) {
                    verifyPhoneNumber(phoneNumber)
                }
                user!!.uid

                val profileUpdate = UserProfileChangeRequest.Builder()
                profileUpdate.displayName = fullName

                user.updateProfile(profileUpdate.build())

                //after the user is registered, time to generate a new FCM token
                //after doing this, a new token will be generated and will trigger the
                //OnNewToken method in firebaseMessaging.TapInFirebaseMessaging
                println("About to get a new FCM Token")
                FirebaseMessaging.getInstance().isAutoInitEnabled = true

                //add user to Android Room, and register them
                //in openfire by sending the ID token to Ktor Server
                user.getIdToken(false).addOnCompleteListener{ taskGetToken ->

                    if(taskGetToken.isSuccessful){
                        val idToken = taskGetToken.result!!.token
                        if (idToken != null) {
                            tapInViewModel.registerNewUser(
                                User(
                                    userJID = user.uid,
                                    fullName = fullName,
                                    email = user.email,
                                    phoneNumber = phoneNumber,
                                    dateCreated = TimeDateManager.getDate(),
                                    userAvatar = null
                                ), idToken, application)
                        }
                    }

                }

                //println("The new user info is ${tapInViewModel.getUser().value}")
                hideProgressSpinner()
                //showSuccessDialog()
            }else{
                hideProgressSpinner()
                Toast.makeText(baseContext, "Something went wrong!", Toast.LENGTH_SHORT).show()
                //hideProgressSpinner()
                showFailureDialog()

            }
        }
    }

    /**
     * Function to sen a confirmation email to the user so that they may confirm their email.
     *
     */
    private fun sendEmailVerification(){
        println("email verification triggered")
        val user = auth.currentUser!!
        user.sendEmailVerification()
            .addOnCompleteListener(this) { task ->

                //show status

                if(task.isSuccessful){
                    Toast.makeText(baseContext, "Verification link sent to ${user.email}", Toast.LENGTH_SHORT)
                        .show()
                }else{
                    //log it
                    Toast.makeText(baseContext, "Failed to send Verification Link", Toast.LENGTH_SHORT)
                        .show()

                }
            }
    }

    /**
     * Function to validate that all of the required fields are filled out for registration.
     *
     * @return Boolean: true of all fields are validated, otherwise false
     */
    private fun validateForm() :Boolean{
        var isValid = false
        val fullName = fulllName.text.toString()
        val phoneNumber = phoneNumber.text.toString()
        val email = email_address.text.toString()
        val email2 = email_address2.text.toString()
        val pass = password.text.toString()
        val pass2 = password4.text.toString()

        if(email.isEmpty() || email2.isEmpty() || pass.isEmpty() || pass2.isEmpty() || fullName.isEmpty() || phoneNumber.isEmpty()){
            Toast.makeText(baseContext, "Please Fill Out All Required Fields", Toast.LENGTH_LONG).show()
        }

        else if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) Toast.makeText(baseContext, "Please enter a valid email!", Toast.LENGTH_LONG).show()

        else if(email != email2) Toast.makeText(baseContext, "Email Addresses Do Not Match!", Toast.LENGTH_LONG).show()

        else if(pass != pass2) Toast.makeText(baseContext, "Passwords Do Not Match!", Toast.LENGTH_LONG).show()

        else if (!android.util.Patterns.PHONE.matcher(phoneNumber).matches()) Toast.makeText(baseContext, "Please enter a valid phone number!", Toast.LENGTH_LONG).show()

        else if(pass.length < 6) Toast.makeText(baseContext, "Password must be at least 6 characters Long", Toast.LENGTH_LONG).show()

        else isValid = true


        return isValid
    }

    /**
     * Funciton to handle the phone number verification. Will attempt
     * to verify the user's entered phone number. Most of the time, Google
     * Play services will handle the phone authentication automatically. In this case,
     * the "verification code" dialog will not be shown, as the user's phone was successfully verified.
     * Otherwise, the user will have to enter the code in manually via the displayed dialog.
     *
     * @param phoneNumber
     */
    private fun verifyPhoneNumber(phoneNumber: String) {
        println("got into this method with $phoneNumber")
        var storedVerificationId: String? = null
        var resendToken: PhoneAuthProvider.ForceResendingToken? = null
        var credential: PhoneAuthCredential?

        /**
         * Delegated observable property, triggers the lambda expression when variable changed. Called when the user
         * has to enter their verification code manually. Will attempt to validate the phone number.
         */
        var code: String by Delegates.observable(""){ property, oldValue, newValue ->
            credential = storedVerificationId?.let { PhoneAuthProvider.getCredential(it, newValue) }
            credential?.let {
                auth.currentUser?.updatePhoneNumber(it)
                Toast.makeText(baseContext, "Phone Number Verification Success", Toast.LENGTH_LONG).show()}
            showSuccessDialog()
        }

        //show dialog to enter code
        val builder = AlertDialog.Builder(this)
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        builder.setTitle("Verify Phone Number")
            .setMessage("Please Enter the Verification Code Sent to You")
            .setView(input)
        builder.apply {
            setPositiveButton(
                "Verify"
            ) { dialog, id ->

                if (input.text.isNullOrEmpty()) Toast.makeText(
                    baseContext,
                    "Please Enter A Valid Code",
                    Toast.LENGTH_LONG
                ).show()
                else{
                    code = input.text.toString()
                    dialog.dismiss()
                }
                // User clicked OK button
            }
        }
        val dialog = builder.create()

        // automatic phone verification
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // This callback will be invoked in two situations:
                // 1 - Instant verification. In some cases the phone number can be instantly
                //     verified without needing to send or enter a verification code.
                // 2 - Auto-retrieval. On some devices Google Play services can automatically
                //     detect the incoming verification SMS and perform verification without
                //     user action.
                //Log.d(TAG, "onVerificationCompleted:$credential")
                //signInWithPhoneAuthCredential(credential)
                dialog.dismiss()
                auth.currentUser?.updatePhoneNumber(credential)
                Toast.makeText(baseContext, "Phone Number Verification Success", Toast.LENGTH_LONG).show()
                showSuccessDialog()
            }

            override fun onVerificationFailed(e: FirebaseException) {
                dialog.dismiss()
                println("Verification failed ${e.message}")
                // This callback is invoked in an invalid request for verification is made,
                // for instance if the the phone number format is not valid.
                //Log.w(TAG, "onVerificationFailed", e)

                if (e is FirebaseAuthInvalidCredentialsException) {
                    println(e)
                    // Invalid request
                    // ...
                } else if (e is FirebaseTooManyRequestsException) {
                    println(e)
                    // The SMS quota for the project has been exceeded
                    // ...
                }

                // Show a message and update the UI
                // ...
                Toast.makeText(
                    baseContext,
                    "Phone Verification failed! Please try Again",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                dialog.show()
                // The SMS verification code has been sent to the provided phone number, we
                // now need to ask the user to enter the code and then construct a credential
                // by combining the code with a verification ID.
                //Log.d(TAG, "onCodeSent:$verificationId")

                // Save verification ID and resending token so we can use them later
                storedVerificationId = verificationId
                resendToken = token

                // ...
            }
        }

        //send phone code
        phoneAuth.verifyPhoneNumber(
            phoneNumber,
            60,
            TimeUnit.SECONDS,
            this,
            callbacks
        )

    }

    /**
     * Function to show a success dialog to the user upon successful account registration.
     *
     */
    private fun showSuccessDialog(){
        val dialogBuilder = AlertDialog.Builder(this)
        val layoutView = layoutInflater.inflate(R.layout.register_success_dialog, null)
        val dialogButton = layoutView.findViewById(R.id.btnDialog) as Button
        dialogBuilder.setView(layoutView)
        val alertDialog = dialogBuilder.create()
        alertDialog.show()
        dialogButton.setOnClickListener {
            auth.signOut()
            alertDialog.dismiss()
            val intent = Intent(this, LoginPage::class.java)
            startActivity(intent)
        }
    }

    /**
     * Function to show a failure dialog to the user upon failure to register
     *
     */
    private fun showFailureDialog(){
        val dialogBuilder = AlertDialog.Builder(this)
        val layoutView = layoutInflater.inflate(R.layout.register_failure_dialog, null)
        val dialogButton = layoutView.findViewById(R.id.btnDialogFail) as Button
        dialogBuilder.setView(layoutView)
        val alertDialog = dialogBuilder.create()
        alertDialog.show()
        dialogButton.setOnClickListener { alertDialog.dismiss() }
    }

    /**
     * Show the progress spinner as the account is attempting to be register.
     *
     */
    private fun showProgressSpinner(){
        val progressBar = progressBarRegister
        progressBar?.visibility = View.VISIBLE
    }

    /**
     * Hide the progress spinner.
     *
     */
    private fun hideProgressSpinner(){
        val progressBar = progressBarRegister
        progressBar?.visibility = View.GONE
    }


}

