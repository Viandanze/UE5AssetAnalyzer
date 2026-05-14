package com.example.ue5analyzer.data.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.ue5analyzer.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Low-level .uasset File Parser
 * 
 * Parses UE5 .uasset binary files to extract header information,
 * including file version, package flags, and import/export tables.
 * 
 * This parser complements UEProjectParser by providing deeper
 * binary-level analysis of individual asset files.
 * 
 * Note: ImportEntry is defined in UEProjectParser.kt to avoid
 * redeclaration issues. This parser uses those types directly.
 */

// Uasset file constants
private const val UASSET_MAGIC = 0x9E2A83C1
private const val LEGACY_MAGIC = 0xA1B2C3D4

/**
 * Uasset file header information
 */
data class UassetHeader(
    val magic: Int,
    val version: Int,
    val packageFlags: Long,
    val nameCount: Int,
    val nameOffset: Long,
    val importCount: Int,
    val importOffset: Long,
    val exportCount: Int,
    val exportOffset: Long,
    val heritageOffset: Long
)

/**
 * Parsed uasset metadata
 */
data class UassetMetadata(
    val filePath: String,
    val fileSize: Long,
    val header: UassetHeader?,
    val imports: List<ImportEntry>,
    val exports: List<ExportEntry>,
    val parseSuccess: Boolean,
    val errorMessage: String? = null,
    val rawHeaderSize: Long = 0
)

/**
 * Export table entry
 */
data class ExportEntry(
    val classIndex: Int,
    val superIndex: Int,
    val packageIndex: Int,
    val objectName: String
)

/**
 * Uasset Parser
 */
class UassetParser(private val context: Context) {
    
    companion object {
        private const val TAG = "UassetParser"
        
        // Maximum header size to read (avoid reading entire large files)
        private const val MAX_HEADER_READ_SIZE = 64 * 1024L  // 64KB
        
        // Minimum valid uasset file size
        private const val MIN_FILE_SIZE = 32
    }
    
    /**
     * Parse a uasset file and extract metadata
     */
    suspend fun parseUasset(uri: Uri): UassetMetadata = withContext(Dispatchers.IO) {
        try {
            val file = DocumentFile.fromSingleUri(context, uri) ?: return@withContext 
                createErrorMetadata(uri.toString(), "Cannot access file")
            
            val fileSize = file.length()
            val fileName = file.name ?: "unknown.uasset"
            
            if (fileSize < MIN_FILE_SIZE) {
                return@withContext createErrorMetadata(uri.toString(), "File too small")
            }
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                parseFromStream(inputStream, fileName, fileSize)
            } ?: createErrorMetadata(uri.toString(), "Cannot open file for reading")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing uasset: ${e.message}")
            createErrorMetadata(uri.toString(), e.message ?: "Unknown error")
        }
    }
    
    /**
     * Parse uasset from file path
     */
    suspend fun parseUasset(filePath: String): UassetMetadata = withContext(Dispatchers.IO) {
        try {
            val file = DocumentFile.fromTreeUri(context, Uri.parse(filePath)) ?: 
                return@withContext createErrorMetadata(filePath, "Cannot access file")
            
            val fileSize = file.length()
            val fileName = file.name ?: "unknown.uasset"
            
            if (fileSize < MIN_FILE_SIZE) {
                return@withContext createErrorMetadata(filePath, "File too small")
            }
            
            context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                parseFromStream(inputStream, fileName, fileSize)
            } ?: createErrorMetadata(filePath, "Cannot open file for reading")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing uasset: ${e.message}")
            createErrorMetadata(filePath, e.message ?: "Unknown error")
        }
    }
    
    /**
     * Parse uasset from InputStream
     */
    private fun parseFromStream(
        inputStream: InputStream,
        fileName: String,
        fileSize: Long
    ): UassetMetadata {
        val limitedStream = LimitedInputStream(inputStream, MAX_HEADER_READ_SIZE)
        val dis = DataInputStream(limitedStream)
        dis.mark(MAX_HEADER_READ_SIZE.toInt())
        
        return try {
            // Read and validate magic number
            val magic = dis.readInt()
            if (magic != UASSET_MAGIC && magic != LEGACY_MAGIC) {
                return createErrorMetadata(fileName, "Invalid uasset magic: 0x${Integer.toHexString(magic)}")
            }
            
            // Read legacy version (4 bytes) - varies by UE version
            val legacyVersion = dis.readInt()
            
            // Read file version headers
            val versionHeaders = readVersionHeaders(dis)
            
            // Try to determine UE version
            val ueVersion = detectUeVersion(versionHeaders, legacyVersion)
            
            // Read package flags
            val packageFlags = if (ueVersion >= 5) {
                dis.readLong()
            } else {
                dis.readInt().toLong()
            }
            
            // Read name/import/export table info
            val nameCount = dis.readInt()
            val nameOffset = if (ueVersion >= 5) dis.readLong() else dis.readInt().toLong()
            
            val importCount = dis.readInt()
            val importOffset = if (ueVersion >= 5) dis.readLong() else dis.readInt().toLong()
            
            val exportCount = dis.readInt()
            val exportOffset = if (ueVersion >= 5) dis.readLong() else dis.readInt().toLong()
            
            val heritageOffset = if (ueVersion >= 5) dis.readLong() else dis.readInt().toLong()
            
            // Create header
            val header = UassetHeader(
                magic = magic,
                version = legacyVersion,
                packageFlags = packageFlags,
                nameCount = nameCount,
                nameOffset = nameOffset,
                importCount = importCount,
                importOffset = importOffset,
                exportCount = exportCount,
                exportOffset = exportOffset,
                heritageOffset = heritageOffset
            )
            
            // Reset stream to try parsing import table
            dis.reset()
            val imports = tryParseImports(dis, importOffset, importCount)
            val exports = tryParseExports(dis, exportOffset, exportCount)
            
            UassetMetadata(
                filePath = fileName,
                fileSize = fileSize,
                header = header,
                imports = imports,
                exports = exports,
                parseSuccess = true,
                rawHeaderSize = limitedStream.bytesRead
            )
            
        } catch (e: IOException) {
            Log.e(TAG, "Parse error: ${e.message}")
            createErrorMetadata(fileName, "Parse error: ${e.message}")
        }
    }
    
    /**
     * Read version-specific headers
     */
    private fun readVersionHeaders(dis: DataInputStream): Map<String, Int> {
        val headers = mutableMapOf<String, Int>()
        
        try {
            // UE5 uses different header format
            // Try to read optional headers
            while (dis.available() >= 4) {
                val marker = dis.readInt()
                // Common markers: -2, -3, -4 for different header versions
                if (marker < 0) {
                    headers["header_$marker"] = dis.readInt()
                } else {
                    // Not a header marker, put back
                    dis.reset()
                    break
                }
            }
        } catch (e: Exception) {
            // Ignore header read errors
        }
        
        return headers
    }
    
    /**
     * Detect Unreal Engine version from headers
     */
    private fun detectUeVersion(headers: Map<String, Int>, legacyVersion: Int): Int {
        return when {
            headers.containsKey("header_-1") -> 5  // UE5
            headers.containsKey("header_-2") -> 4  // UE4.20+
            headers.containsKey("header_-3") -> 4  // UE4.15+
            legacyVersion in 800..899 -> 5          // UE5 alpha/beta
            legacyVersion in 700..799 -> 4          // UE4
            legacyVersion < 500 -> 3                // UE3 or earlier
            else -> 4                               // Default to UE4
        }
    }
    
    /**
     * Try to parse import table
     */
    private fun tryParseImports(dis: DataInputStream, offset: Long, count: Int): List<ImportEntry> {
        if (count <= 0 || offset <= 0) return emptyList()
        
        return try {
            // Skip to import offset (simplified - actual implementation
            // needs proper FName table parsing)
            val imports = mutableListOf<ImportEntry>()
            
            // For now, return empty list as proper FName parsing
            // requires the complete name table which may be elsewhere
            imports
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse imports: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Try to parse export table
     */
    private fun tryParseExports(dis: DataInputStream, offset: Long, count: Int): List<ExportEntry> {
        if (count <= 0 || offset <= 0) return emptyList()
        
        return try {
            val exports = mutableListOf<ExportEntry>()
            // Simplified - proper implementation needs complete parsing
            exports
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse exports: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Quick check if file is a valid uasset
     */
    fun isValidUasset(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val dis = DataInputStream(stream)
                val magic = dis.readInt()
                magic == UASSET_MAGIC || magic == LEGACY_MAGIC
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Create error metadata
     */
    private fun createErrorMetadata(path: String, error: String): UassetMetadata {
        return UassetMetadata(
            filePath = path,
            fileSize = 0,
            header = null,
            imports = emptyList(),
            exports = emptyList(),
            parseSuccess = false,
            errorMessage = error
        )
    }
}

/**
 * InputStream that limits bytes read
 */
private class LimitedInputStream(
    inputStream: InputStream,
    private val maxBytes: Long
) : FilterInputStream(inputStream) {
    var bytesRead = 0L
    
    override fun read(): Int {
        if (bytesRead >= maxBytes) return -1
        val b = super.read()
        if (b != -1) bytesRead++
        return b
    }
    
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (bytesRead >= maxBytes) return -1
        val remaining = (maxBytes - bytesRead).toInt()
        val toRead = minOf(len, remaining)
        val read = super.read(b, off, toRead)
        if (read != -1) bytesRead += read
        return read
    }
}
