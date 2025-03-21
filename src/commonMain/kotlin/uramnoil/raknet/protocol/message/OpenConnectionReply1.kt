package uramnoil.raknet.protocol.message

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import uramnoil.raknet.protocol.Protocol
import uramnoil.raknet.util.writeBytes

/**
 * OpenConnectionReply1メッセージ
 * OpenConnectionRequest1への応答
 */
class OpenConnectionReply1 : Message {
    var serverGUID: Long = 0
    var serverHasSecurity: Boolean = false
    var mtu: UShort = Protocol.MIN_MTU_SIZE
    
    override fun getID(): Byte = Protocol.MessageID.OPEN_CONNECTION_REPLY_1
    
    override fun encode(): Source = buildPacket {
        writeByte(getID())
        writeBytes(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)
        writeLong(serverGUID)
        writeByte(if (serverHasSecurity) 1 else 0)
        writeShort(mtu.toShort())
    }
    
    override fun decode(packet: Source): Boolean {
        try {
            // マジックシーケンスを検証
            val magic = ByteArray(16)
            packet.readFully(magic, 0, 16)
            if (!magic.contentEquals(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)) {
                return false
            }
            
            serverGUID = packet.readLong()
            serverHasSecurity = packet.readByte() != 0.toByte()
            mtu = packet.readShort().toUShort()
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
} 