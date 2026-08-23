package com.xiaochangan.petpetgif.gif

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * AnimatedGifEncoder —— GIF89a 动图编码器。
 *
 * 原始代码：Kevin Weiner, FM Software v1.03（无版权声明，可自由使用），
 * 本文件为 Kotlin 移植，主要改动：
 *  - java.awt.Color → Int（ARGB）
 *  - BufferedImage → android.graphics.Bitmap，并暴露纯 Kotlin 的
 *    [addFramePixels]（IntArray ARGB）入口供 JVM 单测验证
 *
 * 用法：
 *  ```
 *  val enc = AnimatedGifEncoder().apply {
 *      start(stream); setDelay(ms); setRepeat(0); setQuality(10)
 *  }
 *  enc.addFrame(bitmap) ... enc.finish()
 *  ```
 */
class AnimatedGifEncoder {

    private var width = 0            // image size
    private var height = 0

    private var transparent: Int? = null   // transparent color (ARGB) if given
    private var transIndex = -1            // transparent index in color table

    private var repeat = -1                // no repeat
    private var delay = 0                  // frame delay (hundredths)
    private var started = false            // ready to output frames
    private var out: OutputStream? = null

    private var indexedPixels: ByteArray? = null  // converted frame indexed to palette
    private var colorDepth = 0                    // number of bit planes
    private var colorTab: ByteArray? = null       // RGB palette
    private val usedEntry = BooleanArray(256)     // active palette entries
    private var palSize = 7                       // color table size (bits-1)
    private var dispose = -1                      // disposal code (-1 = use default)
    private var closeStream = false               // close stream when finished
    private var firstFrame = true
    private var sizeSet = false                   // if false, get size from first frame
    private var sample = 10                       // default sample interval for quantizer

    /**
     * 设置帧间延迟毫秒（网页版 delay：1000/fps）。
     */
    fun setDelay(ms: Int) {
        delay = Math.round(ms / 10.0f)
    }

    /**
     * 设置 GIF 帧释放码（disposal code）。默认：无透明色=0，有透明色=2。
     */
    fun setDispose(code: Int) {
        if (code >= 0) {
            dispose = code
        }
    }

    /**
     * 设置循环次数。默认 1；0 = 无限循环。必须在第一帧前调用。
     */
    fun setRepeat(iter: Int) {
        if (iter >= 0) {
            repeat = iter
        }
    }

    /**
     * 设置透明色（ARGB int）。量化后最接近该色的调色板条目成为透明色。
     * 传 null 表示不显式指定：编码器会自动检测 alpha<0x80 的像素并分配透明索引。
     */
    fun setTransparent(color: Int?) {
        transparent = color
    }

    /**
     * 添加 Bitmap 帧（ARGB_8888）。
     */
    fun addFrame(im: Bitmap): Boolean {
        if (im.isRecycled) return false
        val w = im.width
        val h = im.height
        val pixels = IntArray(w * h)
        im.getPixels(pixels, 0, w, 0, 0, w, h)
        return addFramePixels(pixels, w, h)
    }

    /**
     * 添加纯像素帧（ARGB int 数组）——JVM 单测友好。
     */
    fun addFramePixels(pixels: IntArray, w: Int, h: Int): Boolean {
        if (pixels.isEmpty() || !started) {
            return false
        }
        var ok = true
        try {
            if (!sizeSet) {
                // use first frame's size
                setSize(w, h)
            }

            analyzePixels(pixels) // build color table & map pixels
            if (firstFrame) {
                writeLSD()       // logical screen descriptor
                writePalette()   // global color table
                if (repeat >= 0) {
                    // use NS app extension to indicate reps
                    writeNetscapeExt()
                }
            }
            writeGraphicCtrlExt() // write graphic control extension
            writeImageDesc()      // image descriptor
            if (!firstFrame) {
                writePalette()    // local color table
            }
            writePixels()         // encode and write pixel data
            firstFrame = false
        } catch (e: IOException) {
            ok = false
        }
        return ok
    }

    /**
     * 结束写入，输出 GIF trailer。不关闭流。
     */
    fun finish(): Boolean {
        if (!started) {
            return false
        }
        var ok = true
        started = false
        try {
            out?.write(0x3b) // gif trailer
            out?.flush()
            if (closeStream) {
                out?.close()
            }
        } catch (e: IOException) {
            ok = false
        }

        // reset for subsequent use
        transIndex = -1
        out = null
        indexedPixels = null
        colorTab = null
        closeStream = false
        firstFrame = true

        return ok
    }

    /**
     * 设置帧率（fps）。等价 setDelay(1000/fps)。
     */
    fun setFrameRate(fps: Float) {
        if (fps != 0f) {
            delay = Math.round(100f / fps)
        }
    }

    /**
     * 设置量化质量（1 最好最慢，10 默认，>20 无明显提速）。
     */
    fun setQuality(quality: Int) {
        sample = if (quality < 1) 1 else quality
    }

    /**
     * 设置帧尺寸（默认取第一帧）。
     */
    fun setSize(w: Int, h: Int) {
        if (started && !firstFrame) {
            return
        }
        width = if (w < 1) 320 else w
        height = if (h < 1) 240 else h
        sizeSet = true
    }

    /**
     * 开始向流写入（不自动关流）。
     */
    fun start(os: OutputStream): Boolean {
        if (os == null) return false
        var ok = true
        closeStream = false
        out = os
        try {
            writeString("GIF89a") // header
        } catch (e: IOException) {
            ok = false
        }
        started = ok
        return started
    }

    /**
     * 便捷：一次性把若干帧编码为 GIF 字节。
     */
    fun encode(frames: List<IntArray>, w: Int, h: Int, delayMs: Int, repeat: Int = 0): ByteArray {
        val bos = ByteArrayOutputStream()
        start(bos)
        setDelay(delayMs)
        setRepeat(repeat)
        for (f in frames) addFramePixels(f, w, h)
        finish()
        return bos.toByteArray()
    }

    /**
     * Analyzes image colors and creates color map.
     */
    private fun analyzePixels(pixels: IntArray) {
        val nPix = pixels.size
        val len = nPix * 3
        indexedPixels = ByteArray(nPix)
        val nq = NeuQuant(pixels, len, sample)

        // initialize quantizer
        colorTab = nq.process() // create reduced palette

        for (i in usedEntry.indices) {
            usedEntry[i] = false
        }

        // map image pixels to new palette
        for (i in 0 until nPix) {
            val pixel = pixels[i]
            val index = nq.map(pixel and 0xff, (pixel shr 8) and 0xff, (pixel shr 16) and 0xff)
            usedEntry[index] = true
            indexedPixels!![i] = index.toByte()
        }
        colorDepth = 8
        palSize = 7

        var unusedIndex = -1
        for (i in usedEntry.indices) {
            if (!usedEntry[i]) {
                unusedIndex = i
                break
            }
        }

        val transIdx: Int
        // get closest match to transparent color if specified
        if (transparent != null) {
            transIndex = findClosest(transparent!!)
            transIdx = transIndex
        } else {
            if (unusedIndex != -1) {
                transIdx = unusedIndex
            } else {
                transIdx = findClosest(0xFF000000.toInt()) // black
            }

            var transp = false
            for (i in 0 until nPix) {
                val pixel = pixels[i]
                if (((pixel shr 24) and 0xff) < 0x80) {
                    transp = true
                    indexedPixels!![i] = transIdx.toByte()
                }
            }

            if (transp) {
                transIndex = transIdx
            }
        }
    }

    /**
     * Returns index of palette color closest to c (ARGB int)
     */
    private fun findClosest(c: Int): Int {
        val tab = colorTab ?: return -1
        val r = (c shr 16) and 0xff
        val g = (c shr 8) and 0xff
        val b = c and 0xff
        var minpos = 0
        var dmin = 256 * 256 * 256
        val len = tab.size
        var i = 0
        while (i < len) {
            val dr = r - (tab[i++].toInt() and 0xff)
            val dg = g - (tab[i++].toInt() and 0xff)
            val db = b - (tab[i].toInt() and 0xff)
            val d = dr * dr + dg * dg + db * db
            val index = i / 3
            if (usedEntry[index] && (d < dmin)) {
                dmin = d
                minpos = index
            }
            i++
        }
        return minpos
    }

    /**
     * Writes Graphic Control Extension
     */
    private fun writeGraphicCtrlExt() {
        out?.write(0x21) // extension introducer
        out?.write(0xf9) // GCE label
        out?.write(4)    // data block size
        var transp: Int
        var disp: Int
        if (transIndex == -1) {
            transp = 0
            disp = 0      // dispose = no action
        } else {
            transp = 1
            disp = 2      // force clear if using transparent color
        }
        if (dispose >= 0) {
            disp = dispose and 7 // user override
        }
        disp = disp shl 2

        // packed fields
        out?.write(0 or disp or 0 or transp)

        writeShort(delay) // delay x 1/100 sec
        out?.write(if (transIndex == -1) 0 else transIndex) // transparent color index
        out?.write(0) // block terminator
    }

    /**
     * Writes Image Descriptor
     */
    private fun writeImageDesc() {
        out?.write(0x2c) // image separator
        writeShort(0)    // image position x,y = 0,0
        writeShort(0)
        writeShort(width)  // image size
        writeShort(height)
        // packed fields
        if (firstFrame) {
            // no LCT - GCT is used for first (or only) frame
            out?.write(0)
        } else {
            // specify normal LCT
            out?.write(0x80 or 0 or 0 or 0 or palSize)
        }
    }

    /**
     * Writes Logical Screen Descriptor
     */
    private fun writeLSD() {
        // logical screen size
        writeShort(width)
        writeShort(height)
        // packed fields
        out?.write((0x80 or // 1 : global color table flag = 1 (gct used)
                0x70 or // 2-4 : color resolution = 7
                0x00 or // 5 : gct sort flag = 0
                palSize)) // 6-8 : gct size

        out?.write(0) // background color index
        out?.write(0) // pixel aspect ratio - assume 1:1
    }

    /**
     * Writes Netscape application extension to define repeat count.
     */
    private fun writeNetscapeExt() {
        out?.write(0x21)  // extension introducer
        out?.write(0xff)  // app extension label
        out?.write(11)    // block size
        writeString("NETSCAPE" + "2.0") // app id + auth code
        out?.write(3)     // sub-block size
        out?.write(1)     // loop sub-block id
        writeShort(repeat) // loop count (extra iterations, 0=repeat forever)
        out?.write(0)     // block terminator
    }

    /**
     * Writes color table
     */
    private fun writePalette() {
        val tab = colorTab ?: return
        out?.write(tab, 0, tab.size)
        val n = (3 * 256) - tab.size
        for (i in 0 until n) {
            out?.write(0)
        }
    }

    /**
     * Encodes and writes pixel data
     */
    private fun writePixels() {
        val encoder = LZWEncoder(width, height, indexedPixels!!, colorDepth)
        encoder.encode(out!!)
    }

    /**
     * Write 16-bit value to output stream, LSB first
     */
    private fun writeShort(value: Int) {
        out?.write(value and 0xff)
        out?.write((value shr 8) and 0xff)
    }

    /**
     * Writes string to output stream
     */
    private fun writeString(s: String) {
        for (i in s.indices) {
            out?.write(s[i].code)
        }
    }
}
