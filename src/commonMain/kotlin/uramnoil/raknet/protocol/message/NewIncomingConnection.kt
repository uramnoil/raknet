package uramnoil.raknet.protocol.message

import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.io.Source
import uramnoil.raknet.protocol.Protocol
import uramnoil.raknet.util.AddressUtil
import uramnoil.raknet.util.writeBytes

/**
 * NewIncomingConnectionメッセージ
 * 接続の最終ステップ
 */
class NewIncomingConnection : Message {
    var serverAddress: SocketAddress? = null
    var systemAddresses: Array<SocketAddress?> = Array(20) { null }
    var timestamp: Long = 0
    var serverTimestamp: Long = 0
    
    override fun getID(): Byte = Protocol.MessageID.NEW_INCOMING_CONNECTION
    
    override fun encode(): Source = buildPacket {
        writeByte(getID())
        
        // サーバーアドレスを書き込む
        val addressBytes = AddressUtil.socketAddressToBytes(serverAddress)
        writeBytes(addressBytes)
        
        // システムアドレスを書き込む
        for (address in systemAddresses) {
            val bytes = AddressUtil.socketAddressToBytes(address)
            writeBytes(bytes)
        }
        
        // タイムスタンプを書き込む
        writeLong(timestamp)
        writeLong(serverTimestamp)
    }
    
    override fun decode(packet: Source): Boolean {
        try {
            // サーバーアドレスを読み取る
            serverAddress = AddressUtil.bytesToSocketAddress(packet)
            
            // システムアドレスを読み取る
            for (i in systemAddresses.indices) {
                systemAddresses[i] = AddressUtil.bytesToSocketAddress(packet)
            }
            
            // タイムスタンプを読み取る
            timestamp = packet.readLong()
            serverTimestamp = packet.readLong()
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
} 