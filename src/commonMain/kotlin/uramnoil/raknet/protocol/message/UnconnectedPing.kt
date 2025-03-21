package uramnoil.raknet.protocol.message

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import uramnoil.raknet.protocol.Protocol
import uramnoil.raknet.util.writeBytes

class UnconnectedPing : Message {
    var pingTime: Long = 0
    var clientGUID: Long = 0
    
    override fun getID(): Byte = Protocol.MessageID.UNCONNECTED_PING
    
    override fun encode(): Source = buildPacket {
        writeByte(getID())
        writeLong(pingTime)
        writeLong(clientGUID)
        writeBytes(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)
    }
    
    override fun decode(packet: Source): Boolean {
        try {
            // IDは既に読み取られていると仮定
            pingTime = packet.readLong()
            clientGUID = packet.readLong()
            
            // マジックシーケンスを検証
            val magic = ByteArray(16)
            packet.readFully(magic, 0, 16)
            return magic.contentEquals(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)
        } catch (e: Exception) {
            return false
        }
    }
} 