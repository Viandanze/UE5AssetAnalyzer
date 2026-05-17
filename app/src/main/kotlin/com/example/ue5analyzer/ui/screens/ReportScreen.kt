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
 * Report Screen - Preview and export reports
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
    
    // Snackbar State
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Coroutine Scope
    val scope = rememberCoroutineScope()
    
    // Report export
    val reportSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        uri?.let { 
            val success = viewModel.exportReport(it)
            scope.launch {
                if (success) {
                    snackbarHostState.showSnackbar(
                        message = "Report exported",
                        duration = SnackbarDuration.Short
                    )
                } else {
                    snackbarHostState.showSnackbar(
                        message = "Export failed",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis Report") },
                actions = {
                    if (currentReport != null) {
                        IconButton(
                            onClick = { 
                                reportSaver.launch("report_${System.currentTimeMillis()}.md") 
                            }
                        ) {
                            Icon(Icons.Default.Download, "Export Report")
                        }
                        IconButton(
                            onClick = {
                                currentReport?.let { report ->
                                    val shareIntent = reportGenerator.shareReport(report)
                                    context.startActivity(shareIntent)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, "Share Report")
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
                            text = if (uiState is UiState.Scanning) "Generating report..." else "No analysis report yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState is UiState.Scanning) "Report will be generated automatically after scanning" else "Please scan a UE5 project to generate an asset analysis report",
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
                            text = "No report available",
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
                            text = "Failed to generate report",
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
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Markdown Format Preview",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        
        Divider()
        
        // Markdown content
        EnhancedMarkdownContent(
            content = reportContent,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Enhanced Markdown content renderer
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
            // Parse and render Markdown
            val parsedContent = parseEnhancedMarkdownContent(content)
            
            items(parsedContent) { element ->
                RenderEnhancedMarkdownElement(element)
            }
        }
    }
}

/**
 * Markdown element - Enhanced version
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
 * Parse Markdown content to element list - Enhanced version
 * Supports: code blocks, nested lists, bold+italic, links
 */
private fun parseEnhancedMarkdownContent(content: String): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = content.lines()
    
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        
        when {
            // Code block start
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
            
            // Heading
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
            
            // Horizontal rule
            line.startsWith("---") || line.startsWith("***") || line.startsWith("___") -> {
                elements.add(MarkdownElement.HorizontalRule())
            }
            
            // Table - detect consecutive | lines
            line.trim().startsWith("|") -> {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    // Skip separator lines (|---|)
                    if (!lines[i].contains("---")) {
                        tableLines.add(lines[i])
                    }
                    i++
                }
                i-- // Go back one line
                
                if (tableLines.size >= 2) {
                    val table = parseTable(tableLines)
                    if (table != null) {
                        elements.add(table)
                    }
                }
            }
            
            // Blockquote
            line.startsWith("> ") -> {
                elements.add(MarkdownElement.Blockquote(line.removePrefix("> ")))
            }
            
            // Nested list - detect indented list items
            line.startsWith("  - ") || line.startsWith("\t- ") -> {
                // Calculate indentation level
                val indent = when {
                    line.startsWith("  - ") -> 2
                    line.startsWith("\t- ") -> 1
                    else -> 0
                }
                val itemText = line.trim().removePrefix("- ")
                val nestedItems = mutableListOf<String>()
                nestedItems.add(itemText)
                
                // Continue collecting same-level nested items
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
            
            // Regular list item
            line.startsWith("- ") -> {
                val listItems = mutableListOf<String>()
                while (i < lines.size && lines[i].startsWith("- ")) {
                    listItems.add(lines[i].removePrefix("- "))
                    i++
                }
                i-- // Go back
                elements.add(MarkdownElement.UnorderedList(listItems))
            }
            
            // Paragraph (text with inline styles)
            line.isNotBlank() -> {
                elements.add(MarkdownElement.Paragraph(line, inlineStyles = true))
            }
            
            else -> {
                // Empty line, skip
            }
        }
        i++
    }
    
    return elements
}

/**
 * Parse table
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
 * Render enhanced Markdown element
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
 * Parse inline styles (bold, italic, links)
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
        
        // Find all style positions
        val matches = mutableListOf<Pair<Int, MatchResult>>()
        
        boldPattern.findAll(text).forEach { matches.add(it.range.first to it) }
        italicPattern.findAll(text).forEach { matches.add(it.range.first to it) }
        linkPattern.findAll(text).forEach { matches.add(it.range.first to it) }
        
        // Sort by position
        matches.sortBy { it.first }
        
        var processedEnd = 0
        matches.forEach { (start, match) ->
            // Add plain text
            if (start > processedEnd) {
                append(text.substring(processedEnd, start))
            }
            
            when {
                // Bold
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
                // Link
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
                // Italic
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
        
        // Add remaining text
        if (processedEnd < text.length) {
            append(text.substring(processedEnd))
        }
    }
}

/**
 * Code block card
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
 * Markdown table
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
            // Table header
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
            
            // Data rows
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
