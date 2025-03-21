package uramnoil.raknet.protocol.message

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import uramnoil.raknet.protocol.Protocol
import uramnoil.raknet.util.writeBytes

/**
 * UnconnectedPongメッセージ
 * UnconnectedPingへの応答
 */
class UnconnectedPong : Message {
    var pingTime: Long = 0
    var serverGUID: Long = 0
    var data: ByteArray = ByteArray(0)
    
    override fun getID(): Byte = Protocol.MessageID.UNCONNECTED_PONG
    
    override fun encode(): Source = buildPacket {
        writeByte(getID())
        writeLong(pingTime)
        writeLong(serverGUID)
        writeBytes(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)
        writeShort(data.size.toShort())
        writeBytes(data)
    }
    
    override fun decode(packet: Source): Boolean {
        try {
            pingTime = packet.readLong()
            serverGUID = packet.readLong()
            
            // マジックシーケンスを検証
            val magic = ByteArray(16)
            packet.readFully(magic, 0, 16)
            if (!magic.contentEquals(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)) {
                return false
            }
            
            // データを読み取る
            val dataLength = packet.readShort().toInt()
            data = ByteArray(dataLength)
            packet.readFully(data, 0, dataLength)
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
} 