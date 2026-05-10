package com.example.ue5analyzer.data.parser

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.ue5analyzer.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * UE5 项目解析器
 * 核心类：负责扫描项目、解析 uasset 文件
 */
class UEProjectParser(private val context: Context) {
    
    // uasset 文件 Magic Number
    companion object {
        private const val UASSET_MAGIC = 0x9E2A83C1
        // 最大读取大小：10MB，超过则跳过 Import Table 解析
        private const val MAX_PARSING_SIZE = 10 * 1024 * 1024L
        
        // 精简的 className 映射表（只保留会出现在 Import Table 中的资源类）
        private val CLASS_NAME_PREFIX_MAP = mapOf(
            // 材质相关
            "MaterialInstanceConstant" to "MI_",
            "Material" to "M_",
            "MaterialFunction" to "MF_",
            "MaterialFunctionInstance" to "MFI_",
            "MaterialParameterCollection" to "MPC_",
            
            // 网格相关
            "StaticMesh" to "SM_",
            "SkeletalMesh" to "SK_",
            "PhysicsAsset" to "PHYS_",
            "Skeleton" to "SKEL_",
            
            // 动画相关
            "AnimSequence" to "A_",
            "AnimMontage" to "AM_",
            "AnimBlueprint" to "ABP_",
            "AnimBlendSpace" to "BS_",
            "BlendSpace" to "BS_",
            "BlendSpace1D" to "BS_",
            "CameraAnim" to "CA_",
            
            // 贴图相关
            "Texture2D" to "T_",
            "TextureCube" to "TC_",
            "RenderTarget" to "RT_",
            "TextureRenderTarget2D" to "RT_",
            "MediaTexture" to "MT_",
            
            // 音频相关
            "SoundCue" to "S_",
            "SoundWave" to "SW_",
            
            // 蓝图相关
            "Blueprint" to "BP_",
            "WidgetBlueprint" to "WBP_",
            
            // 粒子/Niagara
            "ParticleSystem" to "P_",
            "NiagaraSystem" to "NS_",
            "NiagaraEmitter" to "NE_",
            
            // 曲线/数据
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
            
            // 序列/电影
            "LevelSequence" to "LS_",
            "Sequence" to "SEQ_",
            "MovieScene" to "MS_",
            
            // 地形
            "Landscape" to "L_",
            "LandscapeMaterial" to "LM_",
            "FoliageType" to "FT_",
            "FoliageType_Instanced" to "FTI_",
            "FoliageType_Auto" to "FTA_",
            "FoliageType_Object" to "FTO_",
            
            // 物理材质
            "PhysicalMaterial" to "PM_",
            "ChaosPhysicalMaterial" to "CPM_",
            
            // 游戏逻辑
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
            
            // 关卡
            "Level" to "LVL_",
            "World" to "LVL_",
            
            // 灯光
            "PointLight" to "PL_",
            "SpotLight" to "SL_",
            "RectLight" to "RL_",
            "DirectionalLight" to "DL_",
            "SkyLight" to "SKL_",
            
            // 环境
            "VolumetricCloud" to "VC_",
            "ExponentialHeightFog" to "EHF_",
            "SkyAtmosphere" to "SA_",
            "AtmosphericFog" to "ATMF_",
            "HeightFog" to "HF_",
            
            // 反射
            "BoxReflectionCapture" to "BRC_",
            "SphereReflectionCapture" to "SRC_",
            "PlanarReflection" to "PR_",
            
            // 地形/导航
            "NavArea" to "NA_",
            
            // 缓存/几何
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
            
            // Wwise音频
            "AkAudioBank" to "AK_",
            "AkAudioEvent" to "AKE_",
            
            // 虚拟纹理
            "RuntimeVirtualTexture" to "RVT_",
            "VirtualTexture2D" to "VT_",
            
            // 水体
            "WaterBody" to "WB_",
            "WaterBodyRiver" to "WBR_",
            "WaterBodyLake" to "WBL_",
            "WaterBodyOcean" to "WBO_",
            
            // 植被
            "FoliageActor" to "FA_",
            "InstancedFoliageActor" to "IFA_",
            "ProceduralFoliageVolume" to "PFV_",
            
            // HLOD
            "HLODActor" to "HLOD_",
            
            // 环境查询
            "EnvironmentQuery" to "EQ_",
            
            // 行为树
            "BehaviorTree" to "BT_",
            "BlackboardData" to "BB_",
            
            // 状态树
            "StateTree" to "ST_",
            
            // 摄像机
            "CameraComponent" to "CAM_",
            "CineCameraComponent" to "CAM_",
            
            // 体积
            "AudioVolume" to "AV_",
            
            // 后期处理
            "PostProcessVolume" to "PPV_",
            
            // 风
            "WindDirectionalSource" to "WDS_",
            "WindPointSource" to "WPS_",
            
            // Matinee
            "MatineeActor" to "MAT_",
            
            // 向量场
            "VectorField" to "VF_",
            
            // Houdini
            "HoudiniAssetActor" to "H_",
            "HoudiniPDGAssetLink" to "PDG_",
            
            // 忽略的系统资源（映射到 null 表示跳过）
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
     * 扫描 UE5 项目
     * @param projectUri 项目 URI
     * @param onProgress 进度回调：(已扫描数量, 总数, 当前文件名)
     */
    suspend fun scanProject(
        projectUri: Uri, 
        onProgress: (scannedCount: Int, totalCount: Int, currentPath: String) -> Unit = { _, _, _ -> }
    ): ScanResult = withContext(Dispatchers.IO) {
        val assets = mutableListOf<UEAsset>()
        
        // 1. 解析项目名称
        val projectName = getProjectName(projectUri)
        
        // 2. 先快扫计数
        val totalFiles = countAssetFiles(projectUri)
        
        // 3. 扫描 Content 目录
        val contentUri = findContentDirectory(projectUri)
        if (contentUri != null) {
            scanDirectory(contentUri, assets, onProgress, totalFiles)
        }
        
        // 4. 构建依赖关系
        buildDependencies(assets)
        
        // 5. 检测孤立资源（关卡也参与检测）
        detectOrphanAssets(assets)
        
        // 6. 统计
        val assetsByType = assets.groupingBy { it.type }.eachCount()
        val orphanAssets = assets.filter { it.isOrphan }
        val totalSize = assets.sumOf { it.size }
        
        ScanResult(
            projectPath = projectUri.toString(),
            projectName = projectName,
            totalAssets = assets.size,
            totalSize = totalSize,
            assetsByType = assetsByType,
            allAssets = assets.toList(),  // 所有资源
            orphanAssets = orphanAssets
        )
    }
    
    /**
     * 快速扫描统计资源文件数量
     */
    private fun countAssetFiles(projectUri: Uri): Int {
        return try {
            val contentUri = findContentDirectory(projectUri)
            if (contentUri != null) {
                countFilesInDirectory(contentUri)
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 递归统计目录中的资源文件数量
     */
    private fun countFilesInDirectory(directoryUri: Uri): Int {
        var count = 0
        try {
            val docFile = DocumentFile.fromTreeUri(context, directoryUri) ?: return 0
            docFile.listFiles().forEach { file ->
                if (file.isDirectory) {
                    count += countFilesInDirectory(file.uri)
                } else if (file.name?.endsWith(".uasset") == true || file.name?.endsWith(".umap") == true) {
                    count++
                }
            }
        } catch (e: Exception) { }
        return count
    }
    
    /**
     * 获取项目名称
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
     * 查找 Content 目录 - 使用 DocumentFile API
     */
    private fun findContentDirectory(projectUri: Uri): Uri? {
        return try {
            val docFile = DocumentFile.fromTreeUri(context, projectUri) ?: return null
            
            // 遍历子目录找 Content
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
     * 递归扫描目录 - 使用 DocumentFile API + 进度回调
     * @param totalFiles 总文件数（用于进度百分比计算）
     */
    private fun scanDirectory(
        directoryUri: Uri, 
        assets: MutableList<UEAsset>,
        onProgress: (Int, Int, String) -> Unit,
        totalFiles: Int
    ) {
        try {
            val docFile = DocumentFile.fromTreeUri(context, directoryUri) ?: return
            
            docFile.listFiles().forEach { file ->
                if (file.isDirectory) {
                    // 递归扫描子目录
                    scanDirectory(file.uri, assets, onProgress, totalFiles)
                } else if (file.name?.endsWith(".uasset") == true) {
                    // 解析 uasset 文件
                    val asset = parseUasset(file.name!!, file.uri.toString(), file.length(), file.uri)
                    assets.add(asset)
                    
                    // 进度回调
                    onProgress(assets.size, totalFiles, file.name!!)
                } else if (file.name?.endsWith(".umap") == true) {
                    // .umap 文件是关卡资源
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
            // 忽略权限错误或其他异常
        }
    }
    
    /**
     * 解析 uasset 文件（支持二进制解析）
     * 优先尝试解析 Import Table，失败时回退到文件名推断
     */
    private fun parseUasset(name: String, path: String, size: Long, fileUri: Uri? = null): UEAsset {
        val assetName = name.removeSuffix(".uasset")
        val assetType = AssetType.fromName(assetName)
        
        // 尝试二进制解析 Import Table
        val dependencies = try {
            val importEntries = if (fileUri != null) {
                parseImportTableFromFile(fileUri)
            } else {
                parseImportTableFromFile(path)
            }
            if (importEntries.isNotEmpty()) {
                // 从 Import Table 提取依赖关系
                extractDependenciesFromImports(importEntries, assetName)
            } else {
                inferDependencies(assetName, assetType)
            }
        } catch (e: Exception) {
            // 解析失败，回退到文件名推断
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
     * 从文件解析 Import Table（使用直接传入的 Uri）
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
     * 从文件路径解析 Import Table（兼容旧版本）
     * SAF 返回的 path 是 document ID，需要正确转换为可访问的 URI
     */
    private fun parseImportTableFromFile(path: String): List<ImportEntry> {
        val contentResolver = context.contentResolver
        
        // 尝试多种方式构建 URI
        val uris = mutableListOf<Uri?>()
        
        // 方式1: 如果 path 已经是完整的 document URI
        if (path.startsWith("content://")) {
            try {
                uris.add(Uri.parse(path))
            } catch (e: Exception) { }
        }
        
        // 方式2: 尝试作为 tree document ID 构建
        try {
            val cleanPath = path.removePrefix("/tree/").removePrefix("document/")
            uris.add(Uri.parse("content://com.android.externalstorage.documents/document/$cleanPath"))
        } catch (e: Exception) { }
        
        // 方式3: 尝试作为 tree URI
        try {
            if (path.contains("/tree/")) {
                uris.add(Uri.parse(path))
            }
        } catch (e: Exception) { }
        
        // 方式4: 尝试直接使用 path（适用于某些情况）
        try {
            uris.add(Uri.parse(path))
        } catch (e: Exception) { }
        
        // 尝试打开每个 URI
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
                // 权限不足，尝试下一个
                inputStream?.close()
                continue
            } catch (e: Exception) {
                inputStream?.close()
                continue
            }
        }
        
        return emptyList()
    }
    
    /**
     * 计数输入流 - 限制读取字节数，超过上限返回 -1
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
     * 解析二进制 uasset 文件（真正的流式解析，防止OOM）
     * 只读取前10MB数据进行解析，超出部分跳过
     */
    private fun parseBinaryUasset(inputStream: InputStream): List<ImportEntry> {
        return try {
            // 使用计数输入流限制读取大小
            val countingInput = CountingInputStream(inputStream, MAX_PARSING_SIZE)
            val dis = DataInputStream(countingInput)
            
            // 1. 读取并验证 Magic Number (4字节)
            val magic = dis.readInt()
            if (magic.toLong() != UASSET_MAGIC) {
                return emptyList()
            }
            
            // 2. 读取版本信息（5个int32）
            dis.readInt()  // LegacyVersion
            dis.readInt()  // LegacyUE3Version  
            dis.readInt()  // FileVersionUE4
            val fileVersionUE5 = dis.readInt()  // FileVersionUE5
            dis.readInt()  // FileVersionLicenseeUE4
            
            // 3. 跳过 NameTableFlags (4 bytes)
            dis.readInt()
            
            // 4. 跳过 4 个 padding (16 bytes)
            repeat(4) { dis.readInt() }
            
            // 5. 读取 FolderName (51 bytes) - 固定长度字符串
            val folderNameBytes = ByteArray(51)
            dis.readFully(folderNameBytes)
            
            // 6. 尝试从 UE5 标准偏移读取 HeadersSize
            // 在 UE5 中，HeadersSize 通常在偏移 0x5C (92) 左右
            try {
                // 先尝试 HeadersSize 方案
                val headersSize = readIntFromStream(dis)
                
                if (headersSize > 0 && headersSize < MAX_PARSING_SIZE.toInt()) {
                    // 使用 HeadersSize 定位 Name Table 和 Import Table
                    val result = tryParseWithHeadersSize(dis, headersSize)
                    if (result.isNotEmpty()) return result
                }
            } catch (e: Exception) {
                // HeadersSize 方式失败，尝试备用方案
            }
            
            // 7. 备用方案：盲搜 Name Table 和 Import Table 位置
            // 将当前流位置重置读取剩余数据（最多10MB）
            return searchAndParseImportTable(dis)
            
        } catch (e: OutOfMemoryError) {
            // OOM时优雅降级，返回空列表
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 从流中读取一个 int32（不移动流位置）
     */
    private fun readIntFromStream(dis: DataInputStream): Int {
        val bytes = ByteArray(4)
        dis.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).int
    }
    
    /**
     * 使用 HeadersSize 定位并解析 Name/Import Table
     */
    private fun tryParseWithHeadersSize(dis: DataInputStream, headersSize: Int): List<ImportEntry> {
        try {
            // 在 UE5 文件中，Name Table 和 Import Table 的 count/offset 通常在 HeadersSize 附近
            // 跳过到 HeadersSize 位置
            val currentPos = 4 + 5 * 4 + 4 + 16 + 51 + 4  // Magic + 5versions + NameTableFlags + 4padding + FolderName + int32
            val skipBytes = headersSize - currentPos
            if (skipBytes > 0) {
                dis.skipBytes(skipBytes.toInt())
            } else if (skipBytes < 0) {
                return emptyList()
            }
            
            // 读取 Name Table count 和 offset
            val nameCount = dis.readInt()
            val nameOffset = dis.readInt()
            
            // 验证 nameCount
            if (nameCount <= 0 || nameCount >= 100000) {
                return emptyList()
            }
            
            // 读取 Import Table count 和 offset
            val importCount = dis.readInt()
            val importOffset = dis.readInt()
            
            // 验证 importCount
            if (importCount < 0 || importCount >= 50000) {
                return emptyList()
            }
            
            // 验证 offset
            if (nameOffset <= 0 || nameOffset >= MAX_PARSING_SIZE.toInt() || 
                importOffset <= 0 || importOffset >= MAX_PARSING_SIZE.toInt()) {
                return emptyList()
            }
            
            // 跳转到 Name Table 位置
            val nameTableStart = headersSize + nameOffset
            dis.skipBytes((nameTableStart - dis.available()).toInt().coerceAtLeast(0))
            
            // 解析 Name Table
            val nameTable = mutableListOf<String>()
            for (i in 0 until nameCount) {
                try {
                    val stringLength = dis.readInt()
                    if (stringLength <= 0 || stringLength > 512) break
                    
                    val stringBytes = ByteArray(stringLength)
                    dis.readFully(stringBytes)
                    val name = String(stringBytes, Charsets.UTF_8)
                    
                    // 跳过零终止符(1字节) + NonPIUFlags(2字节) = 3字节
                    dis.skipBytes(3)
                    
                    nameTable.add(name)
                } catch (e: Exception) {
                    break
                }
            }
            
            // 跳转到 Import Table 位置
            val importTableStart = headersSize + importOffset
            dis.skipBytes((importTableStart - dis.available()).toInt().coerceAtLeast(0))
            
            // 解析 Import Table
            val importEntries = mutableListOf<ImportEntry>()
            for (i in 0 until importCount) {
                try {
                    val classPackageIndex = dis.readInt()
                    val classNameIndex = dis.readInt()
                    val outerIndex = dis.readInt()
                    val objectNameIndex = dis.readInt()
                    val extra1 = dis.readInt()
                    val extra2 = dis.readInt()
                    
                    // UE5 Name Table 索引通常从 1 开始（0 有特殊含义），所以实际访问需要减 1
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
     * 搜索并解析 Import Table（流式备用方案）
     * 由于流已经被部分读取，这里需要将剩余数据读入小buffer进行搜索
     * 限制读取量到前 1MB，因为 Name Table 和 Import Table 的头部信息通常在前几KB范围内
     */
    private fun searchAndParseImportTable(dis: DataInputStream): List<ImportEntry> {
        // 只读取前 1MB 用于搜索（头部信息通常在前几KB）
        val searchSize = minOf(dis.available(), 1024 * 1024)
        if (searchSize < 64) return emptyList()
        
        val bytes = ByteArray(searchSize)
        try {
            dis.readFully(bytes)
        } catch (e: Exception) {
            return emptyList()
        }
        
        val searchRanges = listOf(
            0x5C to 0x100,  // 常见 HeadersSize 范围
            0x70 to 0x120,   // 备用范围
            0x60 to 0x140    // 更宽的范围
        )
        
        for ((start, end) in searchRanges) {
            if (end > bytes.size) continue
            
            try {
                val testBuffer = ByteBuffer.wrap(bytes, start, end - start).order(ByteOrder.BIG_ENDIAN)
                
                // 尝试读取 name count 和 offset
                val nameCount = testBuffer.int
                val nameOffset = testBuffer.int
                
                // 验证 nameCount
                if (nameCount > 0 && nameCount < 100000) {
                    // 尝试读取 import count 和 offset
                    val importCount = testBuffer.int
                    val importOffset = testBuffer.int
                    
                    // 验证 importCount 和 offset
                    if (importCount >= 0 && importCount < 50000 && 
                        nameOffset > 0 && nameOffset < bytes.size &&
                        importOffset > 0 && importOffset < bytes.size) {
                        
                        // 找到有效的头部，解析 Name Table
                        val nameTable = parseNameTable(bytes, nameOffset.toLong(), nameCount)
                        
                        // 解析 Import Table
                        return parseImportTable(bytes, importOffset.toLong(), importCount, nameTable)
                    }
                }
            } catch (e: Exception) {
                // 继续尝试下一个范围
            }
        }
        
        // 最后尝试：在文件开头范围内搜索
        return tryParseFromHeader(bytes, 0x80)
    }
    
    /**
     * 尝试从给定偏移量解析头部
     */
    private fun tryParseFromHeader(bytes: ByteArray, baseOffset: Int): List<ImportEntry> {
        if (baseOffset + 32 > bytes.size) return emptyList()
        
        val buffer = ByteBuffer.wrap(bytes, baseOffset, bytes.size - baseOffset).order(ByteOrder.BIG_ENDIAN)
        
        try {
            // 跳过一些可能存在的字段
            // 尝试找到 name table
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
            // 解析失败
        }
        
        return emptyList()
    }
    
    /**
     * 解析 Name Table
     * Name Table 条目格式：int32(长度) + UTF-8字符串 + 零终止符 + uint16(非序列化标志)
     */
    private fun parseNameTable(bytes: ByteArray, offset: Long, count: Int): List<String> {
        val result = mutableListOf<String>()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        
        try {
            buffer.position(offset.toInt())
        } catch (e: Exception) {
            return result
        }
        
        for (i in 0 until count) {
            try {
                if (buffer.remaining() < 6) break  // 至少需要 4(长度) + 2(flags)
                
                val stringLength = buffer.int
                if (stringLength <= 0 || stringLength > 512) {
                    // 无效长度，可能是解析位置错误
                    break
                }
                
                if (buffer.remaining() < stringLength + 2) break
                
                val stringBytes = ByteArray(stringLength)
                buffer.get(stringBytes)
                val name = String(stringBytes, Charsets.UTF_8)
                
                // 跳过零终止符(1字节) + NonPIUFlags(2字节) = 3字节
                buffer.position(buffer.position() + 3)
                
                result.add(name)
            } catch (e: Exception) {
                // 解析条目失败，尝试继续下一个
                break
            }
        }
        
        return result
    }
    
    /**
     * 解析 Import Table
     * Import Table 条目格式：6个int32字段
     * ClassPackage, ClassName, OuterIndex, ObjectName + 2个额外字段
     */
    private fun parseImportTable(
        bytes: ByteArray, 
        offset: Long, 
        count: Int, 
        nameTable: List<String>
    ): List<ImportEntry> {
        val result = mutableListOf<ImportEntry>()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        
        try {
            buffer.position(offset.toInt())
        } catch (e: Exception) {
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
                
                // UE5 Name Table 索引通常从 1 开始（0 有特殊含义），所以实际访问需要减 1
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
                // 解析条目失败
                break
            }
        }
        
        return result
    }
    
    /**
     * 从 Import Table 提取依赖关系
     * 使用扩展的映射表匹配资源名称
     */
    private fun extractDependenciesFromImports(
        importEntries: List<ImportEntry>, 
        currentAssetName: String
    ): List<String> {
        val dependencies = mutableSetOf<String>()
        
        for (entry in importEntries) {
            // 跳过系统内置资源和当前资源自身
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
            
            // 先尝试从映射表获取前缀
            val prefix = CLASS_NAME_PREFIX_MAP[entry.className] ?: CLASS_NAME_PREFIX_MAP.entries.find { 
                entry.className.startsWith(it.key) 
            }?.value
            
            if (prefix != null) {
                // 有映射前缀，直接使用
                if (prefix.isNotEmpty()) {
                    dependencies.add(prefix + entry.objectName)
                }
            } else if (entry.objectName.isNotEmpty()) {
                // 映射表没有，尝试直接使用 objectName
                // 检查是否看起来像资源名称（通常有前缀）
                val likelyAsset = entry.objectName
                
                // 如果没有明确前缀但名字较长，尝试匹配
                if (likelyAsset.length > 3 && !likelyAsset.contains(" ")) {
                    dependencies.add(likelyAsset)
                }
            }
        }
        
        return dependencies.toList()
    }
    
    /**
     * 根据文件名和类型推断依赖
     */
    private fun inferDependencies(assetName: String, assetType: AssetType): List<String> {
        // 根据类型添加常见的依赖
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
     * 构建依赖关系（反向引用）
     * references 的语义是"谁引用了我"
     * 如果 A 依赖 B（A.dependencies 包含 B 的 ID），那么 B 的 references 应该包含 A 的 ID
     */
    private fun buildDependencies(assets: MutableList<UEAsset>) {
        val assetMap = assets.associateBy { it.name }
        
        // 构建反向引用关系：被引用者的 ID -> 引用者的 ID 列表
        val referencesMap = mutableMapOf<String, MutableList<String>>()
        
        assets.forEach { asset ->
            asset.dependencies.forEach { depName ->
                // 模糊匹配：先精确，再忽略大小写，再后缀匹配
                val matchedAsset = findMatchingAsset(depName, assetMap)
                if (matchedAsset != null) {
                    // 被依赖的资源（matchedAsset）的 references 应该加上当前资产（asset）的 ID
                    referencesMap.getOrPut(matchedAsset.id) { mutableListOf() }
                        .add(asset.id)
                }
            }
        }
        
        // 更新所有资源的 references
        assets.forEachIndexed { index, asset ->
            val refs = referencesMap[asset.id] ?: emptyList()
            assets[index] = asset.copy(references = refs)
        }
    }
    
    /**
     * 模糊匹配资产名称
     * 依赖名和资源名可能不完全一致，需要多种匹配策略
     */
    private fun findMatchingAsset(depName: String, assetMap: Map<String, UEAsset>): UEAsset? {
        // 1. 精确匹配
        assetMap[depName]?.let { return it }
        
        // 2. 忽略大小写匹配
        assetMap.values.find { it.name.equals(depName, ignoreCase = true) }?.let { return it }
        
        // 3. 后缀匹配：depName 可能是路径形式如 "/Game/Materials/M_Wood"
        val shortName = depName.substringAfterLast("/")
        assetMap[shortName]?.let { return it }
        assetMap.values.find { it.name.equals(shortName, ignoreCase = true) }?.let { return it }
        
        // 4. 去掉 "/Game/" 前缀后匹配
        val gamePath = depName.removePrefix("/Game/")
        val gameName = gamePath.substringAfterLast("/")
        if (gameName != shortName) {
            assetMap[gameName]?.let { return it }
            assetMap.values.find { it.name.equals(gameName, ignoreCase = true) }?.let { return it }
        }
        
        return null
    }
    
    /**
     * 检测孤立资源
     * 所有资源都参与孤立检测，关卡孤立时给 LOW 风险而非 HIGH
     */
    private fun detectOrphanAssets(assets: MutableList<UEAsset>) {
        val referencedIds = assets.flatMap { it.references }.toSet()
        
        assets.forEachIndexed { index, asset ->
            // 所有资源都参与孤立检测，关卡也不例外
            val isOrphan = asset.id !in referencedIds
            
            // 计算孤立风险等级
            val orphanRiskLevel = when {
                isOrphan && asset.type != AssetType.LEVEL -> OrphanRiskLevel.HIGH  // 非关卡孤立=高风险
                isOrphan && asset.type == AssetType.LEVEL -> OrphanRiskLevel.LOW   // 关卡孤立=低风险
                asset.references.size == 1 -> OrphanRiskLevel.LOW                  // 仅1个引用=低风险
                else -> OrphanRiskLevel.NONE                                        // 引用>=2=无风险
            }
            
            assets[index] = asset.copy(isOrphan = isOrphan, orphanRiskLevel = orphanRiskLevel)
        }
    }
}

/**
 * Import Table 条目
 */
data class ImportEntry(
    val className: String,
    val objectName: String
)
