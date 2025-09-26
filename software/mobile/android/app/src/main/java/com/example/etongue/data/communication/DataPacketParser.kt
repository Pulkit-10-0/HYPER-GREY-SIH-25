package com.example.etongue.data.communication

import com.example.etongue.data.models.ColorData
import com.example.etongue.data.models.ElectrodeReadings
import com.example.etongue.data.models.SensorDataPacket
import com.example.etongue.data.models.SensorReadings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Parses incoming data packets from ESP32 devices
 * Handles both JSON and raw string formats
 */
class DataPacketParser {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Parses a raw data packet string into a SensorDataPacket
     * @param rawData The raw data string from ESP32
     * @param deviceId The ID of the device that sent the data
     * @return Result containing the parsed packet or error
     */
    fun parseDataPacket(rawData: String, deviceId: String): Result<SensorDataPacket> {
        return try {
            when {
                rawData.trim().startsWith("{") -> parseJsonPacket(rawData, deviceId)
                rawData.contains(",") -> parseCsvPacket(rawData, deviceId)
                else -> Result.failure(IllegalArgumentException("Unknown data format: $rawData"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Parses JSON formatted data packet
     */
    private fun parseJsonPacket(jsonData: String, deviceId: String): Result<SensorDataPacket> {
        return try {
            val jsonObject = json.parseToJsonElement(jsonData).jsonObject
            
            // Handle both old and new timestamp formats
            val timestamp = when {
                jsonObject["timestamp"]?.jsonPrimitive?.content?.contains("T") == true -> {
                    // ISO timestamp format - convert to milliseconds
                    try {
                        val isoTimestamp = jsonObject["timestamp"]?.jsonPrimitive?.content ?: ""
                        java.time.Instant.parse(isoTimestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                }
                else -> jsonObject["timestamp"]?.jsonPrimitive?.long ?: System.currentTimeMillis()
            }
            
            // Check if this is the new format (has SS, Cu, Zn, etc.)
            val isNewFormat = jsonObject.containsKey("SS") || jsonObject.containsKey("Cu")
            
            val (sensorReadings, electrodeReadings) = if (isNewFormat) {
                parseNewFormatData(jsonObject)
            } else {
                // Old format
                val sensorReadings = parseSensorReadings(jsonObject["sensors"]?.jsonObject)
                val electrodeReadings = parseElectrodeReadings(jsonObject["electrodes"]?.jsonObject)
                Pair(sensorReadings, electrodeReadings)
            }
            
            val packet = SensorDataPacket(
                timestamp = timestamp,
                deviceId = deviceId,
                sensorReadings = sensorReadings,
                electrodeReadings = electrodeReadings
            )
            
            Result.success(packet) // Accept all packets for now to test with real data
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Parses the new format data with SS, Cu, Zn, Ag, Pt, etc.
     */
    private fun parseNewFormatData(jsonObject: JsonObject): Pair<SensorReadings, ElectrodeReadings> {
        // Extract sensor readings
        val temperature = jsonObject["Temp"]?.jsonPrimitive?.double ?: 25.0
        val ph = jsonObject["pH"]?.jsonPrimitive?.double ?: 7.0
        val tds = jsonObject["TDS"]?.jsonPrimitive?.double ?: 0.0
        val uv = jsonObject["UV"]?.jsonPrimitive?.double ?: 0.0
        val soil = jsonObject["Soil"]?.jsonPrimitive?.double ?: 0.0
        
        val sensorReadings = SensorReadings(
            ph = ph,
            tds = tds,
            uvAbsorbance = uv,
            temperature = temperature,
            colorRgb = ColorData(red = 128, green = 128, blue = 128), // Default color
            moisturePercentage = soil
        )
        
        // Extract electrode readings
        val ss = jsonObject["SS"]?.jsonPrimitive?.double ?: 0.0
        val cu = jsonObject["Cu"]?.jsonPrimitive?.double ?: 0.0
        val zn = jsonObject["Zn"]?.jsonPrimitive?.double ?: 0.0
        val ag = jsonObject["Ag"]?.jsonPrimitive?.double ?: 0.0
        val pt = jsonObject["Pt"]?.jsonPrimitive?.double ?: 0.0
        
        val electrodeReadings = ElectrodeReadings(
            platinum = pt,
            silver = ag,
            silverChloride = ss, // Using SS for silver chloride
            stainlessSteel = cu, // Using Cu for stainless steel
            copper = cu,
            carbon = zn, // Using Zn for carbon
            zinc = zn
        )
        
        return Pair(sensorReadings, electrodeReadings)
    }
    
    /**
     * Parses CSV formatted data packet
     * Expected format: ph,tds,uv,temp,r,g,b,moisture,pt,ag,agcl,ss,cu,c,zn
     */
    private fun parseCsvPacket(csvData: String, deviceId: String): Result<SensorDataPacket> {
        return try {
            val values = csvData.trim().split(",").map { it.trim().toDouble() }
            
            if (values.size < 15) {
                return Result.failure(IllegalArgumentException("CSV data must have at least 15 values"))
            }
            
            val sensorReadings = SensorReadings(
                ph = values[0],
                tds = values[1],
                uvAbsorbance = values[2],
                temperature = values[3],
                colorRgb = ColorData(
                    red = values[4].toInt(),
                    green = values[5].toInt(),
                    blue = values[6].toInt()
                ),
                moisturePercentage = values[7]
            )
            
            val electrodeReadings = ElectrodeReadings(
                platinum = values[8],
                silver = values[9],
                silverChloride = values[10],
                stainlessSteel = values[11],
                copper = values[12],
                carbon = values[13],
                zinc = values[14]
            )
            
            val packet = SensorDataPacket(
                timestamp = System.currentTimeMillis(),
                deviceId = deviceId,
                sensorReadings = sensorReadings,
                electrodeReadings = electrodeReadings
            )
            
            if (packet.isValid()) {
                Result.success(packet)
            } else {
                Result.failure(IllegalArgumentException("Invalid sensor data values"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Parses sensor readings from JSON object
     */
    private fun parseSensorReadings(sensorsJson: JsonObject?): SensorReadings {
        return SensorReadings(
            ph = sensorsJson?.get("ph")?.jsonPrimitive?.double ?: 7.0,
            tds = sensorsJson?.get("tds")?.jsonPrimitive?.double ?: 0.0,
            uvAbsorbance = sensorsJson?.get("uv")?.jsonPrimitive?.double ?: 0.0,
            temperature = sensorsJson?.get("temperature")?.jsonPrimitive?.double ?: 25.0,
            colorRgb = parseColorData(sensorsJson?.get("color")?.jsonObject),
            moisturePercentage = sensorsJson?.get("moisture")?.jsonPrimitive?.double ?: 0.0
        )
    }
    
    /**
     * Parses electrode readings from JSON object
     */
    private fun parseElectrodeReadings(electrodesJson: JsonObject?): ElectrodeReadings {
        return ElectrodeReadings(
            platinum = electrodesJson?.get("pt")?.jsonPrimitive?.double ?: 0.0,
            silver = electrodesJson?.get("ag")?.jsonPrimitive?.double ?: 0.0,
            silverChloride = electrodesJson?.get("agcl")?.jsonPrimitive?.double ?: 0.0,
            stainlessSteel = electrodesJson?.get("ss")?.jsonPrimitive?.double ?: 0.0,
            copper = electrodesJson?.get("cu")?.jsonPrimitive?.double ?: 0.0,
            carbon = electrodesJson?.get("c")?.jsonPrimitive?.double ?: 0.0,
            zinc = electrodesJson?.get("zn")?.jsonPrimitive?.double ?: 0.0
        )
    }
    
    /**
     * Parses color data from JSON object
     */
    private fun parseColorData(colorJson: JsonObject?): ColorData {
        val red = colorJson?.get("r")?.jsonPrimitive?.int ?: 0
        val green = colorJson?.get("g")?.jsonPrimitive?.int ?: 0
        val blue = colorJson?.get("b")?.jsonPrimitive?.int ?: 0
        
        return ColorData(red = red, green = green, blue = blue)
    }
    
    /**
     * Validates that a raw data string appears to be valid sensor data
     * @param rawData The raw data string to validate
     * @return true if the data appears valid
     */
    fun isValidDataFormat(rawData: String): Boolean {
        return try {
            when {
                rawData.trim().startsWith("{") -> {
                    json.parseToJsonElement(rawData)
                    true
                }
                rawData.contains(",") -> {
                    val values = rawData.trim().split(",")
                    values.size >= 15 && values.all { it.trim().toDoubleOrNull() != null }
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
}