package com.example.ue5analyzer.data.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.ue5analyzer.model.AssetType
import com.example.ue5analyzer.model.OrphanRiskLevel
import com.example.ue5analyzer.model.ScanConfig
import com.example.ue5analyzer.model.ScanResult
import com.example.ue5analyzer.model.UEAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * UE5 Project Parser
 * Core class: Responsible for scanning projects and parsing uasset files
 * TODO: Consider separating serialization logic into a separate Serializer class for better separation of concerns
 * Current design mixes file parsing and business logic. Can be refactored to:
 *   - UassetParser: Responsible for binary parsing
 *   - DependencyResolver: Responsible for building dependency relationships
 *   - OrphanDetector: Responsible for orphan asset detection
 */
class UEProjectParser(private val context: Context) {

    // uasset file Magic Number
    companion object {
        private const val TAG = "UEProjectParser"
        private const val UASSET_MAGIC: Int = -1638187071  // 0x9E2A83C1 as signed Int32

        // Maximum read size: 10MB, skip Import Table parsing if exceeded
        private const val MAX_PARSING_SIZE = 10 * 1024 * 1024L

        // Streamlined className mapping (only keeps resource classes that appear in Import Table)
        private val CLASS_NAME_PREFIX_MAP = mapOf(
            // Material related
            "MaterialInstanceConstant" to "MI_",
            "Material" to "M_",
            "MaterialFunction" to "MF_",
            "MaterialFunctionInstance" to "MFI_",
            "MaterialParameterCollection" to "MPC_",

            // Mesh related
            "StaticMesh" to "SM_",
            "SkeletalMesh" to "SK_",
            "PhysicsAsset" to "PHYS_",
            "Skeleton" to "SKEL_",

            // Animation related
            "AnimSequence" to "A_",
            "AnimMontage" to "AM_",
            "AnimBlueprint" to "ABP_",
            "AnimBlendSpace" to "BS_",
            "BlendSpace" to "BS_",
            "BlendSpace1D" to "BS_",
            "CameraAnim" to "CA_",

            // Texture related
            "Texture2D" to "T_",
            "TextureCube" to "TC_",
            "RenderTarget" to "RT_",
            "TextureRenderTarget2D" to "RT_",
            "MediaTexture" to "MT_",

            // Audio related
            "SoundCue" to "S_",
            "SoundWave" to "SW_",

            // Blueprint related
            "Blueprint" to "BP_",
            "WidgetBlueprint" to "WBP_",

            // Particles/Niagara
            "ParticleSystem" to "P_",
            "NiagaraSystem" to "NS_",
            "NiagaraEmitter" to "NE_",

            // Curve/Data
            "CurveFloat" to "CR_",
            "CurveVector" to "CRV_",
            "CurveLinearColor" to "CRC_",
            "DataTable" to "DT_",
            "UserDefinedEnum" to "E_",
            "UserDefinedStruct" to "F_",

            // UI
            "Font" to "Font_",
            "Widget" to "W_",
            "UserWidget" to "UW_",

            // Sequence/Movie
            "LevelSequence" to "LS_",
            "Sequence" to "SEQ_",
            "MovieScene" to "MS_",

            // Terrain
            "Landscape" to "L_",
            "LandscapeMaterial" to "LM_",
            "FoliageType" to "FT_",
            "FoliageType_Instanced" to "FTI_",
            "FoliageType_Auto" to "FTA_",
            "FoliageType_Object" to "FTO_",

            // Physical material
            "PhysicalMaterial" to "PM_",
            "ChaosPhysicalMaterial" to "CPM_",

            // Game logic
            "GameModeBase" to "GM_",
            "GameStateBase" to "GS_",
            "PlayerController" to "PC_",
            "Character" to "C_",
            "Pawn" to "P_",
            "AIController" to "AIPC_",
            "GameplayAbility" to "GA_",
            "GameplayEffect" to "GE_",
            "AttributeSet" to "AS_",
            "GameplayAbilityBlueprintBase" to "GABP_",
            "GameplayEffectBlueprintBase" to "GEBP_",
            "AttributeSetBlueprintBase" to "ASBP_",
            "DataAsset" to "DA_",
            "PrimaryDataAsset" to "PDA_",

            // Level
            "Level" to "LVL_",
            "World" to "LVL_",

            // Lighting
            "PointLight" to "PL_",
            "SpotLight" to "SL_",
            "RectLight" to "RL_",
            "DirectionalLight" to "DL_",
            "SkyLight" to "SKL_",

            // Environment
            "VolumetricCloud" to "VC_",
            "ExponentialHeightFog" to "EHF_",
            "SkyAtmosphere" to "SA_",
            "AtmosphericFog" to "ATMF_",
            "HeightFog" to "HF_",

            // Reflection
            "BoxReflectionCapture" to "BRC_",
            "SphereReflectionCapture" to "SRC_",
            "PlanarReflection" to "PR_",

            // Terrain/Navigation
            "NavArea" to "NA_",

            // Cache/Geometry
            "GeometryCache" to "GC_",
            "GeometryCollection" to "GC_",
            "FieldSystem" to "FS_",

            // 2D
            "PaperSprite" to "SP_",
            "PaperFlipbook" to "FB_",
            "PaperTileSet" to "TS_",
            "PaperTileMap" to "TM_",
            "PaperSpriteAtlas" to "SPA_",
            "PaperTerrain" to "PT_",
            "PaperTerrainMaterial" to "PTM_",

            // Wwise audio
            "AkAudioBank" to "AK_",
            "AkAudioEvent" to "AKE_",

            // Virtual texture
            "RuntimeVirtualTexture" to "RVT_",
            "VirtualTexture2D" to "VT_",

            // Water
            "WaterBody" to "WB_",
            "WaterBodyRiver" to "WBR_",
            "WaterBodyLake" to "WBL_",
            "WaterBodyOcean" to "WBO_",

            // Foliage
            "FoliageActor" to "FA_",
            "InstancedFoliageActor" to "IFA_",
            "ProceduralFoliageVolume" to "PFV_",

            // HLOD
            "HLODActor" to "HLOD_",

            // Environment query
            "EnvironmentQuery" to "EQ_",

            // Behavior tree
            "BehaviorTree" to "BT_",
            "BlackboardData" to "BB_",

            // State tree
            "StateTree" to "ST_",

            // Camera
            "CameraComponent" to "CAM_",
            "CineCameraComponent" to "CAM_",

            // Volume
            "AudioVolume" to "AV_",

            // Post processing
            "PostProcessVolume" to "PPV_",

            // Wind
            "WindDirectionalSource" to "WDS_",
            "WindPointSource" to "WPS_",

            // Matinee
            "MatineeActor" to "MAT_",

            // Vector field
            "VectorField" to "VF_",

            // Houdini
            "HoudiniAssetActor" to "H_",
            "HoudiniPDGAssetLink" to "PDG_",

            // Ignored system resources (map to null to skip)
            "MapBuildDataRegistry" to null,
            "StreamingMipset" to null,
            "TextureLODSettings" to null,
            "MeshReductionSettings" to null,
            "LightmassSettings" to null,
            "NavigationSystemBase" to null,
            "WorldSettings" to null,
            "DefaultPawn" to null,
            "SpectatorPawn" to null,
            "PlayerState" to null,
            "GameInstance" to null,
            "EngineTypes" to null,
            "ObjectLibrary" to null,
            "AssetManager" to null,
            "LocalPlayer" to null,
            "PlayerCameraManager" to null,
            "PlayerInput" to null,
            "InputComponent" to null,
            "DataRegistry" to null,
            "DataRegistryManager" to null,
            "GameplayTasksComponent" to null,
            "MovieSceneSequencePlayer" to null,
            "LevelMovieSceneSequencePlayer" to null,
            "CinematicViewport" to null,
            "Actor" to null,
            "SceneComponent" to null,
            "PrimitiveComponent" to null
        )
    }

    /**
     * Binary reader for .uasset files using Little-Endian byte order
     */
    private class UassetBinaryReader(private val buffer: ByteBuffer) {

        init {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
        }

        fun readInt32(): Int = buffer.int

        fun readUInt32(): Long = buffer.int.toLong() and 0xFFFFFFFFL

        fun readInt16(): Short = buffer.short

        fun readUInt16(): Int = buffer.short.toInt() and 0xFFFF

        fun readByte(): Int = buffer.get().toInt() and 0xFF

        fun readBytes(count: Int): ByteArray {
            val bytes = ByteArray(count)
            buffer.get(bytes)
            return bytes
        }

        fun position(): Int = buffer.position()

        fun position(newPos: Int) {
            buffer.position(newPos)
        }

        fun remaining(): Int = buffer.remaining()

        fun skipBytes(count: Int) {
            buffer.position(buffer.position() + count)
        }

        /**
         * Read FString from UE5 binary format:
         * - If length == 0: empty string, no data bytes
         * - If length > 0: ASCII/UTF-8, length bytes (includes null terminator)
         * - If length < 0: UTF-16LE, (-length) * 2 bytes (includes null terminator)
         */
        fun readFString(): String {
            val length = buffer.int
            if (length == 0) return ""

            return if (length > 0) {
                // ASCII/UTF-8: length bytes (includes null terminator)
                val bytes = ByteArray(length)
                buffer.get(bytes)
                // Remove null terminator if present
                String(bytes, Charsets.UTF_8).trimEnd('\u0000')
            } else {
                // UTF-16LE: (-length) * 2 bytes (includes null terminator)
                val charCount = -length
                val bytes = ByteArray(charCount * 2)
                buffer.get(bytes)
                // Remove null terminator if present
                String(bytes, Charsets.UTF_16LE).trimEnd('\u0000')
            }
        }

        /**
         * Read FName entry (8 bytes):
         * int32 NameIndex
         * int32 Number
         */
        data class FName(val nameIndex: Int, val number: Int)

        fun readFName(): FName {
            val nameIndex = buffer.int
            val number = buffer.int
            return FName(nameIndex, number)
        }
    }

    /**
     * Header variants based on UE5 version
     */
    private enum class HeaderVariant {
        VARIANT_1_UE5_HIGH,     // LegacyFileVersion=-8, FileVersionUE5 >= 1008
        VARIANT_2_UE5_LOW,      // LegacyFileVersion=-8, FileVersionUE5 < 1008
        VARIANT_3_LEGACY        // LegacyFileVersion=-7
    }

    /**
     * Parsed header data
     */
    private data class UassetHeader(
        val variant: HeaderVariant,
        val totalHeaderSize: Int,
        val nameCount: Int,
        val nameOffset: Int,
        val packageGuid: String?,  // Only present in variant 2 and 3
        val exportCount: Int,
        val exportOffset: Int,
        val importCount: Int,
        val importOffset: Int,
        val dependsOffset: Int
    )

    /**
     * Scan UE5 project
     * @param projectUri Project URI
     * @param onProgress Progress callback: (scanned count, total count, current filename)
     * @param scanConfig Scan config for filtering directories and files
     */
    suspend fun scanProject(
        projectUri: Uri,
        onProgress: (scannedCount: Int, totalCount: Int, currentPath: String) -> Unit = { _, _, _ -> },
        scanConfig: ScanConfig = ScanConfig.DEFAULT
    ): ScanResult = withContext(Dispatchers.IO) {
        val assets = mutableListOf<UEAsset>()

        // 1. Parse project name
        val projectName = getProjectName(projectUri)

        // 2. Quick scan count first (apply config)
        val totalFiles = countAssetFiles(projectUri, scanConfig)

        // 3. Scan Content directory with coroutine context for cancellation check
        val contentUri = findContentDirectory(projectUri)
        if (contentUri != null) {
            scanDirectory(contentUri, assets, onProgress, totalFiles, coroutineContext, scanConfig)
        }

        // 4. Build dependency relationships
        buildDependencies(assets)

        // 5. Detect orphan assets (levels also participate)
        detectOrphanAssets(assets)

        // 6. Statistics
        val assetsByType = assets.groupingBy { it.type }.eachCount()
        val orphanAssets = assets.filter { it.isOrphan }
        val totalSize = assets.sumOf { it.size }

        ScanResult(
            projectPath = projectUri.toString(),
            projectName = projectName,
            totalAssets = assets.size,
            totalSize = totalSize,
            assetsByType = assetsByType,
            allAssets = assets.toList(),  // All assets
            orphanAssets = orphanAssets
        )
    }

    /**
     * Quick scan to count asset files
     * @param scanConfig Scan config
     */
    private fun countAssetFiles(projectUri: Uri, scanConfig: ScanConfig): Int {
        return try {
            val contentUri = findContentDirectory(projectUri)
            if (contentUri != null) {
                countFilesInDirectory(contentUri, scanConfig)
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Recursively count asset files in directory
     * @param scanConfig Scan config
     */
    private fun countFilesInDirectory(directoryUri: Uri, scanConfig: ScanConfig): Int {
        var count = 0
        try {
            val docFile = DocumentFile.fromTreeUri(context, directoryUri) ?: return 0
            docFile.listFiles().forEach { file ->
                if (file.isDirectory) {
                    // Skip ignored directories
                    val dirName = file.name ?: ""
                    if (!scanConfig.ignoredDirectories.contains(dirName)) {
                        count += countFilesInDirectory(file.uri, scanConfig)
                    }
                } else if (file.name?.endsWith(".uasset") == true || file.name?.endsWith(".umap") == true) {
                    // Check file extension
                    val ext = file.name?.substringAfterLast(".") ?: ""
                    if (!scanConfig.ignoredExtensions.contains(ext)) {
                        // Check file size limit
                        if (scanConfig.maxFileSize <= 0 || file.length() <= scanConfig.maxFileSize) {
                            count++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error counting files: ${e.message}")
        }
        return count
    }

    /**
     * Get project name
     */
    private fun getProjectName(projectUri: Uri): String {
        return try {
            val docFile = DocumentFile.fromTreeUri(context, projectUri)
            docFile?.name?.removeSuffix(".uproject") ?: "Unknown Project"
        } catch (e: Exception) {
            "Unknown Project"
        }
    }

    /**
     * Find Content directory - using DocumentFile API
     */
    private fun findContentDirectory(projectUri: Uri): Uri? {
        return try {
            val docFile = DocumentFile.fromTreeUri(context, projectUri) ?: return null

            // Traverse subdirectories to find Content
            docFile.listFiles().forEach { file ->
                if (file.isDirectory && file.name == "Content") {
                    return file.uri
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Recursively scan directory - using DocumentFile API + progress callback
     * @param totalFiles Total file count (for progress percentage calculation)
     * @param coroutineContext Coroutine context for checking cancellation
     * @param scanConfig Scan config
     */
    private suspend fun scanDirectory(
        directoryUri: Uri,
        assets: MutableList<UEAsset>,
        onProgress: (Int, Int, String) -> Unit,
        totalFiles: Int,
        coroutineContext: kotlin.coroutines.CoroutineContext,
        scanConfig: ScanConfig = ScanConfig.DEFAULT
    ) {
        // Check if coroutine is cancelled
        coroutineContext.ensureActive()

        try {
            val docFile = DocumentFile.fromTreeUri(context, directoryUri) ?: return

            docFile.listFiles().forEach { file ->
                // Check cancellation state in loop
                coroutineContext.ensureActive()

                if (file.isDirectory) {
                    // Skip ignored directories
                    val dirName = file.name ?: ""
                    if (!scanConfig.ignoredDirectories.contains(dirName)) {
                        // Recursively scan subdirectories
                        scanDirectory(
                            file.uri,
                            assets,
                            onProgress,
                            totalFiles,
                            coroutineContext,
                            scanConfig
                        )
                    }
                } else if (file.name?.endsWith(".uasset") == true) {
                    // Check file extension
                    val ext = file.name?.substringAfterLast(".") ?: ""
                    if (scanConfig.ignoredExtensions.contains(ext)) return@forEach

                    // Check file size limit
                    if (scanConfig.maxFileSize > 0 && file.length() > scanConfig.maxFileSize) return@forEach

                    // Parse uasset file
                    val asset =
                        parseUasset(file.name!!, file.uri.toString(), file.length(), file.uri)
                    assets.add(asset)

                    // Progress callback
                    onProgress(assets.size, totalFiles, file.name!!)
                } else if (file.name?.endsWith(".umap") == true) {
                    // Check file extension
                    val ext = file.name?.substringAfterLast(".") ?: ""
                    if (scanConfig.ignoredExtensions.contains(ext)) return@forEach

                    // Check file size limit
                    if (scanConfig.maxFileSize > 0 && file.length() > scanConfig.maxFileSize) return@forEach

                    // .umap files are level assets
                    val assetName = file.name!!.removeSuffix(".umap")
                    val asset = UEAsset(
                        id = UUID.randomUUID().toString(),
                        name = assetName,
                        path = file.uri.toString(),
                        type = AssetType.LEVEL,
                        size = file.length(),
                        dependencies = emptyList()
                    )
                    assets.add(asset)
                    onProgress(assets.size, totalFiles, file.name!!)
                }
            }
        } catch (e: Exception) {
            // Log exception but continue scanning
            Log.w(TAG, "Error scanning directory: ${e.message}")
        }
    }

    /**
     * Parse uasset file (supports binary parsing)
     * Prioritize parsing Import Table, fall back to filename inference on failure
     */
    private fun parseUasset(name: String, path: String, size: Long, fileUri: Uri? = null): UEAsset {
        val assetName = name.removeSuffix(".uasset")
        val assetType = AssetType.fromName(assetName)

        // Try binary parsing Import Table
        val dependencies = try {
            val importEntries = if (fileUri != null) {
                parseImportTableFromFile(fileUri)
            } else {
                parseImportTableFromFile(path)
            }
            if (importEntries.isNotEmpty()) {
                // Extract dependencies from Import Table
                extractDependenciesFromImports(importEntries, assetName)
            } else {
                inferDependencies(assetName, assetType)
            }
        } catch (e: Exception) {
            // Parse failed, fall back to filename inference
            Log.w(TAG, "Binary parse failed for $name: ${e.message}")
            inferDependencies(assetName, assetType)
        }

        return UEAsset(
            id = UUID.randomUUID().toString(),
            name = assetName,
            path = path,
            type = assetType,
            size = size,
            dependencies = dependencies
        )
    }

    /**
     * Parse Import Table from file (using directly passed Uri)
     */
    private fun parseImportTableFromFile(uri: Uri): List<ImportEntry> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                parseBinaryUasset(inputStream)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parse Import Table from file path (legacy compatibility)
     * SAF returns path as document ID, need to convert to accessible URI
     */
    private fun parseImportTableFromFile(path: String): List<ImportEntry> {
        val contentResolver = context.contentResolver

        // Try multiple ways to build URI
        val uris = mutableListOf<Uri?>()

        // Method 1: If path is already a complete document URI
        if (path.startsWith("content://")) {
            try {
                uris.add(Uri.parse(path))
            } catch (e: Exception) {
                Log.w(TAG, "URI parse exception: $path, ${e.message}")
            }
        }

        // Method 2: Try as tree document ID
        try {
            val cleanPath = path.removePrefix("/tree/").removePrefix("document/")
            uris.add(Uri.parse("content://com.android.externalstorage.documents/document/$cleanPath"))
        } catch (e: Exception) {
            Log.w(TAG, "Build document URI exception: ${e.message}")
        }

        // Method 3: Try as tree URI
        try {
            if (path.contains("/tree/")) {
                uris.add(Uri.parse(path))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parse tree URI exception: ${e.message}")
        }

        // Method 4: Try using path directly (works in some cases)
        try {
            uris.add(Uri.parse(path))
        } catch (e: Exception) {
            Log.w(TAG, "Direct URI parse exception: ${e.message}")
        }

        // Try opening each URI
        for (uri in uris) {
            if (uri == null) continue

            var inputStream: InputStream? = null
            try {
                inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val result = parseBinaryUasset(inputStream)
                    inputStream.close()
                    if (result.isNotEmpty()) {
                        return result
                    }
                }
            } catch (e: SecurityException) {
                // Permission denied, try next
                inputStream?.close()
            } catch (e: IOException) {
                Log.w(TAG, "IO exception: ${e.message}")
                inputStream?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error reading file: ${e.message}")
                inputStream?.close()
            }
        }

        return emptyList()
    }

    /**
     * Counting input stream - limit read bytes, return -1 if exceeds limit
     */
    private class CountingInputStream(inputStream: InputStream, private val maxBytes: Long) :
        FilterInputStream(inputStream) {
        private var bytesRead = 0L

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
            val bytes = super.read(b, off, toRead)
            if (bytes != -1) bytesRead += bytes
            return bytes
        }
    }

    /**
     * Parse binary uasset file with correct UE5.2+ header format
     * Supports 3 header variants:
     * 1. LegacyFileVersion=-8, FileVersionUE5 >= 1008
     * 2. LegacyFileVersion=-8, FileVersionUE5 < 1008
     * 3. LegacyFileVersion=-7
     */
    private fun parseBinaryUasset(inputStream: InputStream): List<ImportEntry> {
        return try {
            // Read file into buffer (limited to MAX_PARSING_SIZE)
            val countingInput = CountingInputStream(inputStream, MAX_PARSING_SIZE)
            val allBytes = countingInput.readBytes()

            if (allBytes.size < 24) return emptyList()

            val reader = UassetBinaryReader(ByteBuffer.wrap(allBytes))

            // 1. Read and verify Magic Number (4 bytes)
            val magic = reader.readInt32()
            if (magic != UASSET_MAGIC) {
                return emptyList()
            }

            // 2. Read version info
            val legacyFileVersion = reader.readInt32()
            val legacyUE3Version = reader.readInt32()

            // Determine header variant
            val variant = when {
                legacyFileVersion == -7 -> HeaderVariant.VARIANT_3_LEGACY
                legacyFileVersion == -8 -> {
                    val fileVersionUE4 = reader.readInt32()
                    val fileVersionUE5 = reader.readInt32()
                    // Reset position to re-read these values later
                    reader.position(12)
                    if (fileVersionUE5 >= 1008) {
                        HeaderVariant.VARIANT_1_UE5_HIGH
                    } else {
                        HeaderVariant.VARIANT_2_UE5_LOW
                    }
                }

                else -> return emptyList()
            }

            // Reset and parse header based on variant
            reader.position(4) // Back to after magic

            when (variant) {
                HeaderVariant.VARIANT_1_UE5_HIGH -> parseVariant1(reader)
                HeaderVariant.VARIANT_2_UE5_LOW -> parseVariant2(reader)
                HeaderVariant.VARIANT_3_LEGACY -> parseVariant3(reader)
            }

        } catch (e: Exception) {
            Log.w(TAG, "Binary parse exception: ${e.message}")
            emptyList()
        }
    }

    /**
     * Parse Variant 1: LegacyFileVersion=-8, FileVersionUE5 >= 1008
     * Header structure after base fields:
     *   CustomVersions: TArray (Int32 count + 20*count bytes)
     *   TotalHeaderSize: Int32
     *   PackageName: FString
     *   PackageFlags: UInt32
     *   NameCount: Int32
     *   NameOffset: Int32
     *   SoftObjectPathsCount: Int32
     *   SoftObjectPathsOffset: Int32
     *   LocalizationId: FString
     *   GatherableTextDataCount: Int32
     *   GatherableTextDataOffset: Int32
     *   ExportCount: Int32
     *   ExportOffset: Int32
     *   ImportCount: Int32
     *   ImportOffset: Int32
     *   DependsOffset: Int32
     */
    private fun parseVariant1(reader: UassetBinaryReader): List<ImportEntry> {
        try {
            // Variant 1: LegacyFileVersion=-8, FileVersionUE5 >= 1008
            // Header: Tag(4) + LegacyFileVersion(4) + LegacyUE3Version(4) + FileVersionUE4(4)
            //         + FileVersionUE5(4) + FileVersionLicenseeUE4(4)
            //         + CustomVersions + TotalHeaderSize + PackageName + PackageFlags
            //         + NameCount + NameOffset
            //         + SoftObjectPathsCount + SoftObjectPathsOffset
            //         + LocalizationId (FString)
            //         + GatherableTextDataCount + GatherableTextDataOffset
            //         + ExportCount + ExportOffset + ImportCount + ImportOffset + DependsOffset

            // Skip already-read fields: LegacyFileVersion(4) + LegacyUE3Version(4) 
            // + FileVersionUE4(4) + FileVersionUE5(4) + FileVersionLicenseeUE4(4) = 20 bytes
            reader.skipBytes(20)

            // Read CustomVersions TArray (Int32 count + 20*count bytes)
            val customVersionCount = reader.readInt32()
            if (customVersionCount < 0 || customVersionCount > 1000) return emptyList()
            reader.skipBytes(customVersionCount * 20)

            // Read TotalHeaderSize
            val totalHeaderSize = reader.readInt32()
            if (totalHeaderSize <= 0 || totalHeaderSize > MAX_PARSING_SIZE.toInt()) return emptyList()

            // Read PackageName (FString)
            reader.readFString()

            // Read PackageFlags (UInt32)
            reader.skipBytes(4)

            // Read NameCount + NameOffset
            val nameCount = reader.readInt32()
            val nameOffset = reader.readInt32()
            if (nameCount <= 0 || nameCount >= 100000) return emptyList()

            // Read SoftObjectPathsCount + SoftObjectPathsOffset (just skip, data is at offset)
            reader.skipBytes(8)

            // Read LocalizationId (FString)
            reader.readFString()

            // Read GatherableTextDataCount + GatherableTextDataOffset (just skip)
            reader.skipBytes(8)

            // Read ExportCount + ExportOffset
            val exportCount = reader.readInt32()
            val exportOffset = reader.readInt32()

            // Read ImportCount + ImportOffset
            val importCount = reader.readInt32()
            val importOffset = reader.readInt32()
            if (importCount < 0 || importCount >= 50000) return emptyList()

            // Read DependsOffset
            reader.skipBytes(4)

            // Validate offsets
            if (nameOffset <= 0 || importOffset <= 0) return emptyList()

            // Parse NameTable then ImportTable
            val nameTable = parseNameTable(reader, nameOffset, nameCount)
            return parseImportTable(reader, importOffset, importCount, nameTable)

        } catch (e: Exception) {
            Log.w(TAG, "Variant1 parse failed: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Parse Variant 2: LegacyFileVersion=-8, FileVersionUE5 < 1008
     * Header structure after base fields:
     *   PackageGuid: FString (GUID as hex string)
     *   SoftObjectPathsCount: Int32
     *   SoftObjectPathsOffset: Int32
     *   ExportCount: Int32
     *   ExportOffset: Int32
     *   ImportCount: Int32
     *   ImportOffset: Int32
     *   DependsOffset: Int32
     */
    private fun parseVariant2(reader: UassetBinaryReader): List<ImportEntry> {
        try {
            // Skip: LegacyFileVersion(4) + LegacyUE3Version(4) + FileVersionUE4(4)
            //       + FileVersionUE5(4) + FileVersionLicenseeUE4(4) = 20 bytes
            reader.skipBytes(20)

            // Read CustomVersions TArray
            val customVersionCount = reader.readInt32()
            if (customVersionCount < 0 || customVersionCount > 1000) return emptyList()
            reader.skipBytes(customVersionCount * 20)

            // Read TotalHeaderSize
            val totalHeaderSize = reader.readInt32()
            if (totalHeaderSize <= 0 || totalHeaderSize > MAX_PARSING_SIZE.toInt()) return emptyList()

            // Read PackageName (FString)
            reader.readFString()

            // Read PackageFlags (UInt32)
            reader.skipBytes(4)

            // Read NameCount + NameOffset
            val nameCount = reader.readInt32()
            val nameOffset = reader.readInt32()
            if (nameCount <= 0 || nameCount >= 100000) return emptyList()

            // Read PackageGuid (FString) - GUID as hex string
            reader.readFString()

            // Read SoftObjectPathsCount + SoftObjectPathsOffset (just skip)
            reader.skipBytes(8)

            // Read ExportCount + ExportOffset
            val exportCount = reader.readInt32()
            val exportOffset = reader.readInt32()

            // Read ImportCount + ImportOffset
            val importCount = reader.readInt32()
            val importOffset = reader.readInt32()
            if (importCount < 0 || importCount >= 50000) return emptyList()

            // Read DependsOffset
            reader.skipBytes(4)

            // Validate offsets
            if (nameOffset <= 0 || importOffset <= 0) return emptyList()

            // Parse NameTable then ImportTable
            val nameTable = parseNameTable(reader, nameOffset, nameCount)
            return parseImportTable(reader, importOffset, importCount, nameTable)

        } catch (e: Exception) {
            Log.w(TAG, "Variant2 parse failed: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Parse Variant 3: LegacyFileVersion=-7 (legacy format)
     * No FileVersionUE5 field. Header structure after base fields:
     *   PackageGuid: FString (GUID as hex string)
     *   SoftObjectPathsCount: Int32
     *   SoftObjectPathsOffset: Int32
     *   ExportCount: Int32
     *   ExportOffset: Int32
     *   ImportCount: Int32
     *   ImportOffset: Int32
     *   DependsOffset: Int32
     */
    private fun parseVariant3(reader: UassetBinaryReader): List<ImportEntry> {
        try {
            // Skip: LegacyFileVersion(4) + LegacyUE3Version(4) + FileVersionUE4(4)
            //       + FileVersionLicenseeUE4(4) = 16 bytes
            // Note: NO FileVersionUE5 in this variant!
            reader.skipBytes(16)

            // Read CustomVersions TArray
            val customVersionCount = reader.readInt32()
            if (customVersionCount < 0 || customVersionCount > 1000) return emptyList()
            reader.skipBytes(customVersionCount * 20)

            // Read TotalHeaderSize
            val totalHeaderSize = reader.readInt32()
            if (totalHeaderSize <= 0 || totalHeaderSize > MAX_PARSING_SIZE.toInt()) return emptyList()

            // Read PackageName (FString)
            reader.readFString()

            // Read PackageFlags (UInt32)
            reader.skipBytes(4)

            // Read NameCount + NameOffset
            val nameCount = reader.readInt32()
            val nameOffset = reader.readInt32()
            if (nameCount <= 0 || nameCount >= 100000) return emptyList()

            // Read PackageGuid (FString) - GUID as hex string
            reader.readFString()

            // Read SoftObjectPathsCount + SoftObjectPathsOffset (just skip)
            reader.skipBytes(8)

            // Read ExportCount + ExportOffset
            val exportCount = reader.readInt32()
            val exportOffset = reader.readInt32()

            // Read ImportCount + ImportOffset
            val importCount = reader.readInt32()
            val importOffset = reader.readInt32()
            if (importCount < 0 || importCount >= 50000) return emptyList()

            // Read DependsOffset
            reader.skipBytes(4)

            // Validate offsets
            if (nameOffset <= 0 || importOffset <= 0) return emptyList()

            // Parse NameTable then ImportTable
            val nameTable = parseNameTable(reader, nameOffset, nameCount)
            return parseImportTable(reader, importOffset, importCount, nameTable)

        } catch (e: Exception) {
            Log.w(TAG, "Variant3 parse failed: ${e.message}")
            return emptyList()
        }
    }

    private fun parseNameTable(
        reader: UassetBinaryReader,
        nameOffset: Int,
        nameCount: Int
    ): List<String> {
        val result = mutableListOf<String>()

        try {
            // Check bounds
            if (nameOffset > MAX_PARSING_SIZE.toInt()) return result
            reader.position(nameOffset)

            for (i in 0 until nameCount) {
                try {
                    // Check if we have enough bytes for at least the length field
                    if (reader.remaining() < 4) break

                    val length = reader.readInt32()

                    // Empty string case
                    if (length == 0) {
                        // FString with length 0 - still has 2 bytes of hash
                        if (reader.remaining() >= 4) {
                            reader.skipBytes(4) // Skip both hashes
                        }
                        result.add("")
                        continue
                    }

                    // Calculate total bytes needed for this entry
                    val stringBytes = if (length > 0) length else (-length) * 2
                    val totalEntrySize = 4 + stringBytes + 4 // length + string + 2 uint16 hashes

                    if (reader.remaining() < stringBytes + 4) break

                    // Read string
                    val stringData = reader.readBytes(stringBytes)
                    val name = if (length > 0) {
                        String(stringData, Charsets.UTF_8).trimEnd('\u0000')
                    } else {
                        String(stringData, Charsets.UTF_16LE).trimEnd('\u0000')
                    }

                    // Read the two hash values (2 bytes each)
                    reader.skipBytes(4) // uint16 NonCasePreservingHash + uint16 CasePreservingHash

                    result.add(name)
                } catch (e: Exception) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "NameTable parse failed: ${e.message}")
        }

        return result
    }

    /**
     * Parse ImportTable at given offset
     * Entry format (FObjectImport, Runtime = 32 bytes):
     *   FName ClassPackage: 8 bytes (int32 index + int32 number)
     *   FName ClassName: 8 bytes
     *   FPackageIndex OuterIndex: 4 bytes (int32)
     *   FName ObjectName: 8 bytes
     *   FName PackageName: 8 bytes (always present in runtime)
     *   int32 bImportOptional: 4 bytes
     * Total: 40 bytes (editor) or 32 bytes (runtime)
     * 
     * In the test files, 32-byte entries work correctly.
     */
    private fun parseImportTable(
        reader: UassetBinaryReader,
        importOffset: Int,
        importCount: Int,
        nameTable: List<String>
    ): List<ImportEntry> {
        val result = mutableListOf<ImportEntry>()

        try {
            // Check bounds
            if (importOffset > MAX_PARSING_SIZE.toInt()) return result
            reader.position(importOffset)

            for (i in 0 until importCount) {
                try {
                    // Each import entry is 32 bytes (runtime format)
                    if (reader.remaining() < 32) break

                    // Read ClassPackage FName (8 bytes)
                    val classPackageFName = reader.readFName()

                    // Read ClassName FName (8 bytes)
                    val classNameFName = reader.readFName()

                    // Read OuterIndex FPackageIndex (4 bytes)
                    reader.skipBytes(4)

                    // Read ObjectName FName (8 bytes)
                    val objectNameFName = reader.readFName()

                    // Read PackageName FName (8 bytes) - present in runtime format
                    reader.readFName()

                    // Read bImportOptional (4 bytes)
                    reader.skipBytes(4)

                    // Resolve className from nameTable
                    val className =
                        if (classNameFName.nameIndex >= 0 && classNameFName.nameIndex < nameTable.size) {
                            nameTable[classNameFName.nameIndex]
                        } else ""

                    // Resolve objectName from nameTable
                    val objectName =
                        if (objectNameFName.nameIndex >= 0 && objectNameFName.nameIndex < nameTable.size) {
                            nameTable[objectNameFName.nameIndex]
                        } else ""

                    if (objectName.isNotEmpty()) {
                        result.add(ImportEntry(className, objectName))
                    }
                } catch (e: Exception) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ImportTable parse failed: ${e.message}")
        }

        return result
    }

    /**
     * Extract dependencies from Import Table
     * Use extended mapping table to match asset names
     */
    private fun extractDependenciesFromImports(
        importEntries: List<ImportEntry>,
        currentAssetName: String
    ): List<String> {
        val dependencies = mutableSetOf<String>()

        for (entry in importEntries) {
            // Skip system built-in assets and current asset itself
            if (entry.className.contains("/Script/") ||
                entry.className == "MapBuildDataRegistry" ||
                entry.className == "StreamingMipset" ||
                entry.className == "TextureLODSettings" ||
                entry.className == "MeshReductionSettings" ||
                entry.className == "LightmassSettings" ||
                entry.objectName.startsWith("Default__") ||
                entry.objectName == currentAssetName
            ) {
                continue
            }

            // First try to get prefix from mapping table
            val prefix =
                CLASS_NAME_PREFIX_MAP[entry.className] ?: CLASS_NAME_PREFIX_MAP.entries.find {
                    entry.className.startsWith(it.key)
                }?.value

            if (prefix != null) {
                // Has mapping prefix, use directly
                if (prefix.isNotEmpty()) {
                    dependencies.add(prefix + entry.objectName)
                }
            } else if (entry.objectName.isNotEmpty()) {
                // Not in mapping table, try to use objectName directly
                // Check if it looks like an asset name (usually has prefix)
                val likelyAsset = entry.objectName

                // If no clear prefix but long name, try matching
                if (likelyAsset.length > 3 && !likelyAsset.contains(" ")) {
                    dependencies.add(likelyAsset)
                }
            }
        }

        return dependencies.toList()
    }

    /**
     * Infer dependencies from filename and type
     */
    private fun inferDependencies(assetName: String, assetType: AssetType): List<String> {
        // Add common dependencies based on type
        return when (assetType) {
            AssetType.MATERIAL_INSTANCE -> listOf(
                "M_${assetName.removePrefix("MI_")}",
                "T_${assetName.removePrefix("MI_")}"
            )

            AssetType.BLUEPRINT -> listOf(
                "SM_${assetName.removePrefix("BP_")}",
                "M_${assetName.removePrefix("BP_")}"
            )

            else -> emptyList()
        }
    }

    /**
     * Build dependency relationships (reverse references)
     * The semantics of references is "who references me"
     * If A depends on B (A.dependencies contains B's ID), then B's references should contain A's ID
     */
    private fun buildDependencies(assets: MutableList<UEAsset>) {
        val assetMap = assets.associateBy { it.name }

        // Build reverse reference map: referenced asset ID -> list of referencing asset IDs
        val referencesMap = mutableMapOf<String, MutableList<String>>()

        assets.forEach { asset ->
            asset.dependencies.forEach { depName ->
                // Fuzzy match: exact first, then case-insensitive, then suffix match
                val matchedAsset = findMatchingAsset(depName, assetMap)
                if (matchedAsset != null) {
                    // The referenced asset (matchedAsset) should add the current asset's ID to its references
                    referencesMap.getOrPut(matchedAsset.id) { mutableListOf() }
                        .add(asset.id)
                }
            }
        }

        // Update all assets' references
        assets.forEachIndexed { index, asset ->
            val refs = referencesMap[asset.id] ?: emptyList()
            assets[index] = asset.copy(references = refs)
        }
    }

    /**
     * Fuzzy match asset names
     * Dependency names and asset names may not exactly match, requiring multiple matching strategies
     */
    private fun findMatchingAsset(depName: String, assetMap: Map<String, UEAsset>): UEAsset? {
        // 1. Exact match
        assetMap[depName]?.let { return it }

        // 2. Case-insensitive match
        assetMap.values.find { it.name.equals(depName, ignoreCase = true) }?.let { return it }

        // 3. Suffix match: depName may be in path form like "/Game/Materials/M_Wood"
        val shortName = depName.substringAfterLast("/")
        assetMap[shortName]?.let { return it }
        assetMap.values.find { it.name.equals(shortName, ignoreCase = true) }?.let { return it }

        // 4. Match after removing "/Game/" prefix
        val gamePath = depName.removePrefix("/Game/")
        val gameName = gamePath.substringAfterLast("/")
        if (gameName != shortName) {
            assetMap[gameName]?.let { return it }
            assetMap.values.find { it.name.equals(gameName, ignoreCase = true) }?.let { return it }
        }

        return null
    }

    /**
     * Detect orphan assets
     * All assets participate in orphan detection, levels get LOW risk instead of HIGH
     */
    private fun detectOrphanAssets(assets: MutableList<UEAsset>) {
        val referencedIds = assets.flatMap { it.references }.toSet()

        assets.forEachIndexed { index, asset ->
            // All assets participate in orphan detection, including levels
            val isOrphan = asset.id !in referencedIds

            // Calculate orphan risk level
            val orphanRiskLevel = when {
                isOrphan && asset.type != AssetType.LEVEL -> OrphanRiskLevel.HIGH  // Non-level orphan = High risk
                isOrphan && asset.type == AssetType.LEVEL -> OrphanRiskLevel.LOW   // Level orphan = Low risk
                asset.references.size == 1 -> OrphanRiskLevel.LOW                  // Only 1 reference = Low risk
                else -> OrphanRiskLevel.NONE                                        // References >= 2 = No risk
            }

            assets[index] = asset.copy(isOrphan = isOrphan, orphanRiskLevel = orphanRiskLevel)
        }
    }
}

/**
 * Import Table Entry
 */
data class ImportEntry(
    val className: String,
    val objectName: String
)
