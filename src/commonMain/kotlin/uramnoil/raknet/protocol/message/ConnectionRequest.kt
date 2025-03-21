package uramnoil.raknet.protocol.message

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import uramnoil.raknet.protocol.Protocol

/**
 * ConnectionRequestメッセージ
 * 接続の3番目のステップ
 */
class ConnectionRequest : Message {
    var clientGUID: Long = 0
    var timestamp: Long = 0
    var useSecurity: Boolean = false
    
    override fun getID(): Byte = Protocol.MessageID.CONNECTION_REQUEST
    
    override fun encode(): Source = buildPacket {
        writeByte(getID())
        writeLong(clientGUID)
        writeLong(timestamp)
        writeByte(if (useSecurity) 1 else 0)
    }
    
    override fun decode(packet: Source): Boolean {
        try {
            clientGUID = packet.readLong()
            timestamp = packet.readLong()
            useSecurity = packet.readByte() != 0.toByte()
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
} 