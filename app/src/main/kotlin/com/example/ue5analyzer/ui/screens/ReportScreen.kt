package com.example.ue5analyzer.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ue5analyzer.domain.report.ReportGenerator
import com.example.ue5analyzer.ui.viewmodel.MainViewModel
import com.example.ue5analyzer.ui.viewmodel.UiState
import kotlinx.coroutines.launch

/**
 * 报告页面 - 预览和导出报告
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentReport by viewModel.currentReport.collectAsState()
    val context = LocalContext.current
    
    val reportGenerator = remember { ReportGenerator(context) }
    
    // Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 协程作用域
    val scope = rememberCoroutineScope()
    
    // 报告导出
    val reportSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        uri?.let { 
            val success = viewModel.exportReport(it)
            scope.launch {
                if (success) {
                    snackbarHostState.showSnackbar(
                        message = "报告已导出",
                        duration = SnackbarDuration.Short
                    )
                } else {
                    snackbarHostState.showSnackbar(
                        message = "导出失败",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分析报告") },
                actions = {
                    if (currentReport != null) {
                        IconButton(
                            onClick = { 
                                reportSaver.launch("report_${System.currentTimeMillis()}.md") 
                            }
                        ) {
                            Icon(Icons.Default.Download, "导出报告")
                        }
                        IconButton(
                            onClick = {
                                currentReport?.let { report ->
                                    val shareIntent = reportGenerator.shareReport(report)
                                    context.startActivity(shareIntent)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, "分享报告")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (uiState) {
            is UiState.Idle, is UiState.Scanning -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState is UiState.Scanning) "正在生成报告..." else "暂无分析报告",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState is UiState.Scanning) "扫描完成后将自动生成报告" else "请先扫描 UE5 项目以生成资源分析报告",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            is UiState.Success -> {
                if (currentReport != null) {
                    ReportContent(
                        reportContent = reportGenerator.generateMarkdownReport(currentReport!!),
                        modifier = Modifier.padding(padding)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无报告",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "生成报告失败",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportContent(
    reportContent: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Markdown 格式预览",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        
        Divider()
        
        // Markdown 内容
        EnhancedMarkdownContent(
            content = reportContent,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 增强的 Markdown 内容渲染器
 */
@Composable
private fun EnhancedMarkdownContent(
    content: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 解析并渲染 Markdown
            val parsedContent = parseEnhancedMarkdownContent(content)
            
            items(parsedContent) { element ->
                RenderEnhancedMarkdownElement(element)
            }
        }
    }
}

/**
 * Markdown 元素 - 增强版
 */
sealed class MarkdownElement {
    data class Heading(val level: Int, val text: String) : MarkdownElement()
    data class Paragraph(val text: String, val inlineStyles: Boolean = false) : MarkdownElement()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownElement()
    data class UnorderedList(val items: List<String>) : MarkdownElement()
    data class NestedUnorderedList(val indent: Int, val items: List<String>) : MarkdownElement()
    data class Blockquote(val text: String) : MarkdownElement()
    data class HorizontalRule(val text: String = "") : MarkdownElement()
    data class CodeBlock(val code: String, val language: String = "") : MarkdownElement()
}

/**
 * 解析 Markdown 内容为元素列表 - 增强版
 * 支持：代码块、嵌套列表、加粗+斜体、链接
 */
private fun parseEnhancedMarkdownContent(content: String): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = content.lines()
    
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        
        when {
            // 代码块开始
            line.trim().startsWith("```") -> {
                val language = line.trim().removePrefix("```")
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                elements.add(MarkdownElement.CodeBlock(codeLines.joinToString("\n"), language))
            }
            
            // 标题
            line.startsWith("# ") -> {
                elements.add(MarkdownElement.Heading(1, line.removePrefix("# ")))
            }
            line.startsWith("## ") -> {
                elements.add(MarkdownElement.Heading(2, line.removePrefix("## ")))
            }
            line.startsWith("### ") -> {
                elements.add(MarkdownElement.Heading(3, line.removePrefix("### ")))
            }
            line.startsWith("#### ") -> {
                elements.add(MarkdownElement.Heading(4, line.removePrefix("#### ")))
            }
            
            // 分隔线
            line.startsWith("---") || line.startsWith("***") || line.startsWith("___") -> {
                elements.add(MarkdownElement.HorizontalRule())
            }
            
            // 表格 - 检测连续的 | 行
            line.trim().startsWith("|") -> {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    // 跳过分隔行 (|---|)
                    if (!lines[i].contains("---")) {
                        tableLines.add(lines[i])
                    }
                    i++
                }
                i-- // 回退一行
                
                if (tableLines.size >= 2) {
                    val table = parseTable(tableLines)
                    if (table != null) {
                        elements.add(table)
                    }
                }
            }
            
            // 引用
            line.startsWith("> ") -> {
                elements.add(MarkdownElement.Blockquote(line.removePrefix("> ")))
            }
            
            // 嵌套列表 - 检测缩进的列表项
            line.startsWith("  - ") || line.startsWith("\t- ") -> {
                // 计算缩进级别
                val indent = when {
                    line.startsWith("  - ") -> 2
                    line.startsWith("\t- ") -> 1
                    else -> 0
                }
                val itemText = line.trim().removePrefix("- ")
                val nestedItems = mutableListOf<String>()
                nestedItems.add(itemText)
                
                // 继续收集同级别的嵌套项
                while (i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    val nextIndent = when {
                        nextLine.startsWith("      - ") || nextLine.startsWith("\t\t- ") -> 3
                        nextLine.startsWith("    - ") -> 2
                        nextLine.startsWith("  - ") -> 1
                        nextLine.startsWith("\t- ") -> 1
                        nextLine.startsWith("- ") -> 0
                        nextLine.isBlank() -> -1
                        else -> -2
                    }
                    
                    if (nextIndent == indent || (nextIndent > indent && nextLine.trim().startsWith("- "))) {
                        i++
                        if (nextLine.isNotBlank()) {
                            nestedItems.add(nextLine.trim().removePrefix("- "))
                        }
                    } else {
                        break
                    }
                }
                
                if (nestedItems.isNotEmpty() && nestedItems.any { it.isNotEmpty() }) {
                    elements.add(MarkdownElement.NestedUnorderedList(indent, nestedItems.filter { it.isNotEmpty() }))
                }
            }
            
            // 普通列表项
            line.startsWith("- ") -> {
                val listItems = mutableListOf<String>()
                while (i < lines.size && lines[i].startsWith("- ")) {
                    listItems.add(lines[i].removePrefix("- "))
                    i++
                }
                i-- // 回退
                elements.add(MarkdownElement.UnorderedList(listItems))
            }
            
            // 段落（包含内联样式的文本）
            line.isNotBlank() -> {
                elements.add(MarkdownElement.Paragraph(line, inlineStyles = true))
            }
            
            else -> {
                // 空行，忽略
            }
        }
        i++
    }
    
    return elements
}

/**
 * 解析表格
 */
private fun parseTable(lines: List<String>): MarkdownElement.Table? {
    return try {
        val headers = lines.first().split("|").filter { it.isNotBlank() }.map { it.trim() }
        val rows = lines.drop(1).map { row ->
            row.split("|").filter { it.isNotBlank() }.map { it.trim() }
        }
        MarkdownElement.Table(headers, rows)
    } catch (e: Exception) {
        null
    }
}

/**
 * 渲染增强的 Markdown 元素
 */
@Composable
private fun RenderEnhancedMarkdownElement(element: MarkdownElement) {
    when (element) {
        is MarkdownElement.Heading -> {
            val style = when (element.level) {
                1 -> MaterialTheme.typography.headlineMedium
                2 -> MaterialTheme.typography.headlineSmall
                3 -> MaterialTheme.typography.titleLarge
                4 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleMedium
            }
            
            val fontWeight = when (element.level) {
                1, 2 -> FontWeight.Bold
                else -> FontWeight.SemiBold
            }
            
            Text(
                text = element.text,
                style = style,
                fontWeight = fontWeight,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        is MarkdownElement.Paragraph -> {
            if (element.inlineStyles) {
                Text(
                    text = parseInlineStyles(element.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = element.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        is MarkdownElement.CodeBlock -> {
            CodeBlockCard(code = element.code, language = element.language)
        }
        
        is MarkdownElement.Table -> {
            MarkdownTable(headers = element.headers, rows = element.rows)
        }
        
        is MarkdownElement.UnorderedList -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                element.items.forEach { item ->
                    Row {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseInlineStyles(item),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        
        is MarkdownElement.NestedUnorderedList -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(start = (element.indent * 12).dp)
            ) {
                element.items.forEach { item ->
                    Row {
                        Text(
                            text = "◦",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseInlineStyles(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        
        is MarkdownElement.Blockquote -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = element.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        
        is MarkdownElement.HorizontalRule -> {
            Divider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

/**
 * 解析内联样式（加粗、斜体、链接）
 */
@Composable
private fun parseInlineStyles(text: String): androidx.compose.ui.text.AnnotatedString {
    val context = LocalContext.current
    
    return buildAnnotatedString {
        var currentIndex = 0
        val boldPattern = Regex("\\*\\*(.+?)\\*\\*")
        val italicPattern = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
        val boldItalicPattern = Regex("\\*\\*(.+?)\\*\\*")
        val linkPattern = Regex("\\[([^]]+)]\\(([^)]+)\\)")
        
        // 找到所有样式位置
        val matches = mutableListOf<Pair<Int, MatchResult>>()
        
        boldPattern.findAll(text).forEach { matches.add(it.range.first to it) }
        italicPattern.findAll(text).forEach { matches.add(it.range.first to it) }
        linkPattern.findAll(text).forEach { matches.add(it.range.first to it) }
        
        // 按位置排序
        matches.sortBy { it.first }
        
        var processedEnd = 0
        matches.forEach { (start, match) ->
            // 添加普通文本
            if (start > processedEnd) {
                append(text.substring(processedEnd, start))
            }
            
            when {
                // 加粗
                text.substring(start).startsWith("**") -> {
                    val endIndex = text.indexOf("**", start + 2)
                    if (endIndex > start) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(start + 2, endIndex))
                        }
                        processedEnd = endIndex + 2
                    } else {
                        append(text[start])
                        processedEnd = start + 1
                    }
                }
                // 链接
                match.value.matches(linkPattern) -> {
                    val linkMatch = linkPattern.find(match.value)
                    if (linkMatch != null) {
                        val linkText = linkMatch.groupValues[1]
                        val linkUrl = linkMatch.groupValues[2]
                        withStyle(SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )) {
                            append(linkText)
                        }
                        processedEnd = match.range.last + 1
                    } else {
                        append(text[start])
                        processedEnd = start + 1
                    }
                }
                // 斜体
                text[start] == '*' -> {
                    val endIndex = text.indexOf('*', start + 1)
                    if (endIndex > start && text.getOrNull(endIndex + 1) != '*') {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(start + 1, endIndex))
                        }
                        processedEnd = endIndex + 1
                    } else {
                        append(text[start])
                        processedEnd = start + 1
                    }
                }
                else -> {
                    append(text[start])
                    processedEnd = start + 1
                }
            }
        }
        
        // 添加剩余文本
        if (processedEnd < text.length) {
            append(text.substring(processedEnd))
        }
    }
}

/**
 * 代码块卡片
 */
@Composable
private fun CodeBlockCard(code: String, language: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            if (language.isNotEmpty()) {
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
            )
        }
    }
}

/**
 * Markdown 表格
 */
@Composable
private fun MarkdownTable(headers: List<String>, rows: List<List<String>>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // 表头
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                headers.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            
            // 数据行
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
