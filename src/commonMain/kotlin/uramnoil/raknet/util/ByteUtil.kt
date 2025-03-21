package uramnoil.raknet.util

/**
 * バイト配列操作のユーティリティ
 */
object ByteUtil {
    /**
     * バイト配列からLong値を読み取る
     */
    fun bytesToLong(bytes: ByteArray, offset: Int): Long {
        var result: Long = 0
        for (i in 0..7) {
            result = result or ((bytes[offset + i].toLong() and 0xFF) shl (8 * (7 - i)))
        }
        return result
    }
    
    /**
     * バイト配列からUInt24値を読み取る
     */
    fun bytesToUInt24(bytes: ByteArray, offset: Int): UInt {
        return (((bytes[offset].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                (bytes[offset + 2].toInt() and 0xFF)).toUInt()
    }
    
    /**
     * Long値をバイト配列に変換
     */
    fun longToBytes(value: Long): ByteArray {
        val result = ByteArray(8)
        for (i in 0..7) {
            result[i] = ((value shr (8 * (7 - i))) and 0xFF).toByte()
        }
        return result
    }
    
    /**
     * UInt24値をバイト配列に変換
     */
    fun uInt24ToBytes(value: UInt): ByteArray {
        val result = ByteArray(3)
        result[0] = ((value.toInt() shr 16) and 0xFF).toByte()
        result[1] = ((value.toInt() shr 8) and 0xFF).toByte()
        result[2] = (value.toInt() and 0xFF).toByte()
        return result
    }
} 