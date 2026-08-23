package com.xiaochangan.petpetgif.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaochangan.petpetgif.R
import com.xiaochangan.petpetgif.engine.PetPetEngine
import com.xiaochangan.petpetgif.gif.AnimatedGifEncoder
import com.xiaochangan.petpetgif.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 摸头杀生成器 ViewModel：参数状态、图片加载、GIF 导出、保存/分享。
 *
 * ⚠️ 线程约定：Compose snapshot 状态（params/sprite/resultFile/exporting…）
 * 只能在主线程读写；耗时操作（解码/缩放/GIF 编码）放 Dispatchers.Default，
 * 状态变更一律回到主线程（viewModelScope.launch 默认 Main）。
 */
class PetPetViewModel(app: Application) : AndroidViewModel(app) {

    var params by mutableStateOf(PetPetEngine.Params())
        private set

    /** 用户图片（≤512px，原始比例） */
    var sprite by mutableStateOf<Bitmap?>(null)
        private set

    /** 已生成的 GIF 文件 */
    var resultFile by mutableStateOf<File?>(null)
        private set

    var resultInfo by mutableStateOf("")
        private set

    var exporting by mutableStateOf(false)
        private set

    var notice by mutableStateOf<String?>(null)
        private set

    var fileName by mutableStateOf("")
        private set

    /** URL 上传输入框文本 */
    var urlText by mutableStateOf("")

    /** 手部精灵图（560×112 五帧），懒加载一次 */
    val hand: Bitmap by lazy {
        BitmapFactory.decodeResource(app.resources, R.drawable.petpet_hand)!!
    }

    init {
        // 首启载入网页同款示例图
        loadSample()
    }

    fun loadSample() {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                BitmapFactory.decodeResource(getApplication<Application>().resources, R.drawable.petpet_sample)
                    ?.let { ImageUtil.scaleBitmap(it, 512) }
            }
            if (bmp != null) {
                applySprite(bmp)
                fileName = "示例图片"
            }
        }
    }

    /** 从相册/文件 Uri 载入图片 */
    fun loadImageUri(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                ImageUtil.decodeUri(ctx, uri)?.let { ImageUtil.scaleBitmap(it, 512) }
            }
            if (bmp == null) {
                showNotice("图片加载失败，换一张试试")
            } else {
                applySprite(bmp)
                fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "本地图片"
            }
        }
    }

    /** 从 URL 载入图片 */
    fun loadImageUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                ImageUtil.download(trimmed)
                    ?.let { ImageUtil.decodeBytes(it) }
                    ?.let { ImageUtil.scaleBitmap(it, 512) }
            }
            if (bmp == null) {
                showNotice("URL 图片加载失败，检查地址或网络")
            } else {
                applySprite(bmp)
                fileName = trimmed.substringAfterLast('/').take(24)
            }
        }
    }

    /** 应用新图片并刷新精灵高度（网页版 refreshSprite）——必须主线程调用 */
    private fun applySprite(bmp: Bitmap) {
        PetPetEngine.refreshSpriteHeight(params, bmp.width, bmp.height)
        sprite = bmp
        resultFile = null
        resultInfo = ""
    }

    // ---- 参数操作（全部主线程） ----

    fun setScale(value: Float) {
        params = params.copy(scale = value.coerceIn(0.15f, 2f))
    }

    fun setSquish(value: Float) {
        params = params.copy(squish = value.coerceIn(1f, 3f))
    }

    fun setFps(value: Int) {
        params = params.copy(fps = value.coerceIn(2, 60))
    }

    fun setFlip(flip: Boolean) {
        params = params.copy(flip = flip)
    }

    fun nudgeSprite(dx: Int, dy: Int) {
        params = params.copy(
            spriteX = (params.spriteX + dx).coerceIn(-200, 200),
            spriteY = (params.spriteY + dy).coerceIn(-200, 200),
        )
    }

    fun reset() {
        params = PetPetEngine.Params()
        sprite?.let { PetPetEngine.refreshSpriteHeight(params, it.width, it.height) }
        resultFile = null
        resultInfo = ""
    }

    fun consumeNotice() {
        notice = null
    }

    /** 外部触发提示（如权限拒绝） */
    fun notify(msg: String) {
        notice = msg
    }

    private fun showNotice(msg: String) {
        notice = msg
    }

    // ---- GIF 导出 ----

    /** 渲染 5 帧（真实透明）→ 编码 GIF → 写入 cache/petpet/ */
    fun exportGif(onDone: (File?) -> Unit = {}) {
        val spr = sprite ?: run {
            showNotice("请先上传一张图片")
            return
        }
        if (exporting) return
        exporting = true
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) {
                    val frames = PetPetEngine.renderAllFrames(params, hand, spr)
                    val enc = AnimatedGifEncoder()
                    enc.encode(
                        frames.map { ImageUtil.toArgbArray(it) },
                        PetPetEngine.OUT_SIZE,
                        PetPetEngine.OUT_SIZE,
                        delayMs = params.frameDelayMs,
                        repeat = 0, // 无限循环
                    )
                }
                val dir = File(getApplication<Application>().cacheDir, "petpet").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(dir, "摸头杀_$stamp.gif")
                file.writeBytes(bytes)
                resultFile = file
                val kb = "%.1f".format(bytes.size / 1024.0)
                resultInfo = "$kb KB · ${params.fps} fps · 5 帧"
                onDone(file)
            } catch (e: Exception) {
                showNotice("生成失败：${e.message}")
            } finally {
                exporting = false
            }
        }
    }

    /** 保存到相册（API 26-28 需先授权 WRITE_EXTERNAL_STORAGE） */
    fun saveToGallery(): Boolean {
        val f = resultFile ?: return false
        val name = ImageUtil.saveGifToGallery(getApplication(), f)
        if (name != null) {
            showNotice("已保存到相册：$name")
            return true
        }
        showNotice("保存失败，请检查存储权限")
        return false
    }

    /** 分享 GIF */
    fun shareGif(): Intent? {
        val f = resultFile ?: return null
        val ctx = getApplication<Application>()
        val intent = ImageUtil.shareGif(ctx, f)
        return Intent.createChooser(intent, "分享摸头杀 GIF")
    }
}
