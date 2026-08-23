package com.xiaochangan.petpetgif.gif

/*
 * NeuQuant Neural-Net Quantization Algorithm
 * ------------------------------------------
 * Copyright (c) 1994 Anthony Dekker
 * Ported to Java 12/00 K Weiner（FM Software），本文件为 Kotlin 忠实移植。
 * License: 见原版权声明（可自由使用，须保留版权声明）
 */

/** NeuQuant 神经网络颜色量化：把 ARGB int 像素集缩到 256 色调色板 */
class NeuQuant(thepic: IntArray, len: Int, sample: Int) {

    companion object {
        const val netsize = 256          /* number of colours used */
        /* four primes near 500 - assume no image has a length so large */
        /* that it is divisible by all four primes */
        const val prime1 = 499
        const val prime2 = 491
        const val prime3 = 487
        const val prime4 = 503
        const val minpicturebytes = (3 * prime4) /* minimum size for input image */

        /* Network Definitions */
        const val maxnetpos = (netsize - 1)
        const val netbiasshift = 4       /* bias for colour values */
        const val ncycles = 100          /* no. of learning cycles */

        /* defs for freq and bias */
        const val intbiasshift = 16      /* bias for fractions */
        const val intbias = (1 shl intbiasshift)
        const val gammashift = 10        /* gamma = 1024 */
        const val gamma = (1 shl gammashift)
        const val betashift = 10
        const val beta = (intbias shr betashift)      /* beta = 1/1024 */
        const val betagamma = (intbias shl (gammashift - betashift))

        /* defs for decreasing radius factor */
        const val initrad = (netsize shr 3)          /* for 256 cols, radius starts */
        const val radiusbiasshift = 6                /* at 32.0 biased by 6 bits */
        const val radiusbias = (1 shl radiusbiasshift)
        const val initradius = (initrad * radiusbias) /* and decreases by a */
        const val radiusdec = 30                     /* factor of 1/30 each cycle */

        /* defs for decreasing alpha factor */
        const val alphabiasshift = 10                /* alpha starts at 1.0 */
        const val initalpha = (1 shl alphabiasshift)

        /* radbias and alpharadbias used for radpower calculation */
        const val radbiasshift = 8
        const val radbias = (1 shl radbiasshift)
        const val alpharadbshift = (alphabiasshift + radbiasshift)
        const val alpharadbias = (1 shl alpharadbshift)
    }

    private val thepicture: IntArray = thepic   /* the input image itself */
    private var lengthcount = len               /* lengthcount = H*W*3 */
    private var samplefac = sample              /* sampling factor 1..30 */

    private val network = Array(netsize) { IntArray(4) }  /* the network itself - [netsize][4] */
    private val netindex = IntArray(256)       /* for network lookup - really 256 */
    private val bias = IntArray(netsize)       /* bias and freq arrays for learning */
    private val freq = IntArray(netsize)
    private val radpower = IntArray(initrad)   /* radpower for precomputation */

    /* Initialise network in range (0,0,0) to (255,255,255) and set parameters */
    init {
        for (i in 0 until netsize) {
            val p = network[i]
            p[0] = (i shl (netbiasshift + 8)) / netsize
            p[1] = p[0]
            p[2] = p[0]
            freq[i] = intbias / netsize        /* 1/netsize */
            bias[i] = 0
        }
    }

    fun colorMap(): ByteArray {
        val map = ByteArray(3 * netsize)
        val index = IntArray(netsize)
        for (i in 0 until netsize) {
            index[network[i][3]] = i
        }
        var k = 0
        for (i in 0 until netsize) {
            val j = index[i]
            // convert map from BGR to RGB
            map[k++] = network[j][2].toByte()
            map[k++] = network[j][1].toByte()
            map[k++] = network[j][0].toByte()
        }
        return map
    }

    /*
     * Insertion sort of network and building of netindex[0..255] (to do after unbias)
     */
    fun inxbuild() {
        var previouscol = 0
        var startpos = 0
        for (i in 0 until netsize) {
            val p = network[i]
            var smallpos = i
            var smallval = p[1]                    /* index on g */
            /* find smallest in i..netsize-1 */
            for (j in i + 1 until netsize) {
                val q = network[j]
                if (q[1] < smallval) {             /* index on g */
                    smallpos = j
                    smallval = q[1]                /* index on g */
                }
            }
            val q = network[smallpos]
            /* swap p (i) and q (smallpos) entries */
            if (i != smallpos) {
                var j = q[0]; q[0] = p[0]; p[0] = j
                j = q[1]; q[1] = p[1]; p[1] = j
                j = q[2]; q[2] = p[2]; p[2] = j
                j = q[3]; q[3] = p[3]; p[3] = j
            }
            /* smallval entry is now in position i */
            if (smallval != previouscol) {
                netindex[previouscol] = (startpos + i) shr 1
                for (j in previouscol + 1 until smallval) {
                    netindex[j] = i
                }
                previouscol = smallval
                startpos = i
            }
        }
        netindex[previouscol] = (startpos + maxnetpos) shr 1
        for (j in previouscol + 1 until 256) {
            netindex[j] = maxnetpos /* really 256 */
        }
    }

    /* Main Learning Loop */
    fun learn() {
        var radius: Int
        var rad: Int
        var alpha: Int
        var step: Int
        var delta: Int
        var samplepixels: Int
        var pix: Int
        var lim: Int

        if (lengthcount < minpicturebytes) {
            samplefac = 1
        }
        var alphadec = 30 + ((samplefac - 1) / 3)
        val p = thepicture
        pix = 0
        lim = lengthcount
        samplepixels = lengthcount / (3 * samplefac)
        delta = samplepixels / ncycles
        alpha = initalpha
        radius = initradius

        rad = radius shr radiusbiasshift
        if (rad <= 1) {
            rad = 0
        }
        for (i in 0 until rad) {
            radpower[i] = alpha * (((rad * rad - i * i) * radbias) / (rad * rad))
        }

        if (lengthcount < minpicturebytes) {
            step = 3
        } else if ((lengthcount % prime1) != 0) {
            step = 3 * prime1
        } else {
            if ((lengthcount % prime2) != 0) {
                step = 3 * prime2
            } else {
                if ((lengthcount % prime3) != 0) {
                    step = 3 * prime3
                } else {
                    step = 3 * prime4
                }
            }
        }

        var i = 0
        while (i < samplepixels) {
            val pixel = p[pix / 3]
            val b = (pixel and 0xff) shl netbiasshift
            val g = ((pixel shr 8) and 0xff) shl netbiasshift
            val r = ((pixel shr 16) and 0xff) shl netbiasshift
            val j = contest(b, g, r)

            altersingle(alpha, j, b, g, r)
            if (rad != 0) {
                alterneigh(rad, j, b, g, r) /* alter neighbours */
            }

            pix += step
            if (pix >= lim) {
                pix -= lengthcount
            }

            i++
            if (delta == 0) {
                delta = 1
            }
            if (i % delta == 0) {
                alpha -= alpha / alphadec
                radius -= radius / radiusdec
                rad = radius shr radiusbiasshift
                if (rad <= 1) {
                    rad = 0
                }
                for (j2 in 0 until rad) {
                    radpower[j2] = alpha * (((rad * rad - j2 * j2) * radbias) / (rad * rad))
                }
            }
        }
    }

    /*
     * Search for BGR values 0..255 (after net is unbiased) and return colour index
     */
    fun map(b: Int, g: Int, r: Int): Int {
        var bestd = 1000 /* biggest possible dist is 256*3 */
        var best = -1
        var i = netindex[g] /* index on g */
        var j = i - 1 /* start at netindex[g] and work outwards */

        while ((i < netsize) || (j >= 0)) {
            if (i < netsize) {
                val p = network[i]
                var dist = p[1] - g /* inx key */
                if (dist >= bestd) {
                    i = netsize /* stop iter */
                } else {
                    i++
                    if (dist < 0) {
                        dist = -dist
                    }
                    var a = p[0] - b
                    if (a < 0) {
                        a = -a
                    }
                    dist += a
                    if (dist < bestd) {
                        a = p[2] - r
                        if (a < 0) {
                            a = -a
                        }
                        dist += a
                        if (dist < bestd) {
                            bestd = dist
                            best = p[3]
                        }
                    }
                }
            }
            if (j >= 0) {
                val p = network[j]
                var dist = g - p[1] /* inx key - reverse dif */
                if (dist >= bestd) {
                    j = -1 /* stop iter */
                } else {
                    j--
                    if (dist < 0) {
                        dist = -dist
                    }
                    var a = p[0] - b
                    if (a < 0) {
                        a = -a
                    }
                    dist += a
                    if (dist < bestd) {
                        a = p[2] - r
                        if (a < 0) {
                            a = -a
                        }
                        dist += a
                        if (dist < bestd) {
                            bestd = dist
                            best = p[3]
                        }
                    }
                }
            }
        }
        return best
    }

    fun process(): ByteArray {
        learn()
        unbiasnet()
        inxbuild()
        return colorMap()
    }

    /*
     * Unbias network to give byte values 0..255 and record position i to prepare for sort
     */
    fun unbiasnet() {
        for (i in 0 until netsize) {
            network[i][0] = network[i][0] shr netbiasshift
            network[i][1] = network[i][1] shr netbiasshift
            network[i][2] = network[i][2] shr netbiasshift
            network[i][3] = i /* record colour no */
        }
    }

    /*
     * Move adjacent neurons by precomputed alpha*(1-((i-j)^2/[r]^2)) in radpower[|i-j|]
     */
    private fun alterneigh(rad: Int, i: Int, b: Int, g: Int, r: Int) {
        var lo = i - rad
        if (lo < -1) {
            lo = -1
        }
        var hi = i + rad
        if (hi > netsize) {
            hi = netsize
        }

        var j = i + 1
        var k = i - 1
        var m = 1
        while ((j < hi) || (k > lo)) {
            val a = radpower[m++]
            if (j < hi) {
                val p = network[j++]
                p[0] -= (a * (p[0] - b)) / alpharadbias
                p[1] -= (a * (p[1] - g)) / alpharadbias
                p[2] -= (a * (p[2] - r)) / alpharadbias
            }
            if (k > lo) {
                val p = network[k--]
                p[0] -= (a * (p[0] - b)) / alpharadbias
                p[1] -= (a * (p[1] - g)) / alpharadbias
                p[2] -= (a * (p[2] - r)) / alpharadbias
            }
        }
    }

    /*
     * Move neuron i towards biased (b,g,r) by factor alpha
     */
    private fun altersingle(alpha: Int, i: Int, b: Int, g: Int, r: Int) {
        /* alter hit neuron */
        val n = network[i]
        n[0] -= (alpha * (n[0] - b)) / initalpha
        n[1] -= (alpha * (n[1] - g)) / initalpha
        n[2] -= (alpha * (n[2] - r)) / initalpha
    }

    /*
     * Search for biased BGR values
     * finds closest neuron (min dist) and updates freq
     * finds best neuron (min dist-bias) and returns position
     * for frequently chosen neurons, freq[i] is high and bias[i] is negative
     * bias[i] = gamma*((1/netsize)-freq[i])
     */
    private fun contest(b: Int, g: Int, r: Int): Int {
        var bestd = Int.MIN_VALUE.inv()
        var bestbiasd = bestd
        var bestpos = -1
        var bestbiaspos = bestpos

        for (i in 0 until netsize) {
            val n = network[i]
            var dist = n[0] - b
            if (dist < 0) {
                dist = -dist
            }
            var a = n[1] - g
            if (a < 0) {
                a = -a
            }
            dist += a
            a = n[2] - r
            if (a < 0) {
                a = -a
            }
            dist += a
            if (dist < bestd) {
                bestd = dist
                bestpos = i
            }
            val biasdist = dist - ((bias[i]) shr (intbiasshift - netbiasshift))
            if (biasdist < bestbiasd) {
                bestbiasd = biasdist
                bestbiaspos = i
            }
            val betafreq = (freq[i] shr betashift)
            freq[i] -= betafreq
            bias[i] += (betafreq shl gammashift)
        }
        freq[bestpos] += beta
        bias[bestpos] -= betagamma
        return bestbiaspos
    }
}
