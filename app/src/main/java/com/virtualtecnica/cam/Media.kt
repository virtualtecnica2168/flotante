package com.virtualtecnica.cam

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

object Media {
    private val BASE: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    /** Video más reciente de la cámara (DCIM): id, nombre, tamaño. */
    fun latest(c: Context): Triple<Long, String, Long>? = try {
        c.contentResolver.query(
            BASE,
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE),
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE 'DCIM/%' AND ${MediaStore.MediaColumns.IS_PENDING}=0",
            null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { if (it.moveToFirst()) Triple(it.getLong(0), it.getString(1), it.getLong(2)) else null }
    } catch (e: Exception) { null }

    fun uri(id: Long): Uri = ContentUris.withAppendedId(BASE, id)
}
