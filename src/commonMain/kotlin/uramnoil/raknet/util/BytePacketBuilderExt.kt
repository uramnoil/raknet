package uramnoil.raknet.util

import io.ktor.utils.io.core.*

/**
 * BytePacketBuilderにバイト配列を書き込む拡張関数
 */
fun BytePacketBuilder.writeBytes(bytes: ByteArray) {
    writeFully(bytes, 0, bytes.size)
}

/**
 * BytePacketBuilderにUInt24を書き込む拡張関数
 */
fun BytePacketBuilder.writeUInt24(value: UInt) {
    writeByte(((value.toInt() shr 16) and 0xFF).toByte())
    writeByte(((value.toInt() shr 8) and 0xFF).toByte())
    writeByte((value.toInt() and 0xFF).toByte())
}

/**
 * ByteReadPacketからUInt24を読み取る拡張関数
 */
fun ByteReadPacket.readUInt24(): UInt {
    val b1 = readByte().toInt() and 0xFF
    val b2 = readByte().toInt() and 0xFF
    val b3 = readByte().toInt() and 0xFF
    return ((b1 shl 16) or (b2 shl 8) or b3).toUInt()
} 