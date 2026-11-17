package com.writer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun WriterApp(store: Store, prefs: Prefs) {
    var selected by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var exportText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val dark = prefs.dark
    val foreground = if (dark) Color(0xFFF0EDE5) else Color(0xFF292720)
    val muted = if (dark) Color(0xFFB9B3A7) else Color(0xFF777267)
    val background = if (dark) Color(0xFF252525) else Color(prefs.bg)

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = background, contentColor = foreground) {
            if (selected != null) {
                val id = selected!!
                BackHandler { selected = null }
                EditorScreen(store, prefs, id, { selected = null }, foreground, muted)
            } else {
                HomeScreen(store, foreground, muted,
                    onOpen = { selected = it },
                    onDialog = { type, id, value -> dialog = type; targetId = id; input = value },
                    onExport = { kind, id -> exportText = store.export(kind, id); dialog = "导出内容" },
                    onBackup = { exportText = store.export("b", ""); dialog = "备份内容" },
                    onRestore = { dialog = "恢复说明" })
            }

            when (dialog) {
                "外观设置" -> AlertDialog(
                    onDismissRequest = { dialog = "" }, title = { Text("外观设置") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("背景主题")
                            listOf("羊皮纸" to 0xFFF5ECCB.toInt(), "纯白" to 0xFFFFFFFF.toInt(), "浅灰" to 0xFFE8E8E8.toInt(), "薄荷" to 0xFFDCEEE4.toInt(), "深色" to 0xFF252525.toInt()).forEach { (name, color) ->
                                Row(Modifier.fillMaxWidth().clickable { prefs.bg = color; prefs.dark = name == "深色"; prefs.save() }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(24.dp).background(Color(color), RoundedCornerShape(5.dp)))
                                    Spacer(Modifier.width(10.dp)); Text(name)
                                }
                            }
                            Text("字号：${prefs.size.toInt()} sp")
                            Slider(value = prefs.size, onValueChange = { prefs.size = it; prefs.save() }, valueRange = 14f..32f)
                        }
                    }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("完成") } })
                "回收站" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("回收站") },
                    text = {
                        Column(Modifier.heightIn(max = 360.dp)) {
                            LazyColumn {
                                items(store.book.volumes.filter { it.deleted }, key = { it.id }) { v ->
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text("卷：${v.title}", Modifier.weight(1f)); TextButton(onClick = { store.restoreVolume(v.id) }) { Text("恢复") } }
                                }
                                items(store.book.volumes.flatMap { it.chapters }.filter { it.deleted }, key = { it.id }) { c ->
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text("章：${c.title}", Modifier.weight(1f)); TextButton(onClick = { store.restoreChapter(c.id) }) { Text("恢复") } }
                                }
                            }
                        }
                    }, confirmButton = { TextButton(onClick = { store.emptyTrash(); dialog = "" }) { Text("清空回收站") } },
                    dismissButton = { TextButton(onClick = { dialog = "" }) { Text("关闭") } })
                "导出内容", "备份内容" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("文本已准备好") },
                    text = { Text("当前版本先将内容保存在应用内部。你可以在下一版使用系统文件选择器导出 TXT 和备份文件。") },
                    confirmButton = { TextButton(onClick = { dialog = ""; exportText = null }) { Text("知道了") } })
                "恢复说明" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("恢复备份") },
                    text = { Text("当前版本暂不支持从外部文件恢复。请先保留原有数据，避免覆盖。") },
                    confirmButton = { TextButton(onClick = { dialog = "" }) { Text("确定") } })
                "新建分卷", "新建章节", "重命名分卷", "重命名章节", "重命名小说" -> AlertDialog(
                    onDismissRequest = { dialog = "" }, title = { Text(dialog) },
                    text = { OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                    confirmButton = { TextButton(onClick = {
                        when (dialog) {
                            "新建分卷" -> store.addVolume(input)
                            "新建章节" -> store.addChapter(targetId, input)
                            "重命名分卷" -> store.renameVolume(targetId, input)
                            "重命名章节" -> store.renameChapter(targetId, input)
                            "重命名小说" -> store.renameBook(input)
                        }
                        dialog = ""
                    }) { Text("保存") } },
                    dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } })
            }
        }
    }
}

@Composable
private fun HomeScreen(
    store: Store, fg: Color, muted: Color,
    onOpen: (String) -> Unit,
    onDialog: (String, String, String) -> Unit,
    onExport: (String, String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    val book = store.book
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("QUILL", fontSize = 12.sp, color = muted, fontWeight = FontWeight.Bold)
                Text(book.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { onDialog("重命名小说", "", book.title) }) { Text("改名") }
            TextButton(onClick = { onDialog("外观设置", "", "") }) { Text("外观") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("今日写作 ${store.today} 分钟", color = muted, fontSize = 12.sp)
            Text("共 ${book.volumes.flatMap { it.chapters }.filter { !it.deleted }.sumOf { it.words }} 字", color = muted, fontSize = 12.sp)
        }
        Divider(Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("目录", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { onDialog("新建分卷", "", "新分卷") }) { Text("＋ 新建分卷") }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(book.volumes.filter { !it.deleted }, key = { it.id }) { volume ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
             
@Composable
private fun EditorScreen(
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

    var field by remember(id) {
        mutableStateOf(TextFieldValue(store.text(id)))
    }

    val undoStack = remember(id) {
        mutableStateListOf<TextFieldValue>()
    }

    val redoStack = remember(id) {
        mutableStateListOf<TextFieldValue>()
    }

    val content = field.text
    val wordCount = content.count { !it.isWhitespace() }
    val lineCount = content.count { it == '\n' } + 1

    LaunchedEffect(id, content) {
        delay(650)
        store.saveText(id, content)
    }

    LaunchedEffect(id) {
        while (true) {
            delay(60_000)
            store.addFocus(id, 1)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 18.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                store.saveText(id, content)
                onBack()
            }) {
                Text("← 返回")
            }

            Text(
                chapter?.title ?: "写作",
                Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )

            TextButton(
                enabled = undoStack.isNotEmpty(),
                onClick = {
                    redoStack.add(field)
                    field = undoStack.removeAt(
                        undoStack.lastIndex
                    )
                }
            ) {
                Text("↶")
            }

            TextButton(
                enabled = redoStack.isNotEmpty(),
                onClick = {
                    undoStack.add(field)
                    field = redoStack.removeAt(
                        redoStack.lastIndex
                    )
                }
            ) {
                Text("↷")
            }

            TextButton(onClick = {
                store.saveText(id, content)
            }) {
                Text("保存")
            }
        }

        Divider()

        BasicTextField(
            value = field,
            onValueChange = { newValue ->
                if (newValue.text != field.text) {
                    undoStack.add(field)

                    if (undoStack.size > 100) {
                        undoStack.removeAt(0)
                    }

                    redoStack.clear()
                }

                field = newValue
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = fg,
                fontSize = prefs.size.sp,
                lineHeight = (prefs.size * 1.8f).sp
            ),
            decorationBox = { inner ->
                Box {
                    if (content.isEmpty()) {
                        Text(
                            "开始写下你的故事……",
                            color = muted.copy(alpha = 0.6f),
                            fontSize = prefs.size.sp
                        )
                    }
                    inner()
                }
            }
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 3.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                "$wordCount 字  ·  $lineCount 行",
                color = muted.copy(alpha = 0.75f),
                fontSize = 9.sp
            )
        }
    }
}

