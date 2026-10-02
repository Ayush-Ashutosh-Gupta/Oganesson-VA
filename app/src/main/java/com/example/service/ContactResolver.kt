package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Helper to resolve contact names into phone numbers from the device's Contacts provider.
 * Supports exact matching, multiple match disambiguation, and structured listing.
 */
object ContactResolver {

    private const val TAG = "ContactResolver"

    data class ContactMatch(
        val displayName: String,
        val phoneNumber: String
    )

    /**
     * Finds the single best matching contact for a spoken name.
     */
    fun findContactByName(context: Context, spokenName: String): ContactMatch? {
        val matches = searchContactsByName(context, spokenName)
        return matches.firstOrNull()
    }

    /**
     * Searches device contacts matching a spoken name query.
     * Returns a list of matching contacts (up to 5) for user disambiguation if multiple exist.
     */
    fun searchContactsByName(context: Context, spokenName: String): List<ContactMatch> {
        val query = spokenName.trim().lowercase()
        if (query.isBlank()) return emptyList()

        val hasReadPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadPermission) {
            Log.w(TAG, "READ_CONTACTS permission not granted")
            return emptyList()
        }

        val contentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.IS_PRIMARY
        )

        val exactMatches = mutableListOf<ContactMatch>()
        val prefixMatches = mutableListOf<ContactMatch>()
        val substringMatches = mutableListOf<ContactMatch>()
        val seenNumbers = mutableSetOf<String>()

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            if (cursor != null) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    val number = cursor.getString(numberIndex) ?: continue
                    val cleanNumber = number.filter { it.isDigit() || it == '+' }
                    if (cleanNumber.isBlank() || seenNumbers.contains(cleanNumber)) continue
                    seenNumbers.add(cleanNumber)

                    val lowerName = name.lowercase().trim()
                    val match = ContactMatch(name, number)

                    when {
                        lowerName == query -> exactMatches.add(match)
                        lowerName.startsWith(query) -> prefixMatches.add(match)
                        lowerName.contains(query) -> substringMatches.add(match)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
        } finally {
            cursor?.close()
        }

        // If there's exactly 1 exact match and no ambiguity, return it
        if (exactMatches.size == 1 && prefixMatches.isEmpty()) {
            return exactMatches
        }

        val combined = (exactMatches + prefixMatches + substringMatches).distinctBy { it.phoneNumber }
        return combined.take(5)
    }
}

/**
 * Manages pending contact disambiguation when a call command matches multiple contacts.
 */
object CallDisambiguationManager {
    private var pendingList: List<ContactResolver.ContactMatch> = emptyList()

    fun setPendingContacts(contacts: List<ContactResolver.ContactMatch>) {
        pendingList = contacts
    }

    fun getPendingContacts(): List<ContactResolver.ContactMatch> = pendingList

    fun hasPending(): Boolean = pendingList.isNotEmpty()

    fun clear() {
        pendingList = emptyList()
    }

    /**
     * Resolves user choice from spoken or typed input:
     * - "1", "first", "one", "option 1", "call 1"
     * - "2", "second", "two"
     * - Or matching contact name
     */
    fun resolveChoice(input: String): ContactResolver.ContactMatch? {
        if (pendingList.isEmpty()) return null
        val lower = input.trim().lowercase()

        val index = when {
            lower.contains("first") || lower.contains("option 1") || lower.contains("number 1") || lower == "1" || lower.startsWith("1 ") || lower.endsWith(" 1") || lower.contains(" one") || lower == "one" -> 0
            lower.contains("second") || lower.contains("option 2") || lower.contains("number 2") || lower == "2" || lower.startsWith("2 ") || lower.endsWith(" 2") || lower.contains(" two") || lower == "two" -> 1
            lower.contains("third") || lower.contains("option 3") || lower.contains("number 3") || lower == "3" || lower.startsWith("3 ") || lower.endsWith(" 3") || lower.contains(" three") || lower == "three" -> 2
            lower.contains("fourth") || lower.contains("option 4") || lower.contains("number 4") || lower == "4" || lower.startsWith("4 ") || lower.endsWith(" 4") || lower.contains(" four") || lower == "four" -> 3
            lower.contains("fifth") || lower.contains("option 5") || lower.contains("number 5") || lower == "5" || lower.startsWith("5 ") || lower.endsWith(" 5") || lower.contains(" five") || lower == "five" -> 4
            else -> {
                pendingList.indexOfFirst { lower.contains(it.displayName.lowercase()) }
            }
        }

        return if (index in pendingList.indices) {
            val chosen = pendingList[index]
            clear()
            chosen
        } else {
            null
        }
    }
}
