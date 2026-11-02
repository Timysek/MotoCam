package cz.custom.motogcampro

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.location.Location
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object MotionPhotoMuxer {

    fun enhanceNightJpegIfNeeded(jpegFile: File, applyNightBoost: Boolean) {
        if (!applyNightBoost || !jpegFile.exists()) return
        try {
            val oldExif = ExifInterface(jpegFile.absolutePath)
            val orientation = oldExif.getAttribute(ExifInterface.TAG_ORIENTATION)
            val latLong = FloatArray(2)
            val hasGps = oldExif.getLatLong(latLong)

            val original = BitmapFactory.decodeFile(jpegFile.absolutePath) ?: return
            val boosted = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(boosted)

            val gain = 1.65f
            val bias = 18f
            val cm = ColorMatrix(
                floatArrayOf(
                    gain, 0f, 0f, 0f, bias,
                    0f, gain, 0f, 0f, bias,
                    0f, 0f, gain, 0f, bias,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
            canvas.drawBitmap(original, 0f, 0f, paint)

            FileOutputStream(jpegFile).use { out ->
                boosted.compress(Bitmap.CompressFormat.JPEG, 98, out)
            }
            original.recycle()
            boosted.recycle()

            val newExif = ExifInterface(jpegFile.absolutePath)
            if (orientation != null) {
                newExif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation)
            }
            if (hasGps) {
                newExif.setLatLong(latLong[0].toDouble(), latLong[1].toDouble())
            }
            newExif.saveAttributes()
        } catch (_: Exception) {}
    }

    fun createMotionPhoto(
        context: Context,
        jpegFile: File,
        mp4File: File,
        location: Location? = null,
        nightBoost: Boolean = false,
        presentationTimestampUs: Long = 1_000_000L
    ): Uri? {
        if (!jpegFile.exists() || !mp4File.exists()) return null

        if (nightBoost) {
            enhanceNightJpegIfNeeded(jpegFile, true)
        }

        if (location != null) {
            try {
                val exif = ExifInterface(jpegFile.absolutePath)
                exif.setLatLong(location.latitude, location.longitude)
                exif.saveAttributes()
            } catch (_: Exception) {}
        }

        val jpegBytes = jpegFile.readBytes()
        val mp4Bytes = mp4File.readBytes()
        if (jpegBytes.size < 4 || jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) return null

        val xmpSegment = buildXmpApp1Segment(mp4Bytes.size, presentationTimestampUs)
        val outputStream = ByteArrayOutputStream(jpegBytes.size + xmpSegment.size + mp4Bytes.size)
        outputStream.write(jpegBytes, 0, 2)
        outputStream.write(xmpSegment)
        outputStream.write(jpegBytes, 2, jpegBytes.size - 2)
        outputStream.write(mp4Bytes)

        val fileName = "MOTO_${System.currentTimeMillis()}.MP.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Camera")
            if (location != null) {
                put(MediaStore.Images.Media.LATITUDE, location.latitude)
                put(MediaStore.Images.Media.LONGITUDE, location.longitude)
            }
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        resolver.openOutputStream(uri)?.use { out ->
            out.write(outputStream.toByteArray())
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        jpegFile.delete()
        mp4File.delete()
        return uri
    }

    private fun buildXmpApp1Segment(mp4Length: Int, timestampUs: Long): ByteArray {
        val xmpXml = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about=""
                    xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
                    xmlns:Container="http://ns.google.com/photos/dd/1.0/container/"
                    xmlns:Item="http://ns.google.com/photos/dd/1.0/item/"
                    GCamera:MotionPhoto="1"
                    GCamera:MotionPhotoVersion="1"
                    GCamera:MotionPhotoPresentationTimestampUs="$timestampUs"
                    GCamera:MicroVideo="1"
                    GCamera:MicroVideoVersion="1"
                    GCamera:MicroVideoOffset="$mp4Length"
                    GCamera:MicroVideoPresentationTimestampUs="$timestampUs">
                  <Container:Directory>
                    <rdf:Seq>
                      <rdf:li rdf:parseType="Resource"><Container:Item Item:Semantic="Primary" Item:Mime="image/jpeg"/></rdf:li>
                      <rdf:li rdf:parseType="Resource"><Container:Item Item:Semantic="MotionPhoto" Item:Mime="video/mp4" Item:Length="$mp4Length" Item:Padding="0"/></rdf:li>
                    </rdf:Seq>
                  </Container:Directory>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()

        val nsBytes = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.UTF_8)
        val xmlBytes = xmpXml.toByteArray(Charsets.UTF_8)
        val payloadLen = nsBytes.size + xmlBytes.size + 2
        val segment = ByteArrayOutputStream(payloadLen + 2)
        segment.write(0xFF)
        segment.write(0xE1)
        segment.write((payloadLen shr 8) and 0xFF)
        segment.write(payloadLen and 0xFF)
        segment.write(nsBytes)
        segment.write(xmlBytes)
        return segment.toByteArray()
    }
}
