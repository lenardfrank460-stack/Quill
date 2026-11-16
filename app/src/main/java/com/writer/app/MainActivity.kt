package com.writer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                QuillApp()
            }
        }
    }
}

@Composable
fun QuillApp() {
    val context = LocalContext.current
    val store = remember { Store(context) }

    var books by remember { mutableStateOf(store.books) }
    var selectedChapter by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var newBookName by remember { mutableStateOf("") }

    val prefs = remember { Prefs(context) }

    fun refresh() {
        books = store.books
    }

    MaterialTheme {
        if (selectedChapter != null) {
            EditorScreen(
                store = store,
                chapterId = selectedChapter!!,
                fontSize = prefs.fontSize,
                onBack = {
                    selectedChapter = null
                    refresh()
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Quill", fontSize = 28.sp)
                    TextButton(onClick = { showSettings = true }) {
                        Text("设置")
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = newBookName,
                    onValueChange = { newBookName = it },
                    label = { Text("新书名称") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (newBookName.isNotBlank()) {
                            store.addBook(newBookName.trim())
                            newBookName = ""
                            refresh()
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("创建新书")
                }

                Spacer(Modifier.height(16.dp))

                LazyColumn {
                    items(books) { book ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(book.title, fontSize = 20.sp)

                                book.chapters.forEach { chapter ->
                                    TextButton(
                                        onClick = {
                                            selectedChapter = chapter.id
                                        }
                                    ) {
                                        Text(chapter.title)
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        store.addChapter(book.id, "新章节")
                                        refresh()
                                    }
                                ) {
                                    Text("＋ 添加章节")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("设置") },
            text = {
                Column {
                    Text("字号：${prefs.fontSize.toInt()}")
                    Slider(
                        value = prefs.fontSize,
                        onValueChange = { prefs.fontSize = it },
                        valueRange = 14f..30f
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("完成")
                }
            }
        )
    }
}

@Composable
fun EditorScreen(
    store: Store,
    chapterId: String,
    fontSize: Float,
    onBack: () -> Unit
) {
    var content by remember(chapterId) {
        mutableStateOf(store.getText(chapterId))
    }

    BackHandler {
        store.saveText(chapterId, content)
        onBack()
    }

    LaunchedEffect(chapterId, content) {
        delay(800)
        store.saveText(chapterId, content)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                onClick = {
                    store.saveText(chapterId, content)
                    onBack()
                }
            ) {
                Text("返回")
            }

            Text("正在写作")
        }

        Spacer(Modifier.height(12.dp))

        BasicTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier.fillMaxSize(),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.7f).sp
            )
        )
    }
}