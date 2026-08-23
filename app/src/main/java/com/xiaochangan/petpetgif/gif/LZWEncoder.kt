package com.xiaochangan.petpetgif.gif

import java.io.IOException
import java.io.OutputStream

/*
 * LZWEncoder —— GIF 图像压缩（GIFCOMPR.C 移植链）：
 * 原作者：Spencer W. Thomas / Jim McKie / Steve Davies / Ken Turkowski /
 *         James A. Woods / Joe Orost；经 Jef Poskanzer Java 移植、
 *         J. M. G. Elliott、K Weiner 12/00 修改，本文件为 Kotlin 忠实移植。
 */

/** GIF LZW 编码器：把 8-bit 索引像素流压成 GIF LZW 码流 */
class LZWEncoder(private val imgW: Int, private val imgH: Int, private val pixAry: ByteArray, colorDepth: Int) {

    private val initCodeSize = Math.max(2, colorDepth)

    private var remaining = 0
    private var curPixel = 0

    companion object {
        private const val EOF = -1
        private const val BITS = 12
        private const val HSIZE = 5003 // 80% occupancy
    }

    private var n_bits = 0                    // number of bits/code
    private val maxbits = BITS                // user settable max # bits/code
    private var maxcode = 0                   // maximum code, given n_bits
    private val maxmaxcode = 1 shl BITS       // should NEVER generate this code
    private val htab = IntArray(HSIZE)
    private val codetab = IntArray(HSIZE)
    private val hsize = HSIZE                 // for dynamic table sizing
    private var free_ent = 0                  // first unused entry

    private var clear_flg = false

    private var g_init_bits = 0

    private var ClearCode = 0
    private var EOFCode = 0

    private var cur_accum = 0
    private var cur_bits = 0

    private val masks = intArrayOf(0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F, 0x007F, 0x00FF, 0x01FF,
        0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF)

    // Number of characters so far in this 'packet'
    private var a_count = 0

    // Define the storage for the packet accumulator
    private val accum = ByteArray(256)

    // Add a character to the end of the current packet, and if it is 254
    // characters, flush the packet to disk.
    private fun char_out(c: Byte, outs: OutputStream) {
        accum[a_count++] = c
        if (a_count >= 254) {
            flush_char(outs)
        }
    }

    // Clear out the hash table - table clear for block compress
    private fun cl_block(outs: OutputStream) {
        cl_hash(hsize)
        free_ent = ClearCode + 2
        clear_flg = true
        output(ClearCode, outs)
    }

    // reset code table
    private fun cl_hash(hsize: Int) {
        for (i in 0 until hsize) {
            htab[i] = -1
        }
    }

    private fun compress(init_bits: Int, outs: OutputStream) {
        var fcode: Int
        var c: Int
        var ent: Int
        var disp: Int
        var hshift: Int

        // Set up the globals: g_init_bits - initial number of bits
        g_init_bits = init_bits

        // Set up the necessary values
        clear_flg = false
        n_bits = g_init_bits
        maxcode = MAXCODE(n_bits)

        ClearCode = 1 shl (init_bits - 1)
        EOFCode = ClearCode + 1
        free_ent = ClearCode + 2

        a_count = 0 // clear packet

        ent = nextPixel()

        hshift = 0
        fcode = hsize
        while (fcode < 65536) {
            ++hshift
            fcode *= 2
        }
        hshift = 8 - hshift // set hash code range bound

        val hsize_reg = hsize
        cl_hash(hsize_reg) // clear hash table

        output(ClearCode, outs)

        outer_loop@ while ((nextPixel().also { c = it }) != EOF) {
            fcode = (c shl maxbits) + ent
            var i = (c shl hshift) xor ent // xor hashing

            if (htab[i] == fcode) {
                ent = codetab[i]
                continue
            } else if (htab[i] >= 0) { // non-empty slot
                disp = hsize_reg - i // secondary hash (after G. Knott)
                if (i == 0) {
                    disp = 1
                }
                do {
                    i -= disp
                    if (i < 0) {
                        i += hsize_reg
                    }

                    if (htab[i] == fcode) {
                        ent = codetab[i]
                        continue@outer_loop
                    }
                } while (htab[i] >= 0)
            }
            output(ent, outs)
            ent = c
            if (free_ent < maxmaxcode) {
                codetab[i] = free_ent++ // code -> hashtable
                htab[i] = fcode
            } else {
                cl_block(outs)
            }
        }
        // Put out the final code.
        output(ent, outs)
        output(EOFCode, outs)
    }

    fun encode(os: OutputStream) {
        os.write(initCodeSize) // write "initial code size" byte

        remaining = imgW * imgH // reset navigation variables
        curPixel = 0

        compress(initCodeSize + 1, os) // compress and write the pixel data

        os.write(0) // write block terminator
    }

    // Flush the packet to disk, and reset the accumulator
    private fun flush_char(outs: OutputStream) {
        if (a_count > 0) {
            outs.write(a_count)
            outs.write(accum, 0, a_count)
            a_count = 0
        }
    }

    private fun MAXCODE(n_bits: Int): Int {
        return (1 shl n_bits) - 1
    }

    // Return the next pixel from the image
    private fun nextPixel(): Int {
        if (remaining == 0) {
            return EOF
        }

        --remaining

        val pix = pixAry[curPixel++]

        return pix.toInt() and 0xff
    }

    private fun output(code: Int, outs: OutputStream) {
        cur_accum = cur_accum and masks[cur_bits]

        if (cur_bits > 0) {
            cur_accum = cur_accum or (code shl cur_bits)
        } else {
            cur_accum = code
        }

        cur_bits += n_bits

        while (cur_bits >= 8) {
            char_out((cur_accum and 0xff).toByte(), outs)
            cur_accum = cur_accum ushr 8
            cur_bits -= 8
        }

        // If the next entry is going to be too big for the code size,
        // then increase it, if possible.
        if (free_ent > maxcode || clear_flg) {
            if (clear_flg) {
                maxcode = MAXCODE(g_init_bits.also { n_bits = it })
                clear_flg = false
            } else {
                ++n_bits
                if (n_bits == maxbits) {
                    maxcode = maxmaxcode
                } else {
                    maxcode = MAXCODE(n_bits)
                }
            }
        }

        if (code == EOFCode) {
            // At EOF, write the rest of the buffer.
            while (cur_bits > 0) {
                char_out((cur_accum and 0xff).toByte(), outs)
                cur_accum = cur_accum ushr 8
                cur_bits -= 8
            }

            flush_char(outs)
        }
    }
}
