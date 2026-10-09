package com.example.dotmapapp

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

enum class DotColor { RED, YELLOW, ORANGE }

/**
 Dot

 Dot on the map stores the name, the latitude
 longitude, a description, the color, and the timestamp in which it was made.
 */
@Serializable
data class Dot(
    val id: String,
    val lat: Double,
    val lng: Double,
    val description: String,
    val color: DotColor,
    val timestamp: Long = System.currentTimeMillis()
)

/**
  DotStore Class

  @param ctx - context
 */
class DotStore(private val ctx: Context) {
    // all dots are stored in a .json file
    private val file = File(ctx.filesDir, "dots.json")
    // the json value that will be stored
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * load()
     *
     * Load the list of Dots from the json file
     *
     * @return the list of dots
     */
    fun load(): List<Dot> {
        // if the .json file does not exist, just return an empty list
        if (!file.exists()) return emptyList()
        return try {
            // if valid, read the file
            json.decodeFromString(file.readText())
        } catch (e: Exception) {
            emptyList() // corrupt or empty file... start fresh rather than crash
        }
    }

    /**
     * save()
     *
     * @param - the list of dots
     */
    fun save(dots: List<Dot>) {
        file.writeText(json.encodeToString(dots))
    }
}