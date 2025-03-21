package uramnoil.raknet.protocol.message

import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.io.Source
import uramnoil.raknet.protocol.Protocol
import uramnoil.raknet.util.AddressUtil
import uramnoil.raknet.util.writeBytes

/**
 * OpenConnectionRequest2メッセージ
 * 接続の2番目のステップ
 */
class OpenConnectionRequest2 : Message {
    var serverAddress: SocketAddress? = null
    var mtu: UShort = Protocol.MIN_MTU_SIZE
    var clientGUID: Long = 0
    
    override fun getID(): Byte = Protocol.MessageID.OPEN_CONNECTION_REQUEST_2
    
    override fun encode(): Source = buildPacket {
        writeByte(getID())
        writeBytes(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)
        
        // サーバーアドレスを書き込む
        val addressBytes = AddressUtil.socketAddressToBytes(serverAddress)
        writeBytes(addressBytes)
        
        // MTUを書き込む
        writeShort(mtu.toShort())
        
        // クライアントGUIDを書き込む
        writeLong(clientGUID)
    }
    
    override fun decode(packet: Source): Boolean {
        try {
            // マジックシーケンスを検証
            val magic = ByteArray(16)
            packet.readFully(magic, 0, 16)
            if (!magic.contentEquals(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)) {
                return false
            }
            
            // アドレスを読み取る
            serverAddress = AddressUtil.bytesToSocketAddress(packet)
            
            // MTUを読み取る
            mtu = packet.readShort().toUShort()
            
            // クライアントGUIDを読み取る
            clientGUID = packet.readLong()
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
} 