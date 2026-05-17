package com.example.ue5analyzer.data.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.ue5analyzer.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
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
        private const val UASSET_MAGIC = 0x9E2A83C1
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
            Log.w("UEProjectParser", "Error counting files: ${e.message}")
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
                        scanDirectory(file.uri, assets, onProgress, totalFiles, coroutineContext, scanConfig)
                    }
                } else if (file.name?.endsWith(".uasset") == true) {
                    // Check file extension
                    val ext = file.name?.substringAfterLast(".") ?: ""
                    if (scanConfig.ignoredExtensions.contains(ext)) return@forEach
                    
                    // Check file size limit
                    if (scanConfig.maxFileSize > 0 && file.length() > scanConfig.maxFileSize) return@forEach
                    
                    // Parse uasset file
                    val asset = parseUasset(file.name!!, file.uri.toString(), file.length(), file.uri)
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
            Log.w("UEProjectParser", "Error scanning directory: ${e.message}")
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
                Log.w("UEProjectParser", "URI parse exception: $path, ${e.message}")
            }
        }
        
        // Method 2: Try as tree document ID
        try {
            val cleanPath = path.removePrefix("/tree/").removePrefix("document/")
            uris.add(Uri.parse("content://com.android.externalstorage.documents/document/$cleanPath"))
        } catch (e: Exception) {
            Log.w("UEProjectParser", "Build document URI exception: ${e.message}")
        }
        
        // Method 3: Try as tree URI
        try {
            if (path.contains("/tree/")) {
                uris.add(Uri.parse(path))
            }
        } catch (e: Exception) {
            Log.w("UEProjectParser", "Parse tree URI exception: ${e.message}")
        }
        
        // Method 4: Try using path directly (works in some cases)
        try {
            uris.add(Uri.parse(path))
        } catch (e: Exception) {
            Log.w("UEProjectParser", "Direct URI parse exception: ${e.message}")
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
                Log.w("UEProjectParser", "IO exception: ${e.message}")
                inputStream?.close()
            } catch (e: Exception) {
                Log.w("UEProjectParser", "Error reading file: ${e.message}")
                inputStream?.close()
            }
        }
        
        return emptyList()
    }
    
    /**
     * Counting input stream - limit read bytes, return -1 if exceeds limit
     */
    private class CountingInputStream(inputStream: InputStream, private val maxBytes: Long) : FilterInputStream(inputStream) {
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
     * Parse binary uasset file (true streaming parsing, prevent OOM)
     * Only read first 10MB for parsing, skip rest
     */
    private fun parseBinaryUasset(inputStream: InputStream): List<ImportEntry> {
        return try {
            // Use counting input stream to limit read size
            val countingInput = CountingInputStream(inputStream, MAX_PARSING_SIZE)
            val dis = DataInputStream(countingInput)
            
            // 1. Read and verify Magic Number (4 bytes)
            val magic = dis.readInt()
            if (magic.toLong() != UASSET_MAGIC) {
                return emptyList()
            }
            
            // 2. Read version info (5 int32s)
            dis.readInt()  // LegacyVersion
            dis.readInt()  // LegacyUE3Version  
            dis.readInt()  // FileVersionUE4
            val fileVersionUE5 = dis.readInt()  // FileVersionUE5
            dis.readInt()  // FileVersionLicenseeUE4
            
            // 3. Skip NameTableFlags (4 bytes)
            dis.readInt()
            
            // 4. Skip 4 paddings (16 bytes)
            repeat(4) { dis.readInt() }
            
            // 5. Read FolderName (51 bytes) - fixed length string
            val folderNameBytes = ByteArray(51)
            dis.readFully(folderNameBytes)
            
            // 6. Try reading HeadersSize from UE5 standard offset
            // In UE5, HeadersSize is usually around offset 0x5C (92)
            try {
                // Try HeadersSize method first
                val headersSize = readIntFromStream(dis)
                
                if (headersSize > 0 && headersSize < MAX_PARSING_SIZE.toInt()) {
                    // Use HeadersSize to locate Name Table and Import Table
                    val result = tryParseWithHeadersSize(dis, headersSize)
                    if (result.isNotEmpty()) return result
                }
            } catch (e: Exception) {
                // HeadersSize method failed, try backup plan
            }
            
            // 7. Backup plan: Blind search for Name Table and Import Table positions
            // Reset current stream position to read remaining data (max 10MB)
            return searchAndParseImportTable(dis)
            
        } catch (e: OutOfMemoryError) {
            // Graceful degradation on OOM, return empty list
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Read an int32 from stream (without moving stream position)
     */
    private fun readIntFromStream(dis: DataInputStream): Int {
        val bytes = ByteArray(4)
        dis.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).int
    }
    
    /**
     * Use HeadersSize to locate and parse Name/Import Table
     */
    private fun tryParseWithHeadersSize(dis: DataInputStream, headersSize: Int): List<ImportEntry> {
        try {
            // In UE5 files, Name Table and Import Table count/offset are usually near HeadersSize
            // Skip to HeadersSize position
            val currentPos = 4 + 5 * 4 + 4 + 16 + 51 + 4  // Magic + 5versions + NameTableFlags + 4padding + FolderName + int32
            val skipBytes = headersSize - currentPos
            if (skipBytes > 0) {
                dis.skipBytes(skipBytes.toInt())
            } else if (skipBytes < 0) {
                return emptyList()
            }
            
            // Read Name Table count and offset
            val nameCount = dis.readInt()
            val nameOffset = dis.readInt()
            
            // Validate nameCount
            if (nameCount <= 0 || nameCount >= 100000) {
                return emptyList()
            }
            
            // Read Import Table count and offset
            val importCount = dis.readInt()
            val importOffset = dis.readInt()
            
            // Validate importCount
            if (importCount < 0 || importCount >= 50000) {
                return emptyList()
            }
            
            // Validate offset
            if (nameOffset <= 0 || nameOffset >= MAX_PARSING_SIZE.toInt() || 
                importOffset <= 0 || importOffset >= MAX_PARSING_SIZE.toInt()) {
                return emptyList()
            }
            
            // Jump to Name Table position
            val nameTableStart = headersSize + nameOffset
            dis.skipBytes((nameTableStart - dis.available()).toInt().coerceAtLeast(0))
            
            // Parse Name Table
            val nameTable = mutableListOf<String>()
            for (i in 0 until nameCount) {
                try {
                    val stringLength = dis.readInt()
                    if (stringLength <= 0 || stringLength > 512) break
                    
                    val stringBytes = ByteArray(stringLength)
                    dis.readFully(stringBytes)
                    val name = String(stringBytes, Charsets.UTF_8)
                    
                    // Skip null terminator(1 byte) + NonPIUFlags(2 bytes) = 3 bytes
                    dis.skipBytes(3)
                    
                    nameTable.add(name)
                } catch (e: Exception) {
                    break
                }
            }
            
            // Jump to Import Table position
            val importTableStart = headersSize + importOffset
            dis.skipBytes((importTableStart - dis.available()).toInt().coerceAtLeast(0))
            
            // Parse Import Table
            val importEntries = mutableListOf<ImportEntry>()
            for (i in 0 until importCount) {
                try {
                    val classPackageIndex = dis.readInt()
                    val classNameIndex = dis.readInt()
                    val outerIndex = dis.readInt()
                    val objectNameIndex = dis.readInt()
                    val extra1 = dis.readInt()
                    val extra2 = dis.readInt()
                    
                    // UE5 Name Table indices usually start from 1 (0 has special meaning), so subtract 1 for actual access
                    val className = if (classNameIndex > 0 && classNameIndex - 1 < nameTable.size) {
                        nameTable[classNameIndex - 1]
                    } else ""
                    
                    val objectName = if (objectNameIndex > 0 && objectNameIndex - 1 < nameTable.size) {
                        nameTable[objectNameIndex - 1]
                    } else ""
                    
                    if (objectName.isNotEmpty()) {
                        importEntries.add(ImportEntry(className, objectName))
                    }
                } catch (e: Exception) {
                    break
                }
            }
            
            return importEntries
            
        } catch (e: Exception) {
            return emptyList()
        }
    }
    
    /**
     * Search and parse Import Table (streaming backup plan)
     * Since stream has been partially read, read remaining data into small buffer for searching
     * Limit read to first 1MB, because Name Table and Import Table header info is usually within first few KB
     */
    private fun searchAndParseImportTable(dis: DataInputStream): List<ImportEntry> {
        // Only read first 1MB for searching (header info usually in first few KB)
        val searchSize = minOf(dis.available(), 1024 * 1024)
        if (searchSize < 64) return emptyList()
        
        val bytes = ByteArray(searchSize)
        try {
            dis.readFully(bytes)
        } catch (e: Exception) {
            return emptyList()
        }
        
        val searchRanges = listOf(
            0x5C to 0x100,  // Common HeadersSize ranges
            0x70 to 0x120,   // Backup ranges
            0x60 to 0x140    // Wider range
        )
        
        for ((start, end) in searchRanges) {
            if (end > bytes.size) continue
            
            try {
                val testBuffer = ByteBuffer.wrap(bytes, start, end - start).order(ByteOrder.BIG_ENDIAN)
                
                // Try to read name count and offset
                val nameCount = testBuffer.int
                val nameOffset = testBuffer.int
                
                // Validate nameCount
                if (nameCount > 0 && nameCount < 100000) {
                    // Try to read import count and offset
                    val importCount = testBuffer.int
                    val importOffset = testBuffer.int
                    
                    // Validate importCount and offset
                    if (importCount >= 0 && importCount < 50000 && 
                        nameOffset > 0 && nameOffset < bytes.size &&
                        importOffset > 0 && importOffset < bytes.size) {
                        
                        // Found valid header, parse Name Table
                        val nameTable = parseNameTable(bytes, nameOffset.toLong(), nameCount)
                        
                        // Parse Import Table
                        return parseImportTable(bytes, importOffset.toLong(), importCount, nameTable)
                    }
                }
            } catch (e: Exception) {
                // Continue trying next range
            }
        }
        
        // Final attempt: Search within file header range
        return tryParseFromHeader(bytes, 0x80)
    }
    
    /**
     * Try to parse header from given offset
     */
    private fun tryParseFromHeader(bytes: ByteArray, baseOffset: Int): List<ImportEntry> {
        if (baseOffset + 32 > bytes.size) return emptyList()
        
        val buffer = ByteBuffer.wrap(bytes, baseOffset, bytes.size - baseOffset).order(ByteOrder.BIG_ENDIAN)
        
        try {
            // Skip some potentially existing fields
            // Try to find name table
            val nameCount = buffer.int
            val nameOffset = buffer.int
            
            if (nameCount > 0 && nameCount < 100000 && 
                nameOffset >= baseOffset && nameOffset < bytes.size) {
                
                val importCount = buffer.int
                val importOffset = buffer.int
                
                if (importCount >= 0 && importCount < 50000 &&
                    importOffset >= baseOffset && importOffset < bytes.size) {
                    
                    val nameTable = parseNameTable(bytes, nameOffset.toLong(), nameCount)
                    return parseImportTable(bytes, importOffset.toLong(), importCount, nameTable)
                }
            }
        } catch (e: Exception) {
            // Parse Failed
        }
        
        return emptyList()
    }
    
    /**
     * Parse Name Table
     * Name Table entry format: int32(length) + UTF-8 string + null terminator + uint16(non-serialized flags)
     */
    private fun parseNameTable(bytes: ByteArray, offset: Long, count: Int): List<String> {
        // Boundary check: Prevent integer overflow and out-of-bounds access
        if (offset < 0 || offset > Int.MAX_VALUE || offset > bytes.size) {
            return emptyList()
        }
        
        val result = mutableListOf<String>()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        
        try {
            buffer.position(offset.toInt())
        } catch (e: Exception) {
            Log.w("UEProjectParser", "NameTable position exception: offset=$offset, ${e.message}")
            return result
        }
        
        for (i in 0 until count) {
            try {
                if (buffer.remaining() < 6) break  // Need at least 4(length) + 2(flags)
                
                val stringLength = buffer.int
                if (stringLength <= 0 || stringLength > 512) {
                    // Invalid length, possibly parsing position error
                    break
                }
                
                if (buffer.remaining() < stringLength + 2) break
                
                val stringBytes = ByteArray(stringLength)
                buffer.get(stringBytes)
                val name = String(stringBytes, Charsets.UTF_8)
                
                // Skip null terminator(1 byte) + NonPIUFlags(2 bytes) = 3 bytes
                buffer.position(buffer.position() + 3)
                
                result.add(name)
            } catch (e: Exception) {
                // Failed to parse entry, try to continue with next
                break
            }
        }
        
        return result
    }
    
    /**
     * Parse Import Table
     * Import Table entry format: 6 int32 fields
     * ClassPackage, ClassName, OuterIndex, ObjectName + 2 extra fields
     */
    private fun parseImportTable(
        bytes: ByteArray, 
        offset: Long, 
        count: Int, 
        nameTable: List<String>
    ): List<ImportEntry> {
        // Boundary check: Prevent integer overflow and out-of-bounds access
        if (offset < 0 || offset > Int.MAX_VALUE || offset > bytes.size) {
            return emptyList()
        }
        
        val result = mutableListOf<ImportEntry>()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        
        try {
            buffer.position(offset.toInt())
        } catch (e: Exception) {
            Log.w("UEProjectParser", "ImportTable position exception: offset=$offset, ${e.message}")
            return result
        }
        
        for (i in 0 until count) {
            try {
                if (buffer.remaining() < 24) break  // 6 * 4 bytes
                
                val classPackageIndex = buffer.int
                val classNameIndex = buffer.int
                val outerIndex = buffer.int
                val objectNameIndex = buffer.int
                val extra1 = buffer.int
                val extra2 = buffer.int
                
                // UE5 Name Table indices usually start from 1 (0 has special meaning), so subtract 1 for actual access
                val className = if (classNameIndex > 0 && classNameIndex - 1 < nameTable.size) {
                    nameTable[classNameIndex - 1]
                } else ""
                
                val objectName = if (objectNameIndex > 0 && objectNameIndex - 1 < nameTable.size) {
                    nameTable[objectNameIndex - 1]
                } else ""
                
                if (objectName.isNotEmpty()) {
                    result.add(ImportEntry(className, objectName))
                }
            } catch (e: Exception) {
                // Failed to parse entry
                break
            }
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
                entry.objectName == currentAssetName) {
                continue
            }
            
            // First try to get prefix from mapping table
            val prefix = CLASS_NAME_PREFIX_MAP[entry.className] ?: CLASS_NAME_PREFIX_MAP.entries.find { 
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
