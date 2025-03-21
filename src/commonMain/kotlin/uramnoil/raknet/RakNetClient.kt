package uramnoil.raknet

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.io.Source
import kotlinx.coroutines.*
import uramnoil.raknet.protocol.Protocol
import uramnoil.raknet.protocol.message.*
import uramnoil.raknet.protocol.reliability.DatagramHandler
import uramnoil.raknet.protocol.reliability.ReliabilityLayer
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * RakNetクライアント
 * サーバーへの接続とデータ送受信を管理する
 */
class RakNetClient(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private var socket: ConnectedDatagramSocket? = null
    private var selectorManager: SelectorManager? = null
    private val clientGUID = Random.nextLong()
    
    private var mtu: UShort = Protocol.MIN_MTU_SIZE
    private var serverGUID: Long = 0
    private var isConnected = false
    
    private val datagramHandler = DatagramHandler()
    private val reliabilityLayer = ReliabilityLayer()
    
    private var receiveJob: Job? = null
    private var pingJob: Job? = null
    
    /**
     * サーバーにPingを送信し、応答を待ちます
     */
    suspend fun ping(localAddress: SocketAddress, remoteAddress: SocketAddress, timeout: Int = 5): ByteArray? {
        val selectorManager = SelectorManager()
        val socket = aSocket(selectorManager).udp().connect(localAddress, remoteAddress)
        
        try {
            val ping = UnconnectedPing().apply {
                pingTime = System.currentTimeMillis()
                clientGUID = this@RakNetClient.clientGUID
            }
            
            // Pingパケットを送信
            socket.send(Datagram(packet = ping.encode(), address = socket.remoteAddress))
            
            // 応答を待つ
            val response = withTimeoutOrNull(timeout.seconds) {
                val datagram = socket.receive()
                val packet = datagram.packet
                
                // パケットIDを確認
                if (packet.readByte() != Protocol.MessageID.UNCONNECTED_PONG) {
                    return@withTimeoutOrNull null
                }
                
                // 応答時間を読み取る
                packet.discard(8) // pingTime
                
                // サーバーGUIDを読み取る
                packet.discard(8) // serverGUID
                
                // マジックを検証
                val magic = ByteArray(16)
                packet.readFully(magic, 0, 16)
                if (!magic.contentEquals(Protocol.UNCONNECTED_MESSAGE_SEQUENCE)) {
                    return@withTimeoutOrNull null
                }
                
                // データ長を読み取る
                val dataLength = packet.remaining.toInt()
                val data = ByteArray(dataLength)
                packet.readFully(data, 0, dataLength)
                
                return@withTimeoutOrNull data
            }
            
            return response
        } finally {
            socket.close()
            selectorManager.close()
        }
    }
    
    /**
     * サーバーに接続します
     */
    suspend fun connect(localAddress: SocketAddress, remoteAddress: SocketAddress, timeout: Int = 10): Boolean {
        if (isConnected) return true
        
        selectorManager = SelectorManager()
        socket = aSocket(selectorManager!!).udp().connect(localAddress, remoteAddress)
        
        try {
            // MTU検出とハンドシェイク
            if (!performHandshake(timeout)) {
                close()
                return false
            }
            
            // 接続確立後の処理
            startReceiving()
            startPinging()
            
            isConnected = true
            return true
        } catch (e: Exception) {
            close()
            return false
        }
    }
    
    /**
     * データを送信します
     */
    suspend fun send(data: ByteArray, reliability: Byte = Protocol.RELIABILITY_RELIABLE_ORDERED): Boolean {
        if (!isConnected || socket == null) return false
        
        val packet = reliabilityLayer.createPacket(data, reliability)
        val datagram = datagramHandler.createDatagram(packet)
        
        socket?.send(Datagram(packet = datagram, address = socket!!.remoteAddress))
        return true
    }
    
    /**
     * データを受信します
     */
    suspend fun receive(timeout: Int = 5): ByteArray? {
        if (!isConnected) return null
        
        return reliabilityLayer.receiveQueue.poll()
    }
    
    /**
     * 接続を閉じます
     */
    fun close() {
        if (isConnected) {
            coroutineScope.launch {
                sendDisconnectionNotification()
            }
        }
        
        receiveJob?.cancel()
        pingJob?.cancel()
        
        socket?.close()
        socket = null
        
        selectorManager?.close()
        selectorManager = null
        
        isConnected = false
    }
    
    /**
     * 接続状態を取得します
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * ハンドシェイクを実行します
     */
    private suspend fun performHandshake(timeout: Int): Boolean {
        return withTimeoutOrNull(timeout.seconds) {
            // MTU検出
            val detectedMTU = detectMTU()
            if (detectedMTU == null) {
                return@withTimeoutOrNull false
            }
            
            mtu = detectedMTU
            
            // OpenConnectionRequest2を送信
            if (!sendOpenConnectionRequest2()) {
                return@withTimeoutOrNull false
            }
            
            // ConnectionRequestを送信
            if (!sendConnectionRequest()) {
                return@withTimeoutOrNull false
            }
            
            // NewIncomingConnectionを送信
            if (!sendNewIncomingConnection()) {
                return@withTimeoutOrNull false
            }
            
            return@withTimeoutOrNull true
        } ?: false
    }
    
    /**
     * MTUサイズを検出します
     */
    private suspend fun detectMTU(): UShort? {
        // 二分探索でMTUを検出
        var min = Protocol.MIN_MTU_SIZE
        var max = Protocol.MAX_MTU_SIZE
        
        while (min <= max) {
            val mid = ((min.toInt() + max.toInt()) / 2).toUShort()
            
            if (tryMTU(mid)) {
                min = (mid + 1u).toUShort()
            } else {
                max = (mid - 1u).toUShort()
            }
        }
        
        return if (max >= Protocol.MIN_MTU_SIZE) max else null
    }
    
    /**
     * 指定されたMTUサイズでOpenConnectionRequest1を送信し、応答を確認します
     */
    private suspend fun tryMTU(mtuSize: UShort): Boolean {
        val request = OpenConnectionRequest1().apply {
            protocol = Protocol.PROTOCOL_VERSION
            mtu = mtuSize
        }
        
        socket?.send(Datagram(packet = request.encode(), address = socket!!.remoteAddress))
        
        // 応答を待つ
        return withTimeoutOrNull(500.milliseconds) {
            val datagram = socket?.receive() ?: return@withTimeoutOrNull false
            val packet = datagram.packet
            
            if (packet.readByte() != Protocol.MessageID.OPEN_CONNECTION_REPLY_1) {
                return@withTimeoutOrNull false
            }
            
            val reply = OpenConnectionReply1()
            if (!reply.decode(packet)) {
                return@withTimeoutOrNull false
            }
            
            serverGUID = reply.serverGUID
            return@withTimeoutOrNull true
        } ?: false
    }
    
    /**
     * OpenConnectionRequest2を送信します
     */
    private suspend fun sendOpenConnectionRequest2(): Boolean {
        val request = OpenConnectionRequest2().apply {
            serverAddress = socket!!.remoteAddress
            mtu = mtu
            clientGUID = this@RakNetClient.clientGUID
        }
        
        socket?.send(Datagram(packet = request.encode(), address = socket!!.remoteAddress))
        
        // 応答を待つ
        return withTimeoutOrNull(1.seconds) {
            val datagram = socket?.receive() ?: return@withTimeoutOrNull false
            val packet = datagram.packet
            
            if (packet.readByte() != Protocol.MessageID.OPEN_CONNECTION_REPLY_2) {
                return@withTimeoutOrNull false
            }
            
            val reply = OpenConnectionReply2()
            if (!reply.decode(packet)) {
                return@withTimeoutOrNull false
            }
            
            // MTUを更新
            mtu = reply.mtu
            
            // ReliabilityLayerを初期化
            reliabilityLayer.initialize(mtu)
            datagramHandler.initialize(mtu)
            
            return@withTimeoutOrNull true
        } ?: false
    }
    
    /**
     * ConnectionRequestを送信します
     */
    private suspend fun sendConnectionRequest(): Boolean {
        val request = ConnectionRequest().apply {
            clientGUID = this@RakNetClient.clientGUID
            timestamp = System.currentTimeMillis()
        }
        
        val packet = reliabilityLayer.createPacket(request.encode().readBytes(), Protocol.RELIABILITY_RELIABLE)
        val datagram = datagramHandler.createDatagram(packet)
        
        socket
            ?.send(Datagram(packet = datagram, address = socket!!.remoteAddress))
            ?: return false
        
        // 応答を待つ
        return withTimeoutOrNull(2.seconds) {
            while (true) {
                val datagram = socket?.receive() ?: return@withTimeoutOrNull false
                val packet = datagram.packet

                // ACK/NACKの処理
                if (packet.remaining > 0 && (packet.readByte().toInt() and 0xC0) != 0) {
                    packet.discard(packet.remaining)
                    continue
                }

                // データグラムの処理
                val processedPackets = datagramHandler.processDatagram(packet)
                for (processedPacket in processedPackets) {
                    val messages = reliabilityLayer.processPacket(processedPacket)

                    for (message in messages) {
                        if (message.size < 1) continue

                        val messageId = message[0]
                        if (messageId == Protocol.MessageID.CONNECTION_REQUEST_ACCEPTED) {
                            return@withTimeoutOrNull true
                        }
                    }
                }
                return@withTimeoutOrNull false
            }
        }
    }
    
    /**
     * NewIncomingConnectionを送信します
     */
    private suspend fun sendNewIncomingConnection(): Boolean {
        val request = NewIncomingConnection().apply {
            serverAddress = socket!!.remoteAddress
            timestamp = System.currentTimeMillis()
        }
        
        val packet = reliabilityLayer.createPacket(request.encode().readBytes(), Protocol.RELIABILITY_RELIABLE)
        val datagram = datagramHandler.createDatagram(packet)
        
        socket?.send(Datagram(packet = datagram, address = socket!!.remoteAddress))
        return true
    }
    
    /**
     * 切断通知を送信します
     */
    private suspend fun sendDisconnectionNotification() {
        val notification = DisconnectionNotification()
        
        val packet = reliabilityLayer.createPacket(notification.encode().readBytes(), Protocol.RELIABILITY_RELIABLE)
        val datagram = datagramHandler.createDatagram(packet)
        
        socket?.send(Datagram(packet = datagram, address = socket!!.remoteAddress))
    }
    
    /**
     * パケット受信ループを開始します
     */
    private fun startReceiving() {
        receiveJob = coroutineScope.launch {
            try {
                while (isActive && isConnected) {
                    val datagram = socket?.receive() ?: continue
                    val packet = datagram.packet
                    
                    // ACK/NACKの処理
                    if (packet.remaining > 0) {
                        val firstByte = packet.readByte().toInt()
                        if ((firstByte and 0xC0) != 0) {
                            processAckNack(firstByte, packet)
                            continue
                        }
                        
                        // 読み取ったバイトを戻す
                        packet.release()
                        packet.rewind(1)
                    }
                    
                    // データグラムの処理
                    val processedPackets = datagramHandler.processDatagram(packet)
                    for (processedPacket in processedPackets) {
                        val messages = reliabilityLayer.processPacket(processedPacket)
                        
                        for (message in messages) {
                            if (message.size < 1) continue
                            
                            val messageId = message[0]
                            when (messageId) {
                                Protocol.MessageID.DISCONNECTION_NOTIFICATION -> {
                                    close()
                                }
                                Protocol.MessageID.CONNECTED_PING -> {
                                    handleConnectedPing(message)
                                }
                            }
                        }
                    }
                    
                    // ACKを送信
                    sendAcks()
                }
            } catch (e: Exception) {
                // エラー処理
                close()
            }
        }
    }
    
    /**
     * 定期的なPingを開始します
     */
    private fun startPinging() {
        pingJob = coroutineScope.launch {
            try {
                while (isActive && isConnected) {
                    sendConnectedPing()
                    delay(1.seconds)
                }
            } catch (e: Exception) {
                // エラー処理
            }
        }
    }
    
    /**
     * ConnectedPingを送信します
     */
    private suspend fun sendConnectedPing() {
        val ping = ConnectedPing().apply {
            timestamp = System.currentTimeMillis()
        }
        
        val packet = reliabilityLayer.createPacket(ping.encode().readBytes(), Protocol.RELIABILITY_RELIABLE)
        val datagram = datagramHandler.createDatagram(packet)
        
        socket?.send(Datagram(packet = datagram, address = socket!!.remoteAddress))
    }
    
    /**
     * ConnectedPingを処理します
     */
    private suspend fun handleConnectedPing(data: ByteArray) {
        if (data.size < 9) return
        
        val timestamp = ByteBuffer.wrap(data, 1, 8).getLong()
        
        val pong = ConnectedPong().apply {
            pingTimestamp = timestamp
            pongTimestamp = System.currentTimeMillis()
        }
        
        val packet = reliabilityLayer.createPacket(pong.encode().readBytes(), Protocol.RELIABILITY_RELIABLE)
        val datagram = datagramHandler.createDatagram(packet)
        
        socket?.send(Datagram(packet = datagram, address = socket!!.remoteAddress))
    }
    
    /**
     * ACK/NACKを処理します
     */
    private fun processAckNack(firstByte: Int, packet: Source) {
        if ((firstByte and Protocol.BIT_FLAG_ACK.toInt()) != 0) {
            // ACKの処理
            reliabilityLayer.processACK(packet)
        } else if ((firstByte and Protocol.BIT_FLAG_NACK.toInt()) != 0) {
            // NACKの処理
            reliabilityLayer.processNACK(packet)
        }
    }
    
    /**
     * ACKを送信します
     */
    private suspend fun sendAcks() {
        val acks = datagramHandler.getAcksToSend()
        if (acks.isEmpty()) return
        
        val ackPacket = buildPacket {
            writeByte(Protocol.BIT_FLAG_ACK)
            
            // ACKの数を書き込む
            writeShort(acks.size.toShort())
            
            // 各ACKを書き込む
            for (ack in acks) {
                writeUInt24(ack)
            }
        }
        
        socket?.send(Datagram(packet = ackPacket, address = socket!!.remoteAddress))
    }
} 