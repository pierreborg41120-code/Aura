package com.aura.launcher.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat

/** Un contact trouvé par la recherche du tiroir. */
data class ContactResult(
    val id: Long,
    val lookupKey: String,
    val name: String,
    /** Miniature de la photo (content://…), null si le contact n'en a pas. */
    val photoUri: String?,
    /** Numéro principal, null si le contact n'a pas de téléphone. */
    val phone: String?,
) {
    val key: String get() = "contact:$id"
}

/** Lecture des contacts du téléphone (ContactsContract). Toutes les fonctions sont à appeler hors du thread principal. */
object ContactSearch {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Contacts dont le nom correspond à [query], dans l'ordre proposé par Android, [limit] au plus. */
    fun search(context: Context, query: String, limit: Int = 5): List<ContactResult> {
        val q = query.trim()
        if (q.isEmpty() || !hasPermission(context)) return emptyList()
        val uri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon()
            .appendPath(q)
            .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, limit.toString())
            .build()
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )
        val found = mutableListOf<ContactResult>()
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext() && found.size < limit) {
                    val id = c.getLong(0)
                    val name = c.getString(2)
                    if (!name.isNullOrBlank()) {
                        val hasPhone = c.getInt(4) > 0
                        found += ContactResult(
                            id = id,
                            lookupKey = c.getString(1).orEmpty(),
                            name = name,
                            photoUri = c.getString(3),
                            phone = if (hasPhone) primaryPhone(context, id) else null,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Permission retirée entre-temps, fournisseur de contacts indisponible… : pas de résultat.
            return emptyList()
        }
        return found
    }

    /** Numéro principal du contact (celui marqué « par défaut » s'il y en a un). */
    private fun primaryPhone(context: Context, contactId: Long): String? = try {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, " +
                "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
        )?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
    } catch (e: Exception) {
        null
    }

    /** Lien vers la fiche du contact. */
    fun contactUri(contact: ContactResult): Uri =
        ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
            ?: Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id.toString())

    /** Miniature de la photo, carrée, de [sizePx] pixels au plus. */
    fun loadPhoto(context: Context, contact: ContactResult, sizePx: Int): ImageBitmap? {
        val uri = contact.photoUri ?: return null
        return try {
            val bitmap = context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null
            val scaled = if (bitmap.width > sizePx && sizePx > 0) {
                Bitmap.createScaledBitmap(bitmap, sizePx, sizePx * bitmap.height / bitmap.width.coerceAtLeast(1), true)
            } else {
                bitmap
            }
            scaled.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}
