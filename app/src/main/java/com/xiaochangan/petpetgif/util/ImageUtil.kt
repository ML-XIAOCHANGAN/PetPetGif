package com.xiaochangan.petpetgif.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 图片/GIF 存取与分享工具 */
object ImageUtil {

    private const val MAX_DIM = 4096   // 解码上限：保留原图尺寸，仅防极端大图 OOM（手机照片一般 <4096 不缩放）

    /** 从 content:// 或文件 Uri 解码图片，等比缩放至最长边 [maxDim] */
    fun decodeUri(context: Context, uri: Uri, maxDim: Int = MAX_DIM): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { ins ->
            decodeBytes(ins.readBytes(), maxDim)
        }
    } catch (e: Exception) {
        null
    }

    /** 从字节数组解码（网络下载用），等比缩放至最长边 [maxDim] */
    fun decodeBytes(bytes: ByteArray, maxDim: Int = MAX_DIM): Bitmap? {
        if (bytes.isEmpty()) return null
        // 先读尺寸（inJustDecodeBounds 不分配像素）
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
            sample *= 2
        }
        val real = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, real)
        } catch (e: Exception) {
            null
        }
    }

    /** 从 URL 下载图片字节（http/https，超时 15s，上限 20MB） */
    fun download(url: String): ByteArray? {
        return try {
            val u = URL(url)
            if (u.protocol != "http" && u.protocol != "https") return null
            val conn = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) PetPetGif/1.0")
                instanceFollowRedirects = true
            }
            conn.inputStream.use { ins ->
                val bos = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > 20 * 1024 * 1024) return null
                    bos.write(buf, 0, n)
                }
                bos.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 保存 GIF 到相册。API 29+ 用 MediaStore（免权限）；
     * API 26-28 需要 WRITE_EXTERNAL_STORAGE 已授权。
     * @return 保存的显示名（失败返回 null）
     */
    fun saveGifToGallery(context: Context, file: File): String? {
        return try {
            val displayName = file.name
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/摸头杀")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { os ->
                    file.inputStream().use { it.copyTo(os) }
                } ?: run {
                    resolver.delete(uri, null, null)
                    return null
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                displayName
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "摸头杀")
                if (!dir.exists() && !dir.mkdirs()) return null
                val dest = File(dir, displayName)
                file.inputStream().use { ins -> dest.outputStream().use { ins.copyTo(it) } }
                // 通知相册刷新
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(dest))
                context.sendBroadcast(intent)
                displayName
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 生成分享 Intent（FileProvider） */
    fun shareGif(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/gif"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Bitmap → ARGB IntArray */
    fun toArgbArray(bmp: Bitmap): IntArray {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return px
    }

    /** 等比缩放 Bitmap（最长边 [maxDim]） */
    fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        val max = maxOf(w, h)
        if (max <= maxDim) return src
        val scale = maxDim.toFloat() / max
        val m = Matrix().apply { postScale(scale, scale) }
        return Bitmap.createBitmap(src, 0, 0, w, h, m, true)
    }
}
