package com.example.ue5analyzer.data.parser

import com.example.ue5analyzer.model.AssetType
import com.example.ue5analyzer.model.OrphanRiskLevel
import com.example.ue5analyzer.model.UEAsset
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standalone .uasset File Parser
 * Parses a single .uasset file without requiring Android Context or SAF Uri.
 * Supports UE5.2+ binary format with 3 header variants.
 *
 * Usage:
 *   val result = UassetParser.parse(File("/path/to/asset.uasset"))
 *   // or
 *   val result = UassetParser.parse(inputStream, "AssetName", "/path/to/asset.uasset", fileSize)
 */
object UassetParser {

    private const val UASSET_MAGIC: Int = -1638187071  // 0x9E2A83C1 as signed Int32
    private const val MAX_PARSING_SIZE = 10 * 1024 * 1024L  // 10MB limit

    // ============================================================
    // Public API
    // ============================================================

    /**
     * Parse result for a single .uasset file
     */
    data class ParseResult(
        val success: Boolean,
        val asset: UEAsset? = null,
        val nameTable: List<String> = emptyList(),
        val importTable: List<ImportEntry> = emptyList(),
        val headerInfo: HeaderInfo? = null,
        val error: String? = null
    )

    /**
     * Parsed header information
     */
    data class HeaderInfo(
        val legacyFileVersion: Int,
        val fileVersionUE4: Int,
        val fileVersionUE5: Int?,
        val packageName: String,
        val exportCount: Int,
        val importCount: Int,
        val nameCount: Int,
        val totalHeaderSize: Int
    )

    /**
     * Parse a .uasset file from File object
     */
    fun parse(file: File): ParseResult {
        if (!file.exists()) {
            return ParseResult(success = false, error = "File not found: ${file.path}")
        }
        if (file.length() > MAX_PARSING_SIZE) {
            return ParseResult(success = false, error = "File exceeds 10MB limit")
        }

        val bytes = file.readBytes()
        return parseBytes(bytes, file.nameWithoutExtension, file.path, file.length())
    }

    /**
     * Parse a .uasset file from InputStream
     */
    fun parse(
        inputStream: InputStream,
        assetName: String,
        assetPath: String,
        fileSize: Long
    ): ParseResult {
        if (fileSize > MAX_PARSING_SIZE) {
            return ParseResult(success = false, error = "File exceeds 10MB limit")
        }

        val bytes = inputStream.readBytes()
        return parseBytes(bytes, assetName, assetPath, fileSize)
    }

    // ============================================================
    // Internal parsing
    // ============================================================

    private fun parseBytes(
        bytes: ByteArray,
        assetName: String,
        assetPath: String,
        fileSize: Long
    ): ParseResult {
        if (bytes.size < 4) {
            return ParseResult(success = false, error = "File too small to be a valid .uasset")
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val reader = BinaryReader(buffer)

        return try {
            // Read and validate magic number
            val magic = reader.readInt32()
            if (magic != UASSET_MAGIC) {
                return ParseResult(
                    success = false,
                    error = "Invalid magic: 0x${magic.toString(16).uppercase()}"
                )
            }

            // Read LegacyFileVersion
            val lfv = reader.readInt32()
            if (lfv != -7 && lfv != -8) {
                return ParseResult(success = false, error = "Unsupported LegacyFileVersion: $lfv")
            }

            // Read header fields up to variant-dependent section
            reader.readInt32()  // LegacyUE3Version
            val fvUE4 = reader.readInt32()
            val fvUE5 = if (lfv == -8) reader.readInt32() else null
            reader.readInt32()  // FileVersionLicenseeUE4

            // CustomVersions
            val cvCount = reader.readInt32()
            reader.skipBytes(cvCount * 20)

            // TotalHeaderSize
            val totalHeaderSize = reader.readInt32()

            // PackageName
            val packageName = reader.readFString()

            // PackageFlags
            reader.readUInt32()

            // NameCount + NameOffset
            val nameCount = reader.readInt32()
            val nameOffset = reader.readInt32()

            // Variant-dependent fields
            when {
                lfv == -8 && fvUE5 != null && fvUE5 >= 1008 -> {
                    // Variant1: SoftObjectPaths + LocalizationId + GatherableTextData
                    reader.readInt32()  // SoftObjectPathsCount
                    reader.readInt32()  // SoftObjectPathsOffset
                    reader.readFString()  // LocalizationId
                    reader.readInt32()  // GatherableTextDataCount
                    reader.readInt32()  // GatherableTextDataOffset
                }

                lfv == -8 && fvUE5 != null && fvUE5 < 1008 -> {
                    // Variant2: PackageGuid + SoftObjectPaths
                    reader.readFString()  // PackageGuid
                    reader.readInt32()  // SoftObjectPathsCount
                    reader.readInt32()  // SoftObjectPathsOffset
                }

                lfv == -7 -> {
                    // Variant3: PackageGuid + SoftObjectPaths, no FileVersionUE5
                    reader.readFString()  // PackageGuid
                    reader.readInt32()  // SoftObjectPathsCount
                    reader.readInt32()  // SoftObjectPathsOffset
                }
            }

            // ExportCount + ExportOffset
            val exportCount = reader.readInt32()
            reader.readInt32()  // ExportOffset

            // ImportCount + ImportOffset
            val importCount = reader.readInt32()
            val importOffset = reader.readInt32()

            // DependsOffset (skip)
            reader.readInt32()

            // Parse NameTable at nameOffset (absolute offset in file)
            val nameTable = parseNameTable(reader, nameOffset, nameCount)

            // Parse ImportTable at importOffset (absolute offset in file)
            val importEntries = parseImportTable(reader, importOffset, importCount, nameTable)

            // Build dependency list from imports
            val dependencies = importEntries.map { it.objectName }

            // Determine asset type
            val assetType = determineAssetType(assetName, importEntries)

            // Build UEAsset
            val asset = UEAsset(
                id = assetPath,
                name = assetName,
                path = assetPath,
                type = assetType,
                size = fileSize,
                dependencies = dependencies,
                references = emptyList(),  // References are resolved at project level
                isOrphan = false,
                orphanRiskLevel = OrphanRiskLevel.NONE
            )

            ParseResult(
                success = true,
                asset = asset,
                nameTable = nameTable,
                importTable = importEntries,
                headerInfo = HeaderInfo(
                    legacyFileVersion = lfv,
                    fileVersionUE4 = fvUE4,
                    fileVersionUE5 = fvUE5,
                    packageName = packageName,
                    exportCount = exportCount,
                    importCount = importCount,
                    nameCount = nameCount,
                    totalHeaderSize = totalHeaderSize
                )
            )
        } catch (e: Exception) {
            ParseResult(success = false, error = "Parse error: ${e.message}")
        }
    }

    // ============================================================
    // NameTable & ImportTable parsing
    // ============================================================

    /**
     * Parse NameTable at the given absolute offset
     */
    private fun parseNameTable(
        reader: BinaryReader,
        nameOffset: Int,
        nameCount: Int
    ): List<String> {
        val names = mutableListOf<String>()
        reader.position(nameOffset)

        repeat(nameCount) {
            val name = reader.readFString()
            reader.readUInt16()  // NonCasePreservingHash
            reader.readUInt16()  // CasePreservingHash
            names.add(name)
        }

        return names
    }

    /**
     * Parse ImportTable at the given absolute offset
     */
    private fun parseImportTable(
        reader: BinaryReader,
        importOffset: Int,
        importCount: Int,
        nameTable: List<String>
    ): List<ImportEntry> {
        if (importCount <= 0 || nameTable.isEmpty()) return emptyList()

        val entries = mutableListOf<ImportEntry>()
        reader.position(importOffset)

        repeat(importCount) {
            // FObjectImport: 32 bytes
            // FName ClassPackage (8 bytes)
            val cpIdx = reader.readInt32()
            reader.readInt32()  // number
            // FName ClassName (8 bytes)
            val cnIdx = reader.readInt32()
            reader.readInt32()  // number
            // FPackageIndex OuterIndex (4 bytes)
            reader.readInt32()
            // FName ObjectName (8 bytes)
            val onIdx = reader.readInt32()
            reader.readInt32()  // number
            // FName PackageName (8 bytes)
            val pnIdx = reader.readInt32()
            reader.readInt32()  // number
            // bImportOptional (4 bytes)
            reader.readInt32()

            val className = nameTable.getOrNull(cnIdx) ?: ""
            val objectName = nameTable.getOrNull(onIdx) ?: ""

            entries.add(ImportEntry(className = className, objectName = objectName))
        }

        return entries
    }

    // ============================================================
    // Asset type detection
    // ============================================================

    private fun determineAssetType(name: String, imports: List<ImportEntry>): AssetType {
        // 1. Try prefix-based detection
        val prefixMatch = AssetType.fromName(name)
        if (prefixMatch != AssetType.UNKNOWN) return prefixMatch

        // 2. Try import class name detection
        val classNames = imports.map { it.className }
        return when {
            classNames.contains("StaticMesh") -> AssetType.STATIC_MESH
            classNames.contains("SkeletalMesh") -> AssetType.SKELETAL_MESH
            classNames.contains("Material") && !classNames.contains("MaterialInstanceConstant") -> AssetType.MATERIAL
            classNames.contains("MaterialInstanceConstant") -> AssetType.MATERIAL_INSTANCE
            classNames.contains("MaterialFunction") -> AssetType.MATERIAL_FUNCTION
            classNames.contains("Texture2D") -> AssetType.TEXTURE
            classNames.contains("SoundWave") || classNames.contains("SoundCue") -> AssetType.SOUND
            classNames.contains("AnimSequence") -> AssetType.ANIMATION
            classNames.contains("Blueprint") || classNames.contains("WidgetBlueprint") -> AssetType.BLUEPRINT
            classNames.contains("ParticleSystem") -> AssetType.PARTICLE_SYSTEM
            classNames.contains("UserDefinedEnum") -> AssetType.ENUM
            classNames.contains("UserDefinedStruct") -> AssetType.STRUCT
            classNames.contains("DataTable") -> AssetType.DATA_TABLE
            else -> AssetType.UNKNOWN
        }
    }

    // ============================================================
    // Binary Reader (standalone, not tied to UEProjectParser)
    // ============================================================

    private class BinaryReader(private val buffer: ByteBuffer) {
        fun readInt32(): Int = buffer.int
        fun readUInt32(): Long = buffer.int.toLong() and 0xFFFFFFFFL
        fun readUInt16(): Int = buffer.short.toInt() and 0xFFFF
        fun position(): Int = buffer.position()
        fun position(newPos: Int) {
            buffer.position(newPos)
        }

        fun skipBytes(count: Int) {
            buffer.position(buffer.position() + count)
        }

        fun readFString(): String {
            val length = readInt32()
            if (length == 0) return ""

            if (length > 0) {
                // ASCII/Latin-1
                val bytes = ByteArray(length)
                buffer.get(bytes)
                return String(bytes, 0, length - 1, Charsets.ISO_8859_1)  // -1 for null terminator
            } else {
                // UTF-16LE
                val charCount = -length
                val bytes = ByteArray(charCount * 2)
                buffer.get(bytes)
                return String(
                    bytes,
                    0,
                    charCount * 2 - 2,
                    Charsets.UTF_16LE
                )  // -2 for null terminator
            }
        }
    }
}
