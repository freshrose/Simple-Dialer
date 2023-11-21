package com.simplemobiletools.dialer.helpers

import android.content.ContentProviderOperation
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.util.Log
import android.widget.Toast
import com.simplemobiletools.commons.extensions.getMyContactsCursor
import com.simplemobiletools.commons.extensions.getPhoneNumberTypeText
import com.simplemobiletools.commons.helpers.ContactsHelper
import com.simplemobiletools.commons.helpers.MyContactsContentProvider
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.extensions.isConference
import com.simplemobiletools.dialer.models.CallContact

fun addContact(name: String, phoneNumber: String, context: Context) {
    val DisplayName = name
    val MobileNumber = phoneNumber

    val ops = ArrayList<ContentProviderOperation>()

    ops.add(
        ContentProviderOperation.newInsert(
            ContactsContract.RawContacts.CONTENT_URI
        )
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build()
    )

    //------------------------------------------------------ Names

    //------------------------------------------------------ Names
    if (DisplayName != null) {
        ops.add(
            ContentProviderOperation.newInsert(
                ContactsContract.Data.CONTENT_URI
            )
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                )
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                    DisplayName
                ).build()
        )
    }

    //------------------------------------------------------ Mobile Number

    //------------------------------------------------------ Mobile Number
    if (MobileNumber != null) {
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, MobileNumber)
                .withValue(
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                )
                .build()
        )
    }

    // Asking the Contact provider to create a new contact

    // Asking the Contact provider to create a new contact
    try {
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        Log.e("hash", "log =$e")
        Toast.makeText(context, "Exception: " + e.message, Toast.LENGTH_SHORT).show()
    }
}

fun getCallContact(context: Context, call: Call?, callback: (CallContact) -> Unit) {
    if (call.isConference()) {
        callback(CallContact(context.getString(R.string.conference), "", "", ""))
        return
    }

    val privateCursor = context.getMyContactsCursor(false, true)
    ensureBackgroundThread {
        val callContact = CallContact("", "", "", "")
        val handle = try {
            call?.details?.handle?.toString()
        } catch (e: NullPointerException) {
            null
        }

        if (handle == null) {
            callback(callContact)
            return@ensureBackgroundThread
        }

        val uri = Uri.decode(handle)
        if (uri.startsWith("tel:")) {
            val number = uri.substringAfter("tel:")
            ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                }

                val contactsWithMultipleNumbers = contacts.filter { it.phoneNumbers.size > 1 }
                val numbersToContactIDMap = HashMap<String, Int>()
                contactsWithMultipleNumbers.forEach { contact ->
                    contact.phoneNumbers.forEach { phoneNumber ->
                        numbersToContactIDMap[phoneNumber.value] = contact.contactId
                        numbersToContactIDMap[phoneNumber.normalizedNumber] = contact.contactId
                    }
                }

                callContact.number = number
                val contact = contacts.firstOrNull { it.doesHavePhoneNumber(number) }
                if (contact != null) {
                    callContact.name = contact.getNameToDisplay()
                    callContact.photoUri = contact.photoUri

                    if (contact.phoneNumbers.size > 1) {
                        val specificPhoneNumber = contact.phoneNumbers.firstOrNull { it.value == number }
                        if (specificPhoneNumber != null) {
                            callContact.numberLabel = context.getPhoneNumberTypeText(specificPhoneNumber.type, specificPhoneNumber.label)
                        }
                    }
                } else {
                    Log.e("hash", "user Data-> $number")

//                    Log.e("hash","app installed ->${isCallerAllowed(context)}")
                    try {
                        val contentResolver = context.contentResolver
                        val uri = Uri.parse("content://cz.freshflow.app.ff.MyAndroidContentProvider/phone_lookup")
                        /*           val projection = arrayOf(
                                       "accountName",
                                       "accountType",
                                       "displayName",
                                       "typeResourceId",
                                       "exportSupport",
                                       "shortcutSupport",
                                       "anyUris"
                                   )*/
                        val projection = arrayOf(
                            "_id",
                            "contact_id",
                            "display_name",
                            "photo_thumb_uri",
                            "photo_uri",
                            "type",
                            "label",
                            "lookup",
                            "display_name_alt"
                        )
                        val selection = "phone_number = ?"
                        val selectionArgs = arrayOf(number)
                        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
                        Log.e("hash-", "step#1 -> qery -permission granted")

                        if (cursor != null) {

                            if (cursor.moveToFirst()) {
                                Log.e("hash-cursor-2", "cursor -> true")
                                val idIndex = cursor.getColumnIndex("display_name")
                                Log.e("hash-cursor-1", "display_name -> ${cursor.getString(idIndex)}")
                                callContact.name = cursor.getString(idIndex)
                                addContact(callContact.name, number, context)
                            }
                            Log.e("hash-cursor-1", "cursor -> true")
                        } else {
                            Log.e("hash-e", "cursor is null")
                            callContact.name = "No Name"
                        }
                    } catch (e: Exception) {
                        Log.e("hash", "print->$e")
                    }
                }
                callback(callContact)
            }
        }
    }
}
