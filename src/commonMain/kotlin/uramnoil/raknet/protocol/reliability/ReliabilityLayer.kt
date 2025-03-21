package uramnoil.raknet.protocol.reliability

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import kotlinx.io.readUInt
import kotlinx.io.writeUInt
import uramnoil.raknet.protocol.Protocol
import uramnoil.raknet.util.readUInt24
import uramnoil.raknet.util.writeBytes
import uramnoil.raknet.util.writeUInt24
import kotlin.experimental.or

/**
 * 信頼性レイヤー
 * パケットの信頼性、順序、分割を管理する
 */
class ReliabilityLayer {
    private var messageIndex: UInt = 0u
    private var sequenceNumber: UInt = 0u
    private var orderIndex: UInt = 0u
    private var splitId: UInt = 0u
    
    // 再送信キュー
    private val resendQueue = mutableMapOf<UInt, ReliablePacket>()
    
    // 分割パケットの一時保存
    private val splitPackets = mutableMapOf<UInt, Array<ByteArray?>>()
    
    /**
     * 信頼性パケットを作成
     */
    fun createPacket(data: ByteArray, reliability: Byte): ByteArray {
        val packet = buildPacket {
            // 信頼性フラグ
            writeByte(reliability)
            
            // 信頼性がある場合はメッセージインデックスを書き込む
            if (reliability == Protocol.RELIABILITY_RELIABLE || 
                reliability == Protocol.RELIABILITY_RELIABLE_ORDERED || 
                reliability == Protocol.RELIABILITY_RELIABLE_SEQUENCED) {
                
                writeUInt24(messageIndex)
                messageIndex++
            }
            
            // 順序付きの場合は順序インデックスを書き込む
            if (reliability == Protocol.RELIABILITY_UNRELIABLE_SEQUENCED || 
                reliability == Protocol.RELIABILITY_RELIABLE_ORDERED || 
                reliability == Protocol.RELIABILITY_RELIABLE_SEQUENCED) {
                
                writeUInt24(orderIndex)
                writeByte(0) // 順序チャネル
                orderIndex++
            }
            
            // データが大きすぎる場合は分割する
            if (data.size > Protocol.MAX_MTU_SIZE.toInt() - 60) {
                val splitId = this@ReliabilityLayer.splitId++
                val splitCount = (data.size + Protocol.MAX_MTU_SIZE.toInt() - 61) / (Protocol.MAX_MTU_SIZE.toInt() - 60)
                
                for (splitIndex in 0 until splitCount) {
                    val splitSize = minOf(Protocol.MAX_MTU_SIZE.toInt() - 60, data.size - splitIndex * (Protocol.MAX_MTU_SIZE.toInt() - 60))
                    val splitData = data.copyOfRange(splitIndex * (Protocol.MAX_MTU_SIZE.toInt() - 60), splitIndex * (Protocol.MAX_MTU_SIZE.toInt() - 60) + splitSize)
                    
                    // 分割フラグを設定
                    writeByte(reliability or Protocol.SPLIT_FLAG)
                    
                    // 分割情報を書き込む
                    writeUInt(splitId)
                    writeUInt(splitCount.toUInt())
                    writeUInt(splitIndex.toUInt())
                    
                    // データを書き込む
                    writeBytes(splitData)
                }
            } else {
                // 分割なしでデータを書き込む
                writeBytes(data)
            }
        }
        
        return packet.readBytes()
    }
    
    /**
     * 受信したパケットを処理
     */
    fun processPacket(data: ByteArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        val packet = ByteReadPacket(data)
        
        try {
            // 信頼性フラグを読み取る
            val reliability = packet.readByte()
            val hasSplit = (reliability.toInt() and Protocol.SPLIT_FLAG.toInt()) != 0
            val reliabilityType = (reliability.toInt() and 0x07).toByte()
            
            // 信頼性がある場合はメッセージインデックスを読み取る
            if (reliabilityType == Protocol.RELIABILITY_RELIABLE || 
                reliabilityType == Protocol.RELIABILITY_RELIABLE_ORDERED || 
                reliabilityType == Protocol.RELIABILITY_RELIABLE_SEQUENCED) {
                
                val messageIndex = packet.readUInt24()
                // メッセージインデックスの処理（重複チェックなど）
            }
            
            // 順序付きの場合は順序インデックスを読み取る
            if (reliabilityType == Protocol.RELIABILITY_UNRELIABLE_SEQUENCED || 
                reliabilityType == Protocol.RELIABILITY_RELIABLE_ORDERED || 
                reliabilityType == Protocol.RELIABILITY_RELIABLE_SEQUENCED) {
                
                val orderIndex = packet.readUInt24()
                val orderChannel = packet.readByte()
                // 順序インデックスの処理
            }
            
            // 分割パケットの場合
            if (hasSplit) {
                val splitId = packet.readUInt()
                val splitCount = packet.readUInt().toInt()
                val splitIndex = packet.readUInt().toInt()
                
                // 分割パケットを保存
                if (!splitPackets.containsKey(splitId)) {
                    splitPackets[splitId] = arrayOfNulls(splitCount)
                }
                
                // データを読み取る
                val splitData = ByteArray(packet.remaining.toInt())
                packet.readFully(splitData)
                
                // 分割パケットを保存
                splitPackets[splitId]!![splitIndex] = splitData
                
                // すべての分割パケットが揃ったかチェック
                val splits = splitPackets[splitId]!!
                if (splits.all { it != null }) {
                    // すべての分割パケットが揃った場合、結合する
                    val totalSize = splits.sumOf { it!!.size }
                    val combinedData = ByteArray(totalSize)
                    var offset = 0
                    
                    for (split in splits) {
                        System.arraycopy(split!!, 0, combinedData, offset, split.size)
                        offset += split.size
                    }
                    
                    // 結合したデータを結果に追加
                    result.add(combinedData)
                    
                    // 使用済みの分割パケットを削除
                    splitPackets.remove(splitId)
                }
            } else {
                // 分割なしの場合、データをそのまま読み取る
                val packetData = ByteArray(packet.remaining.toInt())
                packet.readFully(packetData)
                result.add(packetData)
            }
        } catch (e: Exception) {
            // エラー処理
        }
        
        return result
    }
    
    /**
     * ACKを処理
     */
    fun processACK(packet: Source) {
        try {
            val count = packet.readShort().toInt()
            
            for (i in 0 until count) {
                val sequenceNumber = packet.readUInt24()
                
                // 再送信キューから削除
                resendQueue.remove(sequenceNumber)
            }
        } catch (e: Exception) {
            // エラー処理
        }
    }
    
    /**
     * NACKを処理
     */
    fun processNACK(packet: Source) {
        try {
            val count = packet.readShort().toInt()
            
            for (i in 0 until count) {
                val sequenceNumber = packet.readUInt24()
                
                // 再送信キューから取得して再送信マークを付ける
                resendQueue[sequenceNumber]?.needsResend = true
            }
        } catch (e: Exception) {
            // エラー処理
        }
    }
    
    /**
     * 再送信が必要なパケットを取得
     */
    fun getPacketsForResend(): List<ReliablePacket> {
        return resendQueue.values.filter { it.needsResend }
    }
    
    /**
     * パケットを再送信キューに追加
     */
    fun addToResendQueue(sequenceNumber: UInt, data: ByteArray) {
        resendQueue[sequenceNumber] = ReliablePacket(sequenceNumber, data)
    }
}

/**
 * 信頼性パケット
 */
data class ReliablePacket(
    val sequenceNumber: UInt,
    val data: ByteArray,
    var needsResend: Boolean = false,
    var sendTime: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReliablePacket) return false
        
        return sequenceNumber == other.sequenceNumber
    }
    
    override fun hashCode(): Int {
        return sequenceNumber.hashCode()
    }
} 