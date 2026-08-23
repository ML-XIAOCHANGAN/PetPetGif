package com.xiaochangan.petpetgif.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 摸头杀渲染引擎 —— 从网页版 main.js 逐行移植（benisland/xhnav 摸头杀生成器）。
 *
 * 核心：5 帧手部精灵图 + 用户图片，按帧偏移量做「压扁-回弹」变形，
 * 手部从上方压下，图片被压缩变矮再弹回。
 */
object PetPetEngine {

    const val OUT_SIZE = 112          // 输出画布 112×112
    const val MAX_FRAME = 4           // 帧号 0..4 共 5 帧
    const val DEFAULT_SPRITE_X = 14
    const val DEFAULT_SPRITE_Y = 20
    const val DEFAULT_SPRITE_WIDTH = 112

    /** 网页版 DEFAULT_SQUISH=1.25 / DEFAULT_SCALE=0.875（对应滑块 125 / 85） */
    const val DEFAULT_SQUISH = 1.25f
    const val DEFAULT_SCALE = 0.875f
    const val DEFAULT_FPS = 16

    /** 每帧偏移：{x, y, w, h}，x/y 平移、w/h 缩放（压扁/回弹） */
    private val FRAME_OFFSETS = arrayOf(
        intArrayOf(0, 0, 0, 0),          // 帧0：手抬起，图片原样
        intArrayOf(-4, 12, 4, -12),      // 帧1：手按下，图片变矮
        intArrayOf(-12, 18, 12, -18),    // 帧2：按到最底
        intArrayOf(-8, 12, 4, -12),      // 帧3：手抬起，回弹中
        intArrayOf(-4, 0, 0, 0),         // 帧4：接近恢复
    )

    data class Params(
        var squish: Float = DEFAULT_SQUISH,      // 压扁强度（1.00 - 3.00）
        var scale: Float = DEFAULT_SCALE,        // 大小（0.15 - 2.00）
        var spriteX: Int = DEFAULT_SPRITE_X,     // 图片位置（微调拖拽）
        var spriteY: Int = DEFAULT_SPRITE_Y,
        var spriteWidth: Int = DEFAULT_SPRITE_WIDTH,
        var spriteHeight: Int = DEFAULT_SPRITE_WIDTH,
        var flip: Boolean = false,               // 水平翻转
        var fps: Int = DEFAULT_FPS,              // 速度
    ) {
        /** 帧延迟毫秒 = 1000/fps（网页版 delay） */
        val frameDelayMs: Int get() = (1000f / fps).roundToInt()
    }

    /** 按原图比例刷新精灵高度（网页版 refreshSprite） */
    fun refreshSpriteHeight(p: Params, imgWidth: Int, imgHeight: Int) {
        if (imgWidth <= 0) return
        p.spriteHeight = (p.spriteWidth.toFloat() * imgHeight / imgWidth).roundToInt()
            .coerceAtLeast(1)
    }

    data class SpriteRect(val dx: Int, val dy: Int, val dw: Int, val dh: Int)

    /** 网页版 getSpriteFrame：JS ~~(x) 向零取整 == Kotlin toInt()（正值时等同 floor） */
    fun getSpriteFrame(p: Params, frame: Int): SpriteRect {
        val off = FRAME_OFFSETS[frame.coerceIn(0, MAX_FRAME)]
        val dx = (p.spriteX + off[0] * (p.squish * 0.4f)).toInt()
        val dy = (p.spriteY + off[1] * (p.squish * 0.9f)).toInt()
        val dw = ((p.spriteWidth + off[2] * p.squish) * p.scale).toInt()
        val dh = ((p.spriteHeight + off[3] * p.squish) * p.scale).toInt()
        return SpriteRect(dx, dy, dw, dh)
    }

    private val spritePaint = Paint().apply {
        isFilterBitmap = false          // 网页版 imageSmoothingEnabled=false（保持像素感）
        isAntiAlias = false
    }
    private val handPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }

    /**
     * 渲染一帧到 112×112 画布。
     * @param transparentBg true = 输出 GIF 帧（透明像素填绿，供 GIF 透明色处理）
     */
    fun renderFrame(
        canvas: Canvas,
        p: Params,
        frame: Int,
        hand: Bitmap,
        sprite: Bitmap,
        transparentBg: Boolean = false,
    ) {
        val cf = getSpriteFrame(p, frame)

        if (transparentBg) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } else {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }

        // ---- 图片（网页版 drawImage 逻辑：翻转 = translate(dx,dy) 后 scale(-1,1)，目标矩形保持正宽）----
        // ⚠️ Canvas 变换是后乘：必须先 translate 再 scale，
        //    得到 T∘S（点 x' → dx+dw-x'），镜像轴在 dx+dw 处，画面落在 [dx, dx+dw]
        canvas.save()
        val dw = abs(cf.dw)
        if (p.flip) {
            canvas.translate((cf.dx + dw).toFloat(), cf.dy.toFloat())
            canvas.scale(-1f, 1f)
        } else {
            canvas.translate(cf.dx.toFloat(), cf.dy.toFloat())
        }
        val dst = Rect(0, 0, dw, cf.dh)
        canvas.drawBitmap(sprite, null, dst, spritePaint)
        canvas.restore()

        // ---- 手部（网页版：源区 = frame*112,0,112,112；目标 y = max(0, dy*0.75 - spriteY - 0.5)）----
        val handDy = max(0, (cf.dy * 0.75f - max(0, p.spriteY) - 0.5f).toInt())
        val src = Rect(frame * OUT_SIZE, 0, (frame + 1) * OUT_SIZE, OUT_SIZE)
        val dstHand = Rect(0, handDy, OUT_SIZE, OUT_SIZE + handDy)
        canvas.drawBitmap(hand, src, dstHand, handPaint)
    }

    /** 渲染一帧为独立 Bitmap（供预览循环 / GIF 编码） */
    fun renderFrameBitmap(p: Params, frame: Int, hand: Bitmap, sprite: Bitmap, transparentBg: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(OUT_SIZE, OUT_SIZE, Bitmap.Config.ARGB_8888)
        renderFrame(Canvas(bmp), p, frame, hand, sprite, transparentBg)
        return bmp
    }

    /** 渲染全部 5 帧 */
    fun renderAllFrames(p: Params, hand: Bitmap, sprite: Bitmap, transparentBg: Boolean = false): List<Bitmap> =
        (0..MAX_FRAME).map { renderFrameBitmap(p, it, hand, sprite, transparentBg) }

    /**
     * 网页版 optimizeFrameColors：透明像素(<120)填纯绿、绿色通道钳到 250，
     * 全部像素 alpha 置 255 —— 供 GIF 编码器以 0x00FF00 为透明色。
     */
    fun optimizeFrameForGif(bmp: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            var r = (c ushr 16) and 0xFF
            var g = (c ushr 8) and 0xFF
            var b = c and 0xFF
            if (g > 250) g = 250
            if (a < 120) {
                r = 0; g = 255; b = 0
            }
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        out.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return out
    }
}
