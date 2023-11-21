package com.simplemobiletools.dialer.fragments.provider

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.simplemobiletools.commons.extensions.baseConfig
import com.simplemobiletools.commons.extensions.getMyContactsCursor
import com.simplemobiletools.commons.helpers.ContactsHelper
import com.simplemobiletools.commons.helpers.MyContactsContentProvider
import com.simplemobiletools.commons.helpers.SMT_PRIVATE
import com.simplemobiletools.commons.models.contacts.Contact
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.MIN_RECENTS_THRESHOLD
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.helpers.SharedPreferencesHelper
import com.simplemobiletools.dialer.models.RecentCall
import kotlinx.coroutines.*

class RecentCallsContentProvider() : ContentProvider() {
    companion object {
        const val ID = "_id"
        const val NAME = "name"
        const val NUMBER = "number"
        const val CALL_TYPE = "call_type"
        private const val AUTHORITY = "simplemobiletools.dialer.debug.recentcallscontentprovider"
        private val BASE_CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
        private const val PATH_DATA = "data"
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_DATA).build()

        // MIME types
        const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.$AUTHORITY.data"
        const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.$AUTHORITY.data"

        // UriMatcher codes
        private const val DATA = 1
        private const val DATA_ID = 2

        private val sUriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            sUriMatcher.addURI(AUTHORITY, PATH_DATA, DATA)
            sUriMatcher.addURI(AUTHORITY, "$PATH_DATA/#", DATA_ID)
        }
    }

    override fun onCreate(): Boolean {
        Log.e("hash", "oncreate ->true from resolver")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        var cursor: MatrixCursor = MatrixCursor(
            arrayOf(ID, NAME, NUMBER, CALL_TYPE)
        )

        GlobalScope.launch {


        }
        val recentCalls = SharedPreferencesHelper.getRecentCalls(context!!)
        Log.e("hash", "true from resolver")
        for (recentCall in recentCalls) {
            cursor.addRow(arrayOf(recentCall.id, recentCall.name, recentCall.phoneNumber, recentCall.type))
        }
        Log.e("hash", "size of list -> ${recentCalls.size}")
        Log.e("hash-contentProvider", "data -> ${Gson().toJson(recentCalls)}")

        return cursor
    }
        // Implement other required methods like insert, update, delete as needed

        // Return the MIME type
        override fun getType(uri: Uri): String? {
            // Determine the MIME type based on the URI pattern
            when (sUriMatcher.match(uri)) {
                DATA -> return CONTENT_TYPE
                DATA_ID -> return CONTENT_ITEM_TYPE
                else -> throw IllegalArgumentException("Unknown URI: $uri")
            }
        }

        override fun insert(p0: Uri, p1: ContentValues?): Uri? {
            try {
                return ContentUris.withAppendedId(CONTENT_URI, -11)
            } catch (e: Exception) {
                return throw IllegalArgumentException("Unknown URI: $p0")
            }
            return null
        }

        override fun delete(p0: Uri, p1: String?, p2: Array<out String>?): Int {
            return -1
        }

        override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int {
            return -1
        }

        // Helper method to get all recent calls
        private fun getAllRecentCalls(context: Context): List<RecentCall> {
            var allRecentCalls = listOf<RecentCall>()
            val privateCursor = context.getMyContactsCursor(false, true)
            val groupSubsequentCalls = context.config?.groupSubsequentCalls ?: false
            Log.e("hash", "onContent->size of recentList ${allRecentCalls.size}")
            val querySize = allRecentCalls.size.coerceAtLeast(MIN_RECENTS_THRESHOLD)
            RecentsHelper(context).getRecentCalls(groupSubsequentCalls, querySize) { recents ->
                ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                    val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                    allRecentCalls = recents
                        .setNamesIfEmpty(contacts, privateContacts)
                        .hidePrivateContacts(privateContacts, SMT_PRIVATE in context.baseConfig.ignoredContactSources)
                }
            }
            return allRecentCalls
        }

        private fun List<RecentCall>.setNamesIfEmpty(contacts: List<Contact>, privateContacts: List<Contact>): ArrayList<RecentCall> {
            val contactsWithNumbers = contacts.filter { it.phoneNumbers.isNotEmpty() }
            return map { recent ->
                if (recent.phoneNumber == recent.name) {
                    val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(recent.phoneNumber) }
                    val contact = contactsWithNumbers.firstOrNull { it.phoneNumbers.first().normalizedNumber == recent.phoneNumber }

                    when {
                        privateContact != null -> recent.copy(name = privateContact.getNameToDisplay())
                        contact != null -> recent.copy(name = contact.getNameToDisplay())
                        else -> recent
                    }
                } else {
                    recent
                }
            } as ArrayList
        }

        // hide private contacts from recent calls
        private fun List<RecentCall>.hidePrivateContacts(privateContacts: List<Contact>, shouldHide: Boolean): List<RecentCall> {
            return if (shouldHide) {
                filterNot { recent ->
                    val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
                    recent.phoneNumber in privateNumbers
                }
            } else {
                this
            }
        }
    }

