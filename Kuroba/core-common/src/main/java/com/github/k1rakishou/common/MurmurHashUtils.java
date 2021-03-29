package com.github.k1rakishou.common;

/**
 *  The MurmurHash3 algorithm was created by Austin Appleby and placed in the public domain.
 *  This java port was authored by Yonik Seeley and also placed into the public domain.
 *  The author hereby disclaims copyright to this source code.
 *  <p>
 *  This produces exactly the same hash values as the final C++
 *  version of MurmurHash3 and is thus suitable for producing the same hash values across
 *  platforms.
 *  <p>
 *  The 32 bit x86 version of this hash should be the fastest variant for relatively short keys like ids.
 *  murmurhash3_x64_128 is a good choice for longer strings or if you need more than 32 bits of hash.
 *  <p>
 *  Note - The x86 and x64 versions do _not_ produce the same results, as the
 *  algorithms are optimized for their respective platforms.
 *  <p>
 *  See http://github.com/yonik/java_util for future updates to this file.
 *
 *  Taken from https://github.com/yonik/java_util/blob/master/src/util/hash/MurmurHash3.java
 */

import android.text.Spannable;
import android.text.style.CharacterStyle;

import kotlin.text.Charsets;

public class MurmurHashUtils {
    private static final int SEED = 1477434883;

    public static int fmix32(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    public static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

    /** Gets a long from a byte buffer in little endian byte order. */
    public static long getLongLittleEndian(byte[] buf, int offset) {
        return     ((long)buf[offset+7]    << 56)   // no mask needed
                | ((buf[offset+6] & 0xffL) << 48)
                | ((buf[offset+5] & 0xffL) << 40)
                | ((buf[offset+4] & 0xffL) << 32)
                | ((buf[offset+3] & 0xffL) << 24)
                | ((buf[offset+2] & 0xffL) << 16)
                | ((buf[offset+1] & 0xffL) << 8)
                | ((buf[offset  ] & 0xffL));        // no shift needed
    }

    public static Murmur3Hash murmurhash3_x64_128(Spannable input) {
        StringBuilder stringBuilder = new StringBuilder(input.length());
        stringBuilder.append(input.toString());

        CharacterStyle[] spans = input.getSpans(0, input.length(), CharacterStyle.class);

        for (CharacterStyle span : spans) {
            stringBuilder
                    .append("{")
                    .append(input.getSpanStart(span))
                    .append(",")
                    .append(input.getSpanEnd(span))
                    .append(",")
                    .append(input.getSpanFlags(span))
                    .append(",")
                    .append(span.getClass().getSimpleName())
                    .append("}");
        }

        return murmurhash3_x64_128(stringBuilder.toString());
    }

    public static Murmur3Hash murmurhash3_x64_128(CharSequence input) {
        return murmurhash3_x64_128(input.toString());
    }

    public static Murmur3Hash murmurhash3_x64_128(String input) {
        return murmurhash3_x64_128(input.getBytes(Charsets.UTF_8), 0, input.length(), SEED);
    }

    /** Returns the MurmurHash3_x64_128 hash, placing the result in "out". */
    @SuppressWarnings("fallthrough")
    private static Murmur3Hash murmurhash3_x64_128(byte[] key, int offset, int len, int seed) {
        // The original algorithm does have a 32 bit unsigned seed.
        // We have to mask to match the behavior of the unsigned types and prevent sign extension.
        long h1 = seed & 0x00000000FFFFFFFFL;
        long h2 = seed & 0x00000000FFFFFFFFL;

        final long c1 = 0x87c37b91114253d5L;
        final long c2 = 0x4cf5ad432745937fL;

        int roundedEnd = offset + (len & 0xFFFFFFF0);  // round down to 16 byte block
        for (int i=offset; i<roundedEnd; i+=16) {
            long k1 = getLongLittleEndian(key, i);
            long k2 = getLongLittleEndian(key, i+8);
            k1 *= c1; k1  = Long.rotateLeft(k1,31); k1 *= c2; h1 ^= k1;
            h1 = Long.rotateLeft(h1,27); h1 += h2; h1 = h1*5+0x52dce729;
            k2 *= c2; k2  = Long.rotateLeft(k2,33); k2 *= c1; h2 ^= k2;
            h2 = Long.rotateLeft(h2,31); h2 += h1; h2 = h2*5+0x38495ab5;
        }

        long k1 = 0;
        long k2 = 0;

        switch (len & 15) {
            case 15: k2  = (key[roundedEnd+14] & 0xffL) << 48;
            case 14: k2 |= (key[roundedEnd+13] & 0xffL) << 40;
            case 13: k2 |= (key[roundedEnd+12] & 0xffL) << 32;
            case 12: k2 |= (key[roundedEnd+11] & 0xffL) << 24;
            case 11: k2 |= (key[roundedEnd+10] & 0xffL) << 16;
            case 10: k2 |= (key[roundedEnd+ 9] & 0xffL) << 8;
            case  9: k2 |= (key[roundedEnd+ 8] & 0xffL);
                k2 *= c2; k2  = Long.rotateLeft(k2, 33); k2 *= c1; h2 ^= k2;
            case  8: k1  = ((long)key[roundedEnd+7]) << 56;
            case  7: k1 |= (key[roundedEnd+6] & 0xffL) << 48;
            case  6: k1 |= (key[roundedEnd+5] & 0xffL) << 40;
            case  5: k1 |= (key[roundedEnd+4] & 0xffL) << 32;
            case  4: k1 |= (key[roundedEnd+3] & 0xffL) << 24;
            case  3: k1 |= (key[roundedEnd+2] & 0xffL) << 16;
            case  2: k1 |= (key[roundedEnd+1] & 0xffL) << 8;
            case  1: k1 |= (key[roundedEnd  ] & 0xffL);
                k1 *= c1; k1  = Long.rotateLeft(k1,31); k1 *= c2; h1 ^= k1;
        }

        //----------
        // finalization

        h1 ^= len; h2 ^= len;

        h1 += h2;
        h2 += h1;

        h1 = fmix64(h1);
        h2 = fmix64(h2);

        h1 += h2;
        h2 += h1;

        return new Murmur3Hash(h1, h2);
    }

    /** 128 bits of state */
    public static final class Murmur3Hash {
        public static final Murmur3Hash EMPTY = new Murmur3Hash(0L, 0L);

        public long val1;
        public long val2;

        public Murmur3Hash(long val1, long val2) {
            this.val1 = val1;
            this.val2 = val2;
        }

        public Murmur3Hash combine(Murmur3Hash other) {
            return new Murmur3Hash(val1 ^ other.val1, val2 ^ other.val2);
        }

        public Murmur3Hash copy() {
            return new Murmur3Hash(val1, val2);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Murmur3Hash murMur3Hash = (Murmur3Hash) o;

            if (val1 != murMur3Hash.val1) return false;
            return val2 == murMur3Hash.val2;
        }

        @Override
        public int hashCode() {
            int result = (int) (val1 ^ (val1 >>> 32));
            result = 31 * result + (int) (val2 ^ (val2 >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "Murmur3Hash{" +
                    "val1=" + val1 +
                    ", val2=" + val2 +
                    '}';
        }
    }

}
