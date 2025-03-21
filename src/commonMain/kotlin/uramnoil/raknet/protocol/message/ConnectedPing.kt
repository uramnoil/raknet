package uramnoil.raknet.protocol.message

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import uramnoil.raknet.protocol.Protocol

/**
 * ConnectedPingメッセージ
 * 接続後の接続維持に使用される
 */
class ConnectedPing : Message {
    var timestamp: Long = 0
    
    override fun getID(): Byte = Protocol.MessageID.CONNECTED_PING
    
    override fun encode(): Source = buildPacket {
        writeByte(getID())
        writeLong(timestamp)
    }
    
    override fun decode(packet: Source): Boolean {
        try {
            timestamp = packet.readLong()
            return true
        } catch (e: Exception) {
            return false
        }
    }
} 