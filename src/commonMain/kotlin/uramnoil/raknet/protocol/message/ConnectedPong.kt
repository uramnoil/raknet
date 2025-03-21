package uramnoil.raknet.protocol.message

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import uramnoil.raknet.protocol.Protocol

/**
 * ConnectedPongメッセージ
 * ConnectedPingへの応答
 */
class ConnectedPong : Message {
    var pingTimestamp: Long = 0
    var pongTimestamp: Long = 0
    
    override fun getID(): Byte = Protocol.MessageID.CONNECTED_PONG
    
    override fun encode(): Source = buildPacket {
        writeByte(getID())
        writeLong(pingTimestamp)
        writeLong(pongTimestamp)
    }
    
    override fun decode(packet: Source): Boolean {
        try {
            pingTimestamp = packet.readLong()
            pongTimestamp = packet.readLong()
            return true
        } catch (e: Exception) {
            return false
        }
    }
} 