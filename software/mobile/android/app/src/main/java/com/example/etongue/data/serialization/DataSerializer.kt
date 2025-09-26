package com.example.etongue.data.serialization

import com.example.etongue.data.models.SensorDataBatch
import com.example.etongue.data.models.SensorDataPacket
import com.example.etongue.data.models.DataFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerializationException

/**
 * Handles serialization and deserialization of sensor data to/from JSON format
 * Uses Kotlinx Serialization for type-safe JSON operations
 */
class DataSerializer {
    
    companion object {
        /**
         * JSON configuration for serialization
         * - prettyPrint: Makes JSON human-readable
         * - ignoreUnknownKeys: Allows forward compatibility
         * - encodeDefaults: Includes default values in JSON
         */
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        
        /**
         * Serializes a SensorDataBatch to JSON string
         * @param batch The sensor data batch to serialize
         * @return JSON string representation
         * @throws SerializationException if serialization fails
         */
        fun serializeBatch(batch: SensorDataBatch): String {
            return try {
                json.encodeToString(batch)
            } catch (e: Exception) {
                throw SerializationException("Failed to serialize SensorDataBatch: ${e.message}", e)
            }
        }
        
        /**
         * Deserializes a JSON string to SensorDataBatch
         * @param jsonString The JSON string to deserialize
         * @return SensorDataBatch instance
         * @throws SerializationException if deserialization fails
         */
        fun deserializeBatch(jsonString: String): SensorDataBatch {
            return try {
                json.decodeFromString<SensorDataBatch>(jsonString)
            } catch (e: Exception) {
                throw SerializationException("Failed to deserialize SensorDataBatch: ${e.message}", e)
            }
        }
        
        /**
         * Serializes a single SensorDataPacket to JSON string
         * @param packet The sensor data packet to serialize
         * @return JSON string representation
         * @throws SerializationException if serialization fails
         */
        fun serializePacket(packet: SensorDataPacket): String {
            return try {
                json.encodeToString(packet)
            } catch (e: Exception) {
                throw SerializationException("Failed to serialize SensorDataPacket: ${e.message}", e)
            }
        }
        
        /**
         * Deserializes a JSON string to SensorDataPacket
         * @param jsonString The JSON string to deserialize
         * @return SensorDataPacket instance
         * @throws SerializationException if deserialization fails
         */
        fun deserializePacket(jsonString: String): SensorDataPacket {
            return try {
                json.decodeFromString<SensorDataPacket>(jsonString)
            } catch (e: Exception) {
                throw SerializationException("Failed to deserialize SensorDataPacket: ${e.message}", e)
            }
        }
        
        /**
         * Serializes a list of SensorDataPackets to JSON string
         * @param packets The list of sensor data packets to serialize
         * @return JSON string representation
         * @throws SerializationException if serialization fails
         */
        fun serializePacketList(packets: List<SensorDataPacket>): String {
            return try {
                json.encodeToString(packets)
            } catch (e: Exception) {
                throw SerializationException("Failed to serialize packet list: ${e.message}", e)
            }
        }
        
        /**
         * Deserializes a JSON string to list of SensorDataPackets
         * @param jsonString The JSON string to deserialize
         * @return List of SensorDataPacket instances
         * @throws SerializationException if deserialization fails
         */
        fun deserializePacketList(jsonString: String): List<SensorDataPacket> {
            return try {
                json.decodeFromString<List<SensorDataPacket>>(jsonString)
            } catch (e: Exception) {
                throw SerializationException("Failed to deserialize packet list: ${e.message}", e)
            }
        }
        
        /**
         * Serializes a DataFile to JSON string
         * @param dataFile The data file info to serialize
         * @return JSON string representation
         * @throws SerializationException if serialization fails
         */
        fun serializeDataFile(dataFile: DataFile): String {
            return try {
                json.encodeToString(dataFile)
            } catch (e: Exception) {
                throw SerializationException("Failed to serialize DataFile: ${e.message}", e)
            }
        }
        
        /**
         * Deserializes a JSON string to DataFile
         * @param jsonString The JSON string to deserialize
         * @return DataFile instance
         * @throws SerializationException if deserialization fails
         */
        fun deserializeDataFile(jsonString: String): DataFile {
            return try {
                json.decodeFromString<DataFile>(jsonString)
            } catch (e: Exception) {
                throw SerializationException("Failed to deserialize DataFile: ${e.message}", e)
            }
        }
        
        /**
         * Validates that a JSON string can be parsed as a SensorDataBatch
         * @param jsonString The JSON string to validate
         * @return true if valid, false otherwise
         */
        fun isValidBatchJson(jsonString: String): Boolean {
            return try {
                deserializeBatch(jsonString)
                true
            } catch (e: SerializationException) {
                false
            }
        }
        
        /**
         * Validates that a JSON string can be parsed as a SensorDataPacket
         * @param jsonString The JSON string to validate
         * @return true if valid, false otherwise
         */
        fun isValidPacketJson(jsonString: String): Boolean {
            return try {
                deserializePacket(jsonString)
                true
            } catch (e: SerializationException) {
                false
            }
        }
        
        /**
         * Gets the JSON configuration used by this serializer
         * Useful for testing and debugging
         */
        fun getJsonConfig(): Json = json
    }
}