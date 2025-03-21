package uramnoil.raknet.protocol.message

import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.io.Source
import uramnoil.raknet.protocol.Protocol
import uramnoil.raknet.util.AddressUtil
import uramnoil.raknet.util.writeBytes

/**
 * OpenConnectionReply2メッセージ
 * OpenConnectionRequest2への応答
 */
class OpenConnectionReply2 : Message {
    var serverGUID: Long = 0
    var clientAddress: SocketAddress? = null
    var mtu: UShort = Protocol.MIN_MTU_SIZE
    var encryptionEnabled: Boolean = false
    
    override fun getID(): Byte = Protocol.MessageID.OPEN_CONNECTION_REPLY_2
    
    override fun encode(): Source = buildPacket {
        writeByte(getID())
        writeBytes(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)
        writeLong(serverGUID)
        
        // クライアントアドレスを書き込む
        val addressBytes = AddressUtil.socketAddressToBytes(clientAddress)
        writeBytes(addressBytes)
        
        // MTUを書き込む
        writeShort(mtu.toShort())
        
        // 暗号化フラグを書き込む
        writeByte(if (encryptionEnabled) 1 else 0)
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
            
            // アドレスを読み取る
            clientAddress = AddressUtil.bytesToSocketAddress(packet)
            
            // MTUを読み取る
            mtu = packet.readShort().toUShort()
            
            // 暗号化フラグを読み取る
            encryptionEnabled = packet.readByte() != 0.toByte()
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
} 