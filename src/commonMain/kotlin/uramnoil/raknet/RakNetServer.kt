package uramnoil.raknet

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.io.Source
import kotlinx.coroutines.*
import uramnoil.raknet.protocol.Protocol
import uramnoil.raknet.util.writeBytes
import kotlin.random.Random

class RakNetServer(
    private val port: Int,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private var socket: BoundDatagramSocket? = null
    private var isRunning = false
    private val serverGUID = Random.nextLong()
    private var pongData: ByteArray = ByteArray(0)
    
    /**
     * サーバーを起動します
     */
    suspend fun start() {
        if (isRunning) return
        
        val selectorManager = SelectorManager()
        socket = aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", port))
        isRunning = true
        
        coroutineScope.launch {
            try {
                while (isRunning) {
                    val datagram = socket?.receive() ?: break
                    processPacket(datagram)
                }
            } catch (e: Exception) {
                // エラー処理
            } finally {
                stop()
            }
        }
    }
    
    /**
     * サーバーを停止します
     */
    fun stop() {
        isRunning = false
        socket?.close()
        socket = null
    }
    
    /**
     * Pingレスポンスで返すデータを設定します
     */
    fun setPongData(data: ByteArray) {
        pongData = data
    }
    
    /**
     * 受信したパケットを処理します
     */
    private suspend fun processPacket(datagram: Datagram) {
        val packet = datagram.packet
        if (packet.exhausted()) return
        
        val packetId = packet.readByte()
        
        when (packetId) {
            Protocol.MessageID.UNCONNECTED_PING -> {
                handleUnconnectedPing(datagram)
            }
            Protocol.MessageID.OPEN_CONNECTION_REQUEST_1 -> {
                // OpenConnectionRequest1の処理
            }
            // 他のパケットタイプの処理
        }
    }
    
    /**
     * UnconnectedPingパケットを処理します
     */
    private suspend fun handleUnconnectedPing(datagram: Datagram) {
        val packet = datagram.packet
        
        try {
            // pingTimeを読み取る
            val pingTime = packet.readLong()
            
            // clientGUIDを読み取る
            val clientGUID = packet.readLong()
            
            // マジックを検証
            val magic = ByteArray(16)
            packet.readFully(magic, 0, 16)
            if (!magic.contentEquals(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)) {
                return
            }
            
            // Pongレスポンスを送信
            val pong = UnconnectedPong().apply {
                this.pingTime = pingTime
                this.serverGUID = this@RakNetServer.serverGUID
                this.data = this@RakNetServer.pongData
            }
            
            socket?.send(Datagram(packet = pong.encode(), address = datagram.address))
        } catch (e: Exception) {
            // エラー処理
        }
    }
    
    /**
     * UnconnectedPongメッセージ
     */
    private class UnconnectedPong {
        var pingTime: Long = 0
        var serverGUID: Long = 0
        var data: ByteArray = ByteArray(0)
        
        fun encode(): Source = buildPacket {
            writeByte(Protocol.MessageID.UNCONNECTED_PONG)
            writeLong(pingTime)
            writeLong(serverGUID)
            writeBytes(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)
            writeShort(data.size.toShort())
            writeBytes(data)
        }
    }
} 