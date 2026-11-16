
package com.writer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = Store(this)
        val prefs = Prefs(this)

        setContent {
            WriterApp(store, prefs)
        }
    }
}

@Composable
fun WriterApp(store: Store, prefs: Prefs) {
    var selectedChapter by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf(Pair("b", "")) }

    val bg = Color(prefs.bg)
    val dark = prefs.dark
    val fg = if (dark) Color(0xFFF0EDE5) else Color(0xFF292720)
    val muted = if (dark) Color(0xFFB9B3A7) else Color(0xFF777267)

    val txtExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(
                        store.export(
                            pendingExport.first,
                            pendingExport.second
                        ).toByteArray(Charsets.UTF_8)
                    )
                }
            }
        }
    }

    val zipExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use {
                    store.backup(it)
                }
            }
        }
    }

    val zipImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                contentResolver.openInputStream(uri)?.use {
                    store.restore(it)
                } ?: false
            }.getOrDefault(false)

            dialog = if (result) "恢复成功" else "恢复失败"
        }
    }

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = bg,
            contentColor = fg
        ) {
            val chapterId = selectedChapter

            if (chapterId != null) {
                BackHandler {
                    store.saveText(chapterId, "")
                    selectedChapter = null
                }

                EditorScreen(
                    store = store,
                    prefs = prefs,
                    id = chapterId,
                    onBack = {
                        selectedChapter = null
                    },
                    fg = fg,
                    muted = muted
                )
            } else {
                HomeScreen(
                    store = store,
                    fg = fg,
                    muted = muted,
                    onOpen = { selectedChapter = it },
                    onDialog = { type, id, value ->
                        dialog = type
                        targetId = id
                        input = value
                    },
                    onExport = { kind, id ->
                        pendingExport = kind to id
                        txtExport.launch(
                            store.nameOf(kind, id) + ".txt"
                        )
                    },
                    onBackup = {
                        zipExport.launch("Quill_Backup.zip")
                    },
                    onRestore = {
                        zipImport.launch(arrayOf("application/zip", "application/octet-stream"))
                    }
                )
            }

            if (dialog.isNotEmpty()) {
                when (dialog) {
                    "恢复成功", "恢复失败" -> AlertDialog(
                        onDismissRequest = { dialog = "" },
                        title = { Text(dialog) },
                        confirmButton = {
                            TextButton(onClick = { dialog = "" }) {
                                Text("确定")
                            }
                        }
                    )

                    "主题设置" -> AlertDialog(
                        onDismissRequest = { dialog = "" },
                        title = { Text("外观设置") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("背景主题")
                                val themes = listOf(
                                    "羊皮纸" to 0xFFF5ECCB.toInt(),
                                    "纯白" to 0xFFFFFFFF.toInt(),
                                    "浅灰" to 0xFFE8E8E8.toInt(),
                                    "薄荷" to 0xFFDCEEE4.toInt(),
                                    "夜间" to 0xFF252525.toInt(),
                                    "深蓝" to 0xFF182638.toInt()
                                )
                                themes.forEach { (name, color) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                prefs.bg = color
                                                prefs.save()
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .size(28.dp)
                                                .background(Color(color), RoundedCornerShape(6.dp))
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(name)
                                        Spacer(Modifier.weight(1f))
                                        if (prefs.bg == color) Text("✓")
                                    }
                                }
                                Divider()
                                Text("字号：${prefs.size.toInt()} sp")
                                Slider(
                                    value = prefs.size,
                                    onValueChange = {
                                        prefs.size = it
                                        prefs.save()
                                    },
                                    valueRange = 14f..32f
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { dialog = "" }) {
                                Text("完成")
                            }
                        }
                    )

                    "回收站" -> AlertDialog(
                        onDismissRequest = { dialog = "" },
                        title = { Text("回收站") },
                        text = {
                            Column(
                                modifier = Modifier.heightIn(max = 420.dp)
                            ) {
                                LazyColumn {
                                    store.book.volumes
                                        .filter { it.deleted }
                                        .forEach { v ->
                                            item {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "卷：${v.title}",
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    TextButton(onClick = {
                                                        store.restoreVolume(v.id)
                                                    }) { Text("恢复") }
                                                }
                                            }
                                        }

                                    store.book.volumes
                                        .flatMap { v -> v.chapters }
                                        .filter { it.deleted }
                                        .forEach { c ->
                                            item {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "章：${c.title}",
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    TextButton(onClick = {
                                                        store.restoreChapter(c.id)
                                                    }) { Text("恢复") }
                                                }
                                            }
                                        }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                store.emptyTrash()
                                dialog = ""
                            }) { Text("清空回收站") }
                        },
                        dismissButton = {
                            TextButton(onClick = { dialog = "" }) {
                                Text("关闭")
                            }
                        }
                    )

                    else -> {
                        val title = when (dialog) {
                            "新建分卷" -> "分卷名称"
                            "新建章节" -> "章节名称"
                            "重命名分卷" -> "修改分卷名称"
                            "重命名章节" -> "修改章节名称"
                            "重命名小说" -> "小说名称"
                            else -> dialog
                        }

                        AlertDialog(
                            onDismissRequest = { dialog = "" },
                            title = { Text(title) },
                            text = {
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    when (dialog) {
                                        "新建分卷" -> store.addVolume(input)
                                        "新建章节" -> store.addChapter(targetId, input)
                                        "重命名分卷" -> store.renameVolume(targetId, input)
                                        "重命名章节" -> store.renameChapter(targetId, input)
                                        "重命名小说" -> store.renameBook(input)
                                    }
                                    dialog = ""
                                }) { Text("保存") }
                            },
                            dismissButton = {
                                TextButton(onClick = { dialog = "" }) {
                                    Text("取消")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    store: Store,
    fg: Color,
    muted: Color,
    onOpen: (String) -> Unit,
    onDialog: (String, String, String) -> Unit,
    onExport: (String, String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    val book = store.book

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "QUILL",
                    fontSize = 12.sp,
                    color = muted,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    book.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(onClick = {
                onDialog("重命名小说", "", book.title)
            }) { Text("改名") }

            TextButton(onClick = {
                onDialog("主题设置", "", "")
            }) { Text("外观") }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "今日写作 ${store.today / 60} 分钟",
                color = muted,
                fontSize = 13.sp
            )
            Text(
                "共 ${book.volumes.flatMap { it.chapters }.filter { !it.deleted }.sumOf { it.words }} 字",
                color = muted,
                fontSize = 13.sp
            )
        }

        Divider(Modifier.padding(vertical = 14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "目录",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                onDialog("新建分卷", "", "新分卷")
            }) { Text("+ 新建分卷") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(book.volumes.filter { !it.deleted }, key = { it.id }) { volume ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (volume.collapsed) "▸" else "▾",
                                modifier = Modifier.clickable {
                                    store.toggle(volume.id)
                                },
                                fontSize = 18.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                volume.title,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { store.toggle(volume.id) }
                            )
                            TextButton(onClick = {
                                onDialog("新建章节", volume.id, "新章节")
                            }) { Text("+章节") }
                            TextButton(onClick = {
                                onDialog("重命名分卷", volume.id, volume.title)
                            }) { Text("改名") }
                            TextButton(onClick = {
                                store.moveVolume(volume.id, true)
                            }) { Text("↑") }
                            TextButton(onClick = {
                                store.moveVolume(volume.id, false)
                            }) { Text("↓") }
                        }

                        if (!volume.collapsed) {
                            volume.chapters.filter { !it.deleted }.forEach { chapter ->
                                var menu by remember(chapter.id) {
                                    mutableStateOf(false)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpen(chapter.id) }
                                        .padding(start = 24.dp, top = 7.dp, bottom = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(chapter.title, fontSize = 15.sp)
                                        Text(
                                            "${chapter.words} 字",
                                            fontSize = 11.sp,
                                            color = muted
                                        )
                                    }

                                    TextButton(onClick = {
                                        store.moveChapter(volume.id, chapter.id, true)
                                    }) { Text("↑") }

                                    TextButton(onClick = {
                                        store.moveChapter(volume.id, chapter.id, false)
                                    }) { Text("↓") }

                                    Box {
                                        TextButton(onClick = { menu = true }) {
                                            Text("···")
                                        }
                                        DropdownMenu(
                                            expanded = menu,
                                            onDismissRequest = { menu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("重命名") },
                                                onClick = {
                                                    menu = false
                                                    onDialog("重命名章节", chapter.id, chapter.title)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("导出 TXT") },
                                                onClick = {
                                                    menu = false
                                                    onExport("c", chapter.id)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("删除章节") },
                                                onClick = {
                                                    menu = false
                                                    store.delChapter(chapter.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Divider(Modifier.padding(vertical = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                onDialog("回收站", "", "")
            }) { Text("回收站") }

            TextButton(onClick = {
                onExport("b", "")
            }) { Text("导出全书") }

            TextButton(onClick = onBackup) { Text("备份") }

            TextButton(onClick = onRestore) { Text("恢复") }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun EditorScreen(
    store: Store,
    prefs: Prefs,
    id: String,
    onBack: () -> Unit,
    fg: Color,
    muted: Color
) {
    val chapter = store.book.volumes
        .flatMap { it.chapters }
        .firstOrNull { it.id == id }

    var content by remember(id) {
        mutableStateOf(store.text(id))
    }

    var seconds by remember(id) { mutableLongStateOf(0L) }

    LaunchedEffect(id, content) {
        delay(700)
        store.saveText(id, content)
    }

    LaunchedEffect(id) {
        while (true) {
            delay(1000)
            seconds++
            if (seconds % 60L == 0L) {
                store.addFocus(id, 60)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                store.saveText(id, content)
                onBack()
            }) { Text("← 返回") }

            Text(
                chapter?.title ?: "写作",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            TextButton(onClick = {
                store.saveText(id, content)
            }) { Text("保存") }
        }

        Divider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${content.count { !it.isWhitespace() }} 字",
                color = muted,
                fontSize = 12.sp
            )
            Text(
                "自动保存",
                color = muted,
                fontSize = 12.sp
            )
        }

        BasicTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = fg,
                fontSize = prefs.size.sp,
                lineHeight = (prefs.size * 1.8f).sp
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Default
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (content.isEmpty()) {
                        Text(
                            "开始写下你的故事……",
                            color = muted.copy(alpha = 0.6f),
                            fontSize = prefs.size.sp
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
