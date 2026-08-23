package com.xiaochangan.petpetgif.gif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * GIF 编码器往返验证：生成确定性测试图（渐变+色块+透明区），
 * 编码为 GIF 写盘，再由 Python PIL 解码逐帧对比像素（见 tools/verify_gif.py）。
 */
class AnimatedGifEncoderTest {

    @Test
    fun roundTripGif() {
        val w = 112
        val h = 112
        val frames = mutableListOf<IntArray>()

        repeat(3) { f ->
            val px = IntArray(w * h)
            var seed = 42 + f * 1000
            for (y in 0 until h) {
                for (x in 0 until w) {
                    // 左上角透明区（应被编码器识别为透明）
                    if (x < 30 && y < 30) {
                        px[y * w + x] = 0x00FFFFFF // 全透明
                        continue
                    }
                    // 渐变底 + 移动色块 + 伪随机噪点
                    val r = (x * 2 + y) and 0xff
                    val g = (y * 2) and 0xff
                    val b = (255 - x) and 0xff
                    // 移动方块（帧 f 位置不同）
                    val bx = (10 + f * 30 + x / 8 * 0) % 112
                    val block = (x in bx..(bx + 20)) && (y in 40..60)
                    // 伪随机
                    seed = seed * 1103515245 + 12345
                    val noise = (seed ushr 16) and 0x0f
                    val cr = if (block) 255 else (r + noise) and 0xff
                    val cg = if (block) 0 else (g + noise) and 0xff
                    val cb = if (block) 0 else (b + noise) and 0xff
                    px[y * w + x] = (0xFF shl 24) or (cr shl 16) or (cg shl 8) or cb
                }
            }
            frames.add(px)
        }

        val enc = AnimatedGifEncoder()
        val bytes = enc.encode(frames, w, h, delayMs = 60, repeat = 0)

        // GIF89a 头 + trailer
        assertEquals('G'.code, bytes[0].toInt() and 0xff)
        assertEquals('I'.code, bytes[1].toInt() and 0xff)
        assertEquals('F'.code, bytes[2].toInt() and 0xff)
        assertEquals('8'.code, bytes[3].toInt() and 0xff)
        assertEquals('9'.code, bytes[4].toInt() and 0xff)
        assertEquals('a'.code, bytes[5].toInt() and 0xff)
        assertEquals(0x3B, bytes[bytes.size - 1].toInt() and 0xff)
        assertTrue("gif 太小: ${bytes.size}", bytes.size > 2000)

        // 写盘供 PIL 验证
        val outFile = File("build/test-output/roundtrip.gif")
        outFile.parentFile?.mkdirs()
        outFile.writeBytes(bytes)
        println("GIF written: ${outFile.absolutePath} (${bytes.size} bytes)")
    }

    @Test
    fun singleFrameGif() {
        val w = 112
        val h = 112
        val px = IntArray(w * h)
        // 纯色图（最坏情况：全部同色 → LZW 压缩率极高）
        for (i in px.indices) px[i] = (0xFF shl 24) or (0x38 shl 16) or (0xBD shl 8) or 0xF8

        val enc = AnimatedGifEncoder()
        val bytes = enc.encode(listOf(px), w, h, delayMs = 100)
        assertTrue("单色图应压缩得很小: ${bytes.size}", bytes.size < 1200)

        val outFile = File("build/test-output/single.gif")
        outFile.parentFile.mkdirs()
        outFile.writeBytes(bytes)
        println("Single-frame GIF written: ${outFile.absolutePath} (${bytes.size} bytes)")
    }
}
