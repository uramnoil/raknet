package uramnoil.raknet.protocol.reliability

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import uramnoil.raknet.protocol.Protocol
import uramnoil.raknet.util.readUInt24
import uramnoil.raknet.util.writeBytes
import uramnoil.raknet.util.writeUInt24

/**
 * データグラム処理クラス
 * データグラムの送受信と確認応答を管理する
 */
class DatagramHandler {
    private var sequenceNumber: UInt = 0u
    private val receivedDatagrams = mutableSetOf<UInt>()
    private val missingDatagrams = mutableSetOf<UInt>()
    private var highestSequenceNumber: UInt = 0u
    
    /**
     * データグラムを作成
     */
    fun createDatagram(data: ByteArray): Source = buildPacket {
        // データグラムフラグ
        writeByte(Protocol.BIT_FLAG_DATAGRAM)
        
        // シーケンス番号
        writeUInt24(sequenceNumber)
        sequenceNumber++
        
        // データを書き込む
        writeBytes(data)
    }
    
    /**
     * データグラムを処理
     */
    fun processDatagram(packet: Source): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        
        try {
            // シーケンス番号を読み取る
            val sequenceNumber = packet.readUInt24()
            
            // 受信済みデータグラムとして記録
            receivedDatagrams.add(sequenceNumber)
            
            // 欠落データグラムから削除
            missingDatagrams.remove(sequenceNumber)
            
            // 最大シーケンス番号を更新
            if (sequenceNumber > highestSequenceNumber) {
                // 欠落データグラムを検出
                for (i in highestSequenceNumber + 1u until sequenceNumber) {
                    missingDatagrams.add(i)
                }
                
                highestSequenceNumber = sequenceNumber
            }
            
            // データを読み取る
            val data = ByteArray(packet.remaining.toInt())
            packet.readFully(data)
            result.add(data)
        } catch (e: Exception) {
            // エラー処理
        }
        
        return result
    }
    
    /**
     * 送信すべきACKを取得
     */
    fun getAcksToSend(): List<UInt> {
        val acks = receivedDatagrams.toList()
        receivedDatagrams.clear()
        return acks
    }
    
    /**
     * 送信すべきNACKを取得
     */
    fun getNacksToSend(): List<UInt> {
        val nacks = missingDatagrams.toList()
        missingDatagrams.clear()
        return nacks
    }
} 