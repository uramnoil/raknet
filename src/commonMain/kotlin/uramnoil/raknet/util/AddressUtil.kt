package uramnoil.raknet.util

import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.io.Source

/**
 * ソケットアドレス操作のユーティリティ
 */
object AddressUtil {
    /**
     * SocketAddressをバイト配列に変換
     */
    fun socketAddressToBytes(address: SocketAddress?): ByteArray {
        if (address == null) {
            // NULLアドレス
            return byteArrayOf(0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        }
        
        when (address) {
            is InetSocketAddress -> {
                val inetAddress = address.hostname
                val port = address.port
                
                // IPv4アドレスの場合
                if (inetAddress.contains('.')) {
                    val parts = inetAddress.split('.')
                    if (parts.size == 4) {
                        val result = ByteArray(7)
                        result[0] = 0x04 // IPv4
                        
                        // IPアドレスを書き込む
                        for (i in 0..3) {
                            result[i + 1] = parts[i].toInt().toByte()
                        }
                        
                        // ポートを書き込む
                        result[5] = ((port shr 8) and 0xFF).toByte()
                        result[6] = (port and 0xFF).toByte()
                        
                        return result
                    }
                }
                
                // IPv6アドレスの場合（簡略化）
                return byteArrayOf(0x06) + ByteArray(19) { 0 } // 実際のIPv6実装は複雑なため省略
            }
            else -> {
                // 不明なアドレスタイプ
                return byteArrayOf(0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
            }
        }
    }
    
    /**
     * バイト配列からSocketAddressを読み取る
     */
    fun bytesToSocketAddress(packet: Source): SocketAddress? {
        val addressType = packet.readByte().toInt() and 0xFF
        
        when (addressType) {
            4 -> { // IPv4
                val ip1 = packet.readByte().toInt() and 0xFF
                val ip2 = packet.readByte().toInt() and 0xFF
                val ip3 = packet.readByte().toInt() and 0xFF
                val ip4 = packet.readByte().toInt() and 0xFF
                
                val port = (packet.readByte().toInt() and 0xFF) shl 8 or (packet.readByte().toInt() and 0xFF)
                
                val ipAddress = "$ip1.$ip2.$ip3.$ip4"
                return InetSocketAddress(ipAddress, port)
            }
            6 -> { // IPv6（簡略化）
                // 19バイトスキップ（実際のIPv6実装は複雑なため省略）
                packet.discard(19)
                return null
            }
            else -> {
                return null
            }
        }
    }
} 