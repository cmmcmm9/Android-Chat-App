package ktorClient

import android.app.Application
import android.provider.ContactsContract
import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.io.use

/**
 * Kotlin Object (Singleton class) to handle all of the network calls.
 * Using the Ktor Client library.
 * Can send a registration request, send a request to sync the user contact's
 * with their phone contacts, and upload an avatar image (for both user and group chat)
 */
object KtorClient {
    private const val secureURL = "https://tapinapp.com:8090"
    private const val registerPath = "/register"
    private const val contactSyncPath = "/json/tapinapp/contactdump"


    /**
     * Test function to ensure connection to server is possible.
     * Only used in development.
     *
     */
    suspend fun getHello(){

        HttpClient {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }.use { client ->
            val hello = client.get<String>{
                url(secureURL)
            }
            println("Get hello got $hello")
        }


    }

    /**
     * Sends the user's contacts to the backend Ktor server
     * to sync with their TapIn contacts.
     *
     * @param uid : firebase UID of the user (lowercase)
     * @param idToken : firebase ID token (JWT)
     * @param contactList : list of contact numbers (as strings)
     */
    suspend fun contactSync(uid: String, idToken: String, contactList: List<String>) {

        val client = HttpClient {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }

        val request = client.post<HttpResponse> {
            url("$secureURL$contactSyncPath")
            contentType(ContentType.Application.Json)
            body = UserContacts(
                userID = uid,
                firebaseIDToken = idToken,
                contactNumbers = contactList,
            )

        }
        println("The request returned ${request.status}")
        if (request.status == HttpStatusCode.BadRequest) {
            println("Bad FirebaseToken")
        }
        client.close()

    }

    /**
     * Register a new user with Openfire. Sends the request to the backend Ktor server
     *
     * @param idToken : firebase ID token of the new user
     */
    suspend fun registerUserOpenfire(idToken: String){

        val client = HttpClient {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }


        val request = client.post<HttpResponse>{
            url("$secureURL$registerPath")
            body = idToken
        }

        println("The Request returned with status code $request")
        if(request.status != HttpStatusCode.Accepted){
            //Thread.sleep(10000L)
            val request2 = client.post<HttpResponse>{
                url("$secureURL$registerPath")
                body = idToken
            }
        }
        client.close()

    }

    /**
     * Uploads an image file to the backend Ktor server to be used as an avatar.
     * Either the user's avatar or a group chat avatar (if user is the owner of the chat)
     *
     * @param uploadFiles : map of the files to upload. Map<FileNameString, File>
     * @param texts : map of the text to send with the http multipart data (if any)
     */
    suspend fun uploadAvatar(uploadFiles: Map<String, File>, texts: Map<String, String>){
        HttpClient().post<HttpResponse>("$secureURL/uploads/avatar"){
            headers {
                append("Accept", ContentType.Application.Json)
            }

            body = MultiPartFormDataContent(
                formData {
                    texts.entries.forEach {
                        this.append(FormPart(it.key, it.value))
                    }
                    uploadFiles.entries.forEach {
                        this.appendInput(
                            key = it.key,
                            headers = Headers.build {
                                append(HttpHeaders.ContentDisposition,
                                    "filename=${it.value.name}")
                            },
                            size = it.value.length()
                        ) { buildPacket { writeFully(it.value.readBytes()) } }
                    }

                }
            )


        }.also {
            println("Response for upload was $it")
        }
    }

}

/**
 * Data class to store the user's contacts
 * and user information for @see[KtorClient.contactSync]
 * Using Kotlinx Serializable, which converts the data class to a JSON
 * @property userID
 * @property firebaseIDToken
 * @property contactNumbers
 */
@Serializable
data class UserContacts(
    val userID: String,
    val firebaseIDToken: String,
    val contactNumbers: List<String>,
)

/**
 * Kotlin Object to help retrieve the user's contacts.
 * Will throw an exception if the user did not give permissions
 * to the app.
 * @see[KtorClient.contactSync]
 */
object ContactFetcher {

    /**
     * Function to retrieve all of the user's stored phone numbers for their contacts
     *
     * @param application : application reference. User for cursor object to query for contact phone numbers
     * @return List<PhoneNumbersAsString> : list of phone numbers as strings (+16505551234)
     */
     fun getPhoneNumbers(application: Application) :List<String> {

        // ContentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null)

        val phoneNumbersToSend = mutableListOf<String>()

        val phoneNumbers = application.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            null,
            null,
            null
        )

        if (phoneNumbers != null) {
            while (phoneNumbers.moveToNext()) {
                val phoneNumber = phoneNumbers.getString(phoneNumbers.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)).replace("(\\s+)|(-)".toRegex(), "")

                println("Got $phoneNumber in here")
                when (phoneNumber.length) {
                    12 -> phoneNumbersToSend.add(phoneNumber)
                    11 -> phoneNumbersToSend.add("+$phoneNumber")
                    10 -> phoneNumbersToSend.add("+1$phoneNumber")
                }


            }


        }
        phoneNumbers?.close()
        return phoneNumbersToSend.distinct().toList()
    }



}