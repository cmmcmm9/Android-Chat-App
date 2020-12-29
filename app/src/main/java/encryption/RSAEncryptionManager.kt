package encryption

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import repository.Repository
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher


/**
 * Class that handles the RSA encryption logic, and managers the users and user's contacts public keys
 * stored in the Firebase Database. Public keys for every user are under the 'public-keys' node in
 * Firebase realtime storage. Structure in 'public-keys' node is { firebaseUID: public-key-as-string}
 * A user has the rights to read and write their node corresponding to their firebaseUID.
 * Contacts only have the right to read the value of a user's public key, assuming they are authorized.
 * If they are not an authorized user, they cannot do anything to the database.
 * Upon initialization of this class, using the @see [getRSAEncryptionManagerInstance] method for singleton logic,
 * the class creates a new RSA key pair if it does not exist, uploads the public key to Firebase database,
 * confirms the public key in Firebase is correct, and sets up "Firebase Event Listeners' for every one of
 * the user's contacts public-key nodes so any changes to their public keys can be handled. Every contact's public
 * key is stored locally in the Contact table.
 *
 */
class RSAEncryptionManager {

    private lateinit var repository: Repository
    private val firebaseDatabase = Firebase.database
    private val publicKeyNodeReference = firebaseDatabase.getReference("public-keys")
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    private val rsaEncryptionScope = CoroutineScope(Job() + Dispatchers.IO)

    init {
        //must load keyStore before be able to use it.
        keyStore.load(null)
    }

    /**
     * Function to verify that the user's current public key stored in the Android Key Store
     * matches the public key (string) stored in their node in the Firebase database.
     * If the user's public key is incorrect in Firebase Database, it will update it.
     *
     */
    private fun verifyUserPublicKeyIsCorrect(){
        val firebaseUID = FirebaseAuth.getInstance().currentUser?.uid?.toLowerCase(Locale.ROOT)

        firebaseUID?.let { UID ->
            publicKeyNodeReference.child(UID).addListenerForSingleValueEvent( object: ValueEventListener{
                /**
                 * This method will be called with a snapshot of the data at this location. It will also be called
                 * each time that data changes.
                 *
                 * @param snapshot The current data at the location
                 */
                override fun onDataChange(snapshot: DataSnapshot) {
                    val publicKey = getUsersPublicKey()
                    publicKey?.let {
                        if(!snapshot.exists() || snapshot.value.toString() != publicKey.toStringForFirebase()){
                            uploadPublicKeyToFirebase(publicKey)
                        }
                    }

                }

                /**
                 * This method will be triggered in the event that this listener either failed at the server, or
                 * is removed as a result of the security and Firebase Database rules. For more information on
                 * securing your data, see: [ Security
 * Quickstart](https://firebase.google.com/docs/database/security/quickstart)
                 *
                 * @param error A description of the error that occurred
                 */
                override fun onCancelled(error: DatabaseError) {
                    println("Some firebase error $error")
                }

            })
        }
    }

    /**
     * Upload the user's public key to their respective node in the Firebase Database.
     * Public key is stored as a string, using the @see [PublicKey.toStringForFirebase]
     * extension function in this class.
     *
     * @param publicKey of user in the Android Key Store
     */
    private fun uploadPublicKeyToFirebase(publicKey: PublicKey){

        val firebaseUser = FirebaseAuth.getInstance().currentUser

        firebaseUser?.uid?.let { firebaseUID ->
            //maybe? privateKey.toString()
            publicKeyNodeReference.child(firebaseUID.toLowerCase(Locale.ROOT)).setValue(publicKey.toStringForFirebase()).addOnCompleteListener { upload ->
                if(upload.isSuccessful){
                    println("Successfully uploaded public key for user with UID $firebaseUID and ${firebaseUser.displayName}")
                }
                else {
                    println("Unable to upload public key")
                    println("Error: Current user is $firebaseUID and display name is ${firebaseUser.displayName}")
                    println(upload.exception)
                }
            }

        }

    }


    /**
     * Function to create an RSA asymmetric key pair (public and private)
     * and store it locally in the Android Key store.
     * Will only create a new key pair if one does not exist.
     * Once the keypair is generated, it will call the @see [uploadPublicKeyToFirebase]
     * method to upload the public key to Firebase.
     */
    fun createAsymmetricKeyPair() {

        if(!keyStore.containsAlias(KEY_ALIAS)){
            println("Generating new key pair")
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )

            generator.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .setRandomizedEncryptionRequired(true)
                    .setKeySize(2048)
                    .build()
            )

            generator.generateKeyPair()
            keyStore.getCertificate(KEY_ALIAS)?.publicKey?.let { uploadPublicKeyToFirebase(it) }
        }
        else println("Key already exists")

    }

    /**
     * Function to get the user's private key stored in the Adnroid keystore.
     * Note that since API 26, Android Keystore DOES NOT return the private key as
     * encoded. So any call to PrivateKey.encoded will return null.
     * @return PrivateKey | null : the user's private key, if it exists or null if it doesn't
     */
    private fun getUsersPrivateKey() : PrivateKey? {

        val keyEntry = keyStore.getEntry(KEY_ALIAS, null)

        return if(keyEntry is KeyStore.PrivateKeyEntry){
//            println("It was a private key")
//            println(keyEntry.privateKey)
            keyEntry.privateKey
        } else{
            println("Something Went Wrong")
            null
        }

//        return keyStore.getKey(KEY_ALIAS, null) as PrivateKey?

    }

    /**
     * Function to get the user's public key stored in the Android Key Store.
     *
     * @return PublicKey | null : user's public key if it exists, or null
     */
    private fun getUsersPublicKey() :PublicKey? {
        return keyStore.getCertificate(KEY_ALIAS)?.publicKey
    }

    /**
     * Function to encrypt the message body (as a string) for a particular contact, using their
     * public key stored in the Contacts table (locally). Requires that the contact have a valid public key
     * in the local database (non null).
     *
     * @param messageBody : message to encrypt
     * @param contactJID : JID of the user to encrypt the message for.
     * @return encrypted message | null : encrypted message or null if not successful
     */
    suspend fun encryptMessage(messageBody: String, contactJID: String) :String?{
        val contactsPublicKey = repository.getContactsPublicKey(contactJID).toPublicKey()
        val cipher = Cipher.getInstance(CIPHER_TYPE)
        cipher.init(Cipher.ENCRYPT_MODE, contactsPublicKey)
        val encryptedBytes = cipher.doFinal(messageBody.toByteArray())
        val encryptedString = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        println("The encrypted string is: $encryptedString")
        return encryptedString

    }

    /**
     * Function to decrypt an incoming encrypted message based on the user's
     * private key stored in the Android KeyStore.
     *
     * @param messageBody : encrypted message
     * @return decryptedMessage | null : decrypted message or null if not successful
     */
    fun decryptMessage(messageBody: String) :String? {
        println("In decrypt Message")
        println("Decrypt string got $messageBody")
        val cipher = Cipher.getInstance(CIPHER_TYPE)
        cipher.init(Cipher.DECRYPT_MODE, getUsersPrivateKey())
        val decryptedBytes = cipher.doFinal(Base64.decode(messageBody, Base64.DEFAULT))
        val decryptedString = String(decryptedBytes)
        println("Decrypted String is: $decryptedString")
        if(decryptedString.isBlank() or decryptedString.isEmpty()){
            println("Decryption failed")
        }
        return decryptedString

    }


    private fun stringToPrivateKey(privateKeyString: String): PrivateKey? {
        val pkcs8EncodedBytes = Base64.decode(privateKeyString, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(pkcs8EncodedBytes)
        val keyFactory = KeyFactory.getInstance(CRYPTO_METHOD)
        return keyFactory.generatePrivate(keySpec)
    }

    private fun privateKeyToString(privateKey: PrivateKey) :String {
        return Base64.encodeToString(privateKey.encoded, Base64.DEFAULT)

    }

    /**
     * Kotlin extension function @see [https://kotlinlang.org/docs/reference/extensions.html] of the String class
     * to convert a given string to a PublicKey if possible. Used to convert the contact's public key
     * stored locally in the database as a string to a PublicKey so that we can encrypt a message for them.
     * @see [encryptMessage]
     *
     * @return PublicKey | null : return the public key represented by the string if possible, or null
     */
    private fun String.toPublicKey() :PublicKey? {
        val keyBytes = Base64.decode(this, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(CRYPTO_METHOD)
        return keyFactory.generatePublic(spec)
    }

    /**
     * Kotlin extension function @see [https://kotlinlang.org/docs/reference/extensions.html] of the PublicKey class
     * to convert a given public key to a string. This is used to upload the public key to the firebase database.
     * @see uploadPublicKeyToFirebase
     *
     * @return publicKeyAsString : the public key represented as a string
     */
    private fun PublicKey.toStringForFirebase(): String {
        return Base64.encodeToString(this.encoded, Base64.DEFAULT)
    }

    /**
     * Function to delete the User's RSA key pair stored in the Android Key Store.
     * Only used in development
     */
    private fun deleteAllKeys() = keyStore.deleteEntry(KEY_ALIAS)

    /**
     * Function to add a new event listener to a contact's Firebase
     * Database public key node. This will be triggered when a new contact is added
     * and the app is already running, since all listeners are registered upon successful login.
     *
     * @param contactJID: JID of the contact to add a new listener for
     */
    fun addNewEventListeners(contactJID: String){
        rsaEncryptionScope.launch(Dispatchers.IO) {

                publicKeyNodeReference.child(contactJID.substringBefore('@')).addValueEventListener(
                    object : ValueEventListener {
                        /**
                         * This method will be called with a snapshot of the data at this location. It will also be called
                         * each time that data changes.
                         *
                         * @param snapshot The current data at the location
                         */
                        override fun onDataChange(snapshot: DataSnapshot) {

                            rsaEncryptionScope.launch(Dispatchers.IO) {
                                repository.updateContactPublicKey(
                                    contactJID = contactJID,
                                    publicKey = snapshot.value.toString()
                                )
                            }


                        }

                        /**
                         * This method will be triggered in the event that this listener either failed at the server, or
                         * is removed as a result of the security and Firebase Database rules. For more information on
                         * securing your data, see: [ Security
 * Quickstart](https://firebase.google.com/docs/database/security/quickstart)
                         *
                         * @param error A description of the error that occurred
                         */
                        override fun onCancelled(error: DatabaseError) {
                            println("Some error occurred $error")
                        }

            })
        }
    }

    /**
     * Function to add Firebase value event listeners to listen for changes in the
     * contact's public key value. If a contact's public key value is updated, it will insert the new
     * value into the local database.
     *
     */
    private fun addEventListenerToPublicKeys(){

        rsaEncryptionScope.launch(Dispatchers.IO) {
            val contacts = repository.getAllContactJIDS()

            contacts.forEach { contactJID ->
                publicKeyNodeReference.child(contactJID.substringBefore('@')).addValueEventListener(
                    object : ValueEventListener {
                        /**
                         * This method will be called with a snapshot of the data at this location. It will also be called
                         * each time that data changes.
                         *
                         * @param snapshot The current data at the location
                         */
                        override fun onDataChange(snapshot: DataSnapshot) {

                            rsaEncryptionScope.launch(Dispatchers.IO) {
                                repository.updateContactPublicKey(
                                    contactJID = contactJID,
                                    publicKey = snapshot.value.toString()
                                )
                            }



                        }

                        /**
                         * This method will be triggered in the event that this listener either failed at the server, or
                         * is removed as a result of the security and Firebase Database rules. For more information on
                         * securing your data, see: [ Security
 * Quickstart](https://firebase.google.com/docs/database/security/quickstart)
                         *
                         * @param error A description of the error that occurred
                         */
                        override fun onCancelled(error: DatabaseError) {
                            println("Some error occurred $error")
                        }

                    })
            }
        }



    }


    /**
     * Companion object to ensure singleton logic of this class.
     * Also holds const values for string values used in the methods.
     */
    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tapinappkeys"
        const val CIPHER_TYPE = "RSA/ECB/PKCS1Padding"
        const val CRYPTO_METHOD = "RSA"

        @Volatile
        private var INSTANCE: RSAEncryptionManager? = null

        /**
         * Function to get the singleton instance of @see [RSAEncryptionManager]
         * and setup the RSA Encryption logic.
         * @param repository : @see [Repository]
         * @return RSAEncryptionManager : Return a singleton instance of RSAEncryptionManager
         */
        fun getRSAEncryptionManagerInstance(repository: Repository) :RSAEncryptionManager{
            val tempInstance = INSTANCE
            if (tempInstance != null) return tempInstance
            synchronized(this){
                val newTempInstance = RSAEncryptionManager()
                newTempInstance.repository = repository
                newTempInstance.addEventListenerToPublicKeys()
                //newTempInstance.deleteAllKeys()
                newTempInstance.createAsymmetricKeyPair()
                newTempInstance.verifyUserPublicKeyIsCorrect()
                //newTempInstance.testEncryptAndDecrypt()
                INSTANCE = newTempInstance
                return newTempInstance
            }
        }
    }





}