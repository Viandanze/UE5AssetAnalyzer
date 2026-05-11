package com.example.ue5analyzer.ui.screens

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
    
    // 报告导出
    val reportSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        uri?.let { 
            val success = viewModel.exportReport(it)
            viewModelScope.launch {
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
    
    // 协程作用域
    val scope = rememberCoroutineScope()
    
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
        MarkdownContent(
            content = reportContent,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 简单 Markdown 渲染器
 */
@Composable
private fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val lines = content.lines()
    
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
                .padding(16.dp)
                .verticalScroll(scrollState),
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 解析并渲染 Markdown
            val parsedContent = parseMarkdownContent(content)
            
            items(parsedContent) { element ->
                RenderMarkdownElement(element)
            }
        }
    }
}

/**
 * Markdown 元素
 */
sealed class MarkdownElement {
    data class Heading(val level: Int, val text: String) : MarkdownElement()
    data class Paragraph(val text: String) : MarkdownElement()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownElement()
    data class UnorderedList(val items: List<String>) : MarkdownElement()
    data class Blockquote(val text: String) : MarkdownElement()
    data class HorizontalRule(val text: String = "") : MarkdownElement()
}

/**
 * 解析 Markdown 内容为元素列表
 */
private fun parseMarkdownContent(content: String): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = content.lines()
    
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        
        when {
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
            
            // 分隔线
            line.startsWith("---") -> {
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
                i-- // 回退一行，因为 for 循环会再加回来
                
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
            
            // 列表项
            line.startsWith("- ") -> {
                val listItems = mutableListOf<String>()
                while (i < lines.size && lines[i].startsWith("- ")) {
                    listItems.add(lines[i].removePrefix("- "))
                    i++
                }
                i-- // 回退
                elements.add(MarkdownElement.UnorderedList(listItems))
            }
            
            // 段落
            line.isNotBlank() -> {
                elements.add(MarkdownElement.Paragraph(line))
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
    if (lines.size < 2) return null
    
    val headers = lines[0].split("|").map { it.trim() }.filter { it.isNotEmpty() }
    val rows = lines.drop(1).map { row ->
        row.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    }
    
    return if (headers.isNotEmpty()) {
        MarkdownElement.Table(headers, rows)
    } else null
}

/**
 * 渲染单个 Markdown 元素
 */
@Composable
private fun RenderMarkdownElement(element: MarkdownElement) {
    when (element) {
        is MarkdownElement.Heading -> {
            Heading(element.level, element.text)
        }
        is MarkdownElement.Paragraph -> {
            Paragraph(element.text)
        }
        is MarkdownElement.Table -> {
            RenderTable(element.headers, element.rows)
        }
        is MarkdownElement.UnorderedList -> {
            UnorderedList(element.items)
        }
        is MarkdownElement.Blockquote -> {
            Blockquote(element.text)
        }
        is MarkdownElement.HorizontalRule -> {
            Spacer(modifier = Modifier.height(8.dp))
            Divider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Heading(level: Int, text: String) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
        2 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        3 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        else -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    }
    
    val color = when (level) {
        1 -> MaterialTheme.colorScheme.primary
        2 -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Spacer(modifier = Modifier.height(if (level <= 2) 12.dp else 8.dp))
    Text(
        text = parseInlineMarkdown(text),
        style = style,
        color = color
    )
    Spacer(modifier = Modifier.height(if (level <= 2) 8.dp else 4.dp))
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = parseInlineMarkdown(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun RenderTable(headers: List<String>, rows: List<List<String>>) {
    val headerColor = MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 表头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                headers.forEachIndexed { index, header ->
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .border(
                                width = 1.dp,
                                color = borderColor
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = header,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = headerColor
                        )
                    }
                }
            }
            
            // 数据行
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    row.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .border(
                                    width = 1.dp,
                                    color = borderColor
                                )
                                .padding(8.dp)
                        ) {
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun UnorderedList(items: List<String>) {
    Column(modifier = Modifier.padding(start = 16.dp)) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = parseInlineMarkdown(item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun Blockquote(text: String) {
    val borderColor = MaterialTheme.colorScheme.primary
    
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(borderColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 解析行内 Markdown（粗体等）
 */
@Composable
private fun parseInlineMarkdown(text: String) = buildAnnotatedString {
    var currentIndex = 0
    
    while (currentIndex < text.length) {
        // 检查 **粗体**
        val boldStart = text.indexOf("**", currentIndex)
        if (boldStart != -1) {
            val boldEnd = text.indexOf("**", boldStart + 2)
            if (boldEnd != -1) {
                // 添加粗体前的普通文本
                if (boldStart > currentIndex) {
                    append(text.substring(currentIndex, boldStart))
                }
                // 添加粗体文本
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(boldStart + 2, boldEnd))
                }
                currentIndex = boldEnd + 2
                continue
            }
        }
        
        // 没有找到更多格式，直接添加剩余文本
        append(text.substring(currentIndex))
        break
    }
}
