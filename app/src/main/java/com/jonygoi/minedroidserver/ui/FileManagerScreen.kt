package com.jonygoi.minedroidserver.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jonygoi.minedroidserver.server.ServerInstaller
import com.jonygoi.minedroidserver.server.ServerManager
import com.jonygoi.minedroidserver.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val servers by ServerManager.servers.collectAsState()
    val activeServerId by ServerManager.activeServerId.collectAsState()

    val activeConfig = remember(servers, activeServerId) {
        servers.find { it.id == activeServerId } ?: servers.firstOrNull()
    }

    if (activeConfig == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Nenhum servidor cadastrado. Crie um servidor na tela inicial para explorar seus arquivos.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
        return
    }

    val rootDir = remember(activeConfig.id) {
        ServerInstaller.getServerDirectory(context, activeConfig.id)
    }

    var currentDir by remember(rootDir) { mutableStateOf(rootDir) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val files = remember(currentDir, refreshTrigger) {
        if (!currentDir.exists()) currentDir.mkdirs()
        val list = currentDir.listFiles() ?: emptyArray()
        list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
    }

    var selectedFileForEditing by remember { mutableStateOf<File?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var isSavingFile by remember { mutableStateOf(false) }

    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    // Importador de arquivos externos do celular (ex: plugins .jar baixados no Chrome)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val fileName = getFileNameFromUri(context, uri) ?: "imported_file_${System.currentTimeMillis()}"
                    val destFile = File(currentDir, fileName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Arquivo importado: $fileName", Toast.LENGTH_SHORT).show()
                        refreshTrigger++
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao importar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val relativePath = remember(currentDir, rootDir) {
        val rootPath = rootDir.absolutePath
        val curPath = currentDir.absolutePath
        if (curPath == rootPath) "/" else curPath.removePrefix(rootPath)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinecraftObsidian)
    ) {
        // Top Bar
        Surface(
            color = MinecraftDarkSurface,
            border = BorderStroke(1.dp, MinecraftBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Voltar",
                                tint = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Explorador de Diretórios",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${activeConfig.name} • $relativePath",
                            fontSize = 12.sp,
                            color = MinecraftEmerald,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Botão Importar Arquivo / Plugin
                        IconButton(
                            onClick = { importLauncher.launch("*/*") },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Importar Arquivo", tint = MinecraftEmerald)
                        }

                        // Botão Nova Pasta
                        IconButton(
                            onClick = {
                                newFolderName = ""
                                showNewFolderDialog = true
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "Nova Pasta", tint = MinecraftDiamond)
                        }

                        // Botão Novo Arquivo
                        IconButton(
                            onClick = {
                                newFileName = ""
                                showNewFileDialog = true
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "Novo Arquivo", tint = MinecraftLapis)
                        }

                        // Atualizar
                        IconButton(
                            onClick = { refreshTrigger++ },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = TextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Atalhos Rápidos
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentDir == rootDir,
                        onClick = { currentDir = rootDir },
                        label = { Text("Raiz /", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MinecraftCardSurface,
                            selectedLabelColor = MinecraftEmerald
                        )
                    )

                    val pluginsDir = File(rootDir, "plugins")
                    FilterChip(
                        selected = currentDir == pluginsDir,
                        onClick = {
                            if (!pluginsDir.exists()) pluginsDir.mkdirs()
                            currentDir = pluginsDir
                        },
                        label = { Text("plugins/", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MinecraftCardSurface,
                            selectedLabelColor = MinecraftEmerald
                        )
                    )

                    val worldDir = File(rootDir, activeConfig.levelName.ifBlank { "world" })
                    if (worldDir.exists()) {
                        FilterChip(
                            selected = currentDir == worldDir,
                            onClick = { currentDir = worldDir },
                            label = { Text("${worldDir.name}/", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MinecraftCardSurface,
                                selectedLabelColor = MinecraftEmerald
                            )
                        )
                    }

                    val serverProps = File(rootDir, "server.properties")
                    if (serverProps.exists()) {
                        SuggestionChip(
                            onClick = {
                                selectedFileForEditing = serverProps
                                fileContent = serverProps.readText()
                            },
                            label = { Text("server.properties", fontSize = 11.sp) },
                            icon = { Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(14.dp), tint = MinecraftGold) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MinecraftDarkSurface,
                                labelColor = MinecraftGold
                            )
                        )
                    }
                }
            }
        }

        // Navegação para cima (Voltar)
        if (currentDir != rootDir) {
            Surface(
                color = MinecraftCardSurface,
                border = BorderStroke(1.dp, MinecraftBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val parent = currentDir.parentFile
                        if (parent != null && parent.absolutePath.startsWith(rootDir.absolutePath)) {
                            currentDir = parent
                        } else {
                            currentDir = rootDir
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = MinecraftEmerald, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = ".. (Voltar para pasta anterior)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinecraftEmerald
                    )
                }
            }
        }

        // Lista de Arquivos e Pastas
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Pasta vazia", fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Text(text = "Nenhum arquivo ou subpasta encontrada aqui.", fontSize = 11.sp, color = TextMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(files, key = { it.absolutePath }) { file ->
                    FileItemRow(
                        file = file,
                        onFolderClick = { currentDir = file },
                        onFileClick = {
                            if (isEditableTextFile(file)) {
                                selectedFileForEditing = file
                                fileContent = try {
                                    file.readText()
                                } catch (e: Exception) {
                                    "Erro ao ler arquivo: ${e.message}"
                                }
                            } else {
                                Toast.makeText(context, "Arquivo binário: ${file.name}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDeleteClick = { fileToDelete = file }
                    )
                }
            }
        }
    }

    // Modal: Editor de Texto Integrado
    selectedFileForEditing?.let { file ->
        Dialog(
            onDismissRequest = { selectedFileForEditing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(16.dp),
                color = MinecraftDarkSurface,
                border = BorderStroke(1.dp, MinecraftBorder)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = "Tamanho: ${formatFileSize(file.length())}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        IconButton(onClick = { selectedFileForEditing = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Text Field Monospace
                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MinecraftEmerald,
                            unfocusedBorderColor = MinecraftBorder,
                            focusedContainerColor = MinecraftObsidian,
                            unfocusedContainerColor = MinecraftObsidian
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Ações do Editor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedFileForEditing = null },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MinecraftBorder)
                        ) {
                            Text("CANCELAR", color = TextSecondary, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                isSavingFile = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        file.writeText(fileContent)
                                        withContext(Dispatchers.Main) {
                                            isSavingFile = false
                                            Toast.makeText(context, "Arquivo salvo com sucesso!", Toast.LENGTH_SHORT).show()
                                            selectedFileForEditing = null
                                            refreshTrigger++
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isSavingFile = false
                                            Toast.makeText(context, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isSavingFile,
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isSavingFile) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MinecraftObsidian, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, tint = MinecraftObsidian)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SALVAR", color = MinecraftObsidian, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Excluir Arquivo / Pasta
    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Excluir ${if (file.isDirectory) "Pasta" else "Arquivo"}") },
            text = { Text("Tem certeza que deseja apagar '${file.name}'? Esta ação não pode ser desfeita.") },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            if (file.isDirectory) {
                                file.deleteRecursively()
                            } else {
                                file.delete()
                            }
                            Toast.makeText(context, "Excluído com sucesso!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Erro ao excluir: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        fileToDelete = null
                        refreshTrigger++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftRedstone)
                ) {
                    Text("Excluir", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancelar")
                }
            },
            containerColor = MinecraftDarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Modal: Criar Nova Pasta
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Criar Nova Pasta") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Nome da Pasta") },
                    placeholder = { Text("Ex: plugins, mods, backups") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MinecraftEmerald,
                        unfocusedBorderColor = MinecraftBorder
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFolderName.trim()
                        if (name.isNotBlank()) {
                            val newFolder = File(currentDir, name)
                            if (newFolder.mkdirs()) {
                                Toast.makeText(context, "Pasta criada!", Toast.LENGTH_SHORT).show()
                                refreshTrigger++
                            } else {
                                Toast.makeText(context, "Falha ao criar pasta.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showNewFolderDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald)
                ) {
                    Text("Criar", color = MinecraftObsidian, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancelar")
                }
            },
            containerColor = MinecraftDarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Modal: Criar Novo Arquivo
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Criar Novo Arquivo") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("Nome do Arquivo") },
                    placeholder = { Text("Ex: whitelist.json, ops.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MinecraftEmerald,
                        unfocusedBorderColor = MinecraftBorder
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFileName.trim()
                        if (name.isNotBlank()) {
                            val newFile = File(currentDir, name)
                            try {
                                if (newFile.createNewFile()) {
                                    Toast.makeText(context, "Arquivo criado!", Toast.LENGTH_SHORT).show()
                                    refreshTrigger++
                                    // Abre direto no editor
                                    selectedFileForEditing = newFile
                                    fileContent = ""
                                } else {
                                    Toast.makeText(context, "Arquivo já existe ou erro.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showNewFileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald)
                ) {
                    Text("Criar e Editar", color = MinecraftObsidian, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancelar")
                }
            },
            containerColor = MinecraftDarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
private fun FileItemRow(
    file: File,
    onFolderClick: () -> Unit,
    onFileClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isDir = file.isDirectory
    val icon = getFileIcon(file)
    val iconTint = getFileIconTint(file)

    Surface(
        color = MinecraftCardSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MinecraftBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isDir) onFolderClick() else onFileClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = if (isDir) {
                            val count = file.listFiles()?.size ?: 0
                            "$count item(ns)"
                        } else {
                            val sizeStr = formatFileSize(file.length())
                            val dateStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                            "$sizeStr • $dateStr"
                        },
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Excluir",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun isEditableTextFile(file: File): Boolean {
    val name = file.name.lowercase(Locale.ROOT)
    return name.endsWith(".txt") ||
            name.endsWith(".properties") ||
            name.endsWith(".yml") ||
            name.endsWith(".yaml") ||
            name.endsWith(".json") ||
            name.endsWith(".toml") ||
            name.endsWith(".log") ||
            name.endsWith(".md") ||
            name.endsWith(".conf") ||
            name.endsWith(".cfg") ||
            name.endsWith(".env") ||
            name == "eula.txt" ||
            name == "server.properties"
}

private fun getFileIcon(file: File): ImageVector {
    if (file.isDirectory) return Icons.Default.Folder
    val name = file.name.lowercase(Locale.ROOT)
    return when {
        name.endsWith(".jar") -> Icons.Default.Extension
        name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json") -> Icons.Default.Description
        name.endsWith(".log") || name.endsWith(".txt") -> Icons.Default.Article
        name.endsWith(".zip") || name.endsWith(".tar") || name.endsWith(".gz") -> Icons.Default.Archive
        else -> Icons.Default.InsertDriveFile
    }
}

private fun getFileIconTint(file: File): androidx.compose.ui.graphics.Color {
    if (file.isDirectory) return MinecraftGold
    val name = file.name.lowercase(Locale.ROOT)
    return when {
        name.endsWith(".jar") -> MinecraftEmerald
        name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml") -> MinecraftDiamond
        name.endsWith(".json") -> MinecraftLapis
        name.endsWith(".log") -> TextSecondary
        else -> TextMuted
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
