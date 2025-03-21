package uramnoil.raknet.protocol.message

import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.io.Source
import uramnoil.raknet.protocol.Protocol
import uramnoil.raknet.util.AddressUtil
import uramnoil.raknet.util.writeBytes

/**
 * ConnectionRequestAcceptedメッセージ
 * ConnectionRequestへの応答
 */
class ConnectionRequestAccepted : Message {
    var clientAddress: SocketAddress? = null
    var systemIndex: Short = 0
    var systemAddresses: Array<SocketAddress?> = Array(20) { null }
    var incomingTimestamp: Long = 0
    var serverTimestamp: Long = 0
    
    override fun getID(): Byte = Protocol.MessageID.CONNECTION_REQUEST_ACCEPTED
    
    override fun encode(): Source = buildPacket {
        writeByte(getID())
        
        // クライアントアドレスを書き込む
        val addressBytes = AddressUtil.socketAddressToBytes(clientAddress)
        writeBytes(addressBytes)
        
        // システムインデックスを書き込む
        writeShort(systemIndex)
        
        // システムアドレスを書き込む
        for (address in systemAddresses) {
            val bytes = AddressUtil.socketAddressToBytes(address)
            writeBytes(bytes)
        }
        
        // タイムスタンプを書き込む
        writeLong(incomingTimestamp)
        writeLong(serverTimestamp)
    }
    
    override fun decode(packet: Source): Boolean {
        try {
            // クライアントアドレスを読み取る
            clientAddress = AddressUtil.bytesToSocketAddress(packet)
            
            // システムインデックスを読み取る
            systemIndex = packet.readShort()
            
            // システムアドレスを読み取る
            for (i in systemAddresses.indices) {
                systemAddresses[i] = AddressUtil.bytesToSocketAddress(packet)
            }
            
            // タイムスタンプを読み取る
            incomingTimestamp = packet.readLong()
            serverTimestamp = packet.readLong()
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
} 