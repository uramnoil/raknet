package uramnoil.raknet.protocol.message

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import uramnoil.raknet.protocol.Protocol
import uramnoil.raknet.util.writeBytes

/**
 * OpenConnectionRequest1メッセージ
 * 接続の最初のステップで、MTU検出に使用される
 */
class OpenConnectionRequest1 : Message {
    var protocol: Byte = Protocol.PROTOCOL_VERSION
    var mtu: UShort = Protocol.MIN_MTU_SIZE
    
    override fun getID(): Byte = Protocol.MessageID.OPEN_CONNECTION_REQUEST_1
    
    override fun encode(): Source = buildPacket {
        writeByte(getID())
        writeBytes(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)
        writeByte(protocol)
        
        // パディングを追加してMTUサイズにする
        val padding = ByteArray((mtu.toInt() - 20 - 8 - 1 - 16 - 1).coerceAtLeast(0))
        writeBytes(padding)
    }
    
    override fun decode(packet: Source): Boolean {
        try {
            // マジックシーケンスを検証
            val magic = ByteArray(16)
            packet.readFully(magic, 0, 16)
            if (!magic.contentEquals(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)) {
                return false
            }
            
            protocol = packet.readByte()
            
            // MTUサイズを計算
            mtu = (packet.remaining + 20 + 8 + 1 + 16 + 1).toUShort()
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
} 