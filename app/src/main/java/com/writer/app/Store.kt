package com.writer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

 data class Chapter(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var deleted: Boolean = false,
    var text: String = "",
    var words: Int = 0
)

data class Volume(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var collapsed: Boolean = false,
    var deleted: Boolean = false,
    val chapters: MutableList<Chapter> = mutableListOf()
)

data class Book(
    var title: String = "我的小说",
    val volumes: MutableList<Volume> = mutableListOf()
)

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("quill_store", Context.MODE_PRIVATE)
    private val state = mutableStateOf(loadBook())
    private val revision = mutableIntStateOf(0)
    private val focusState = mutableStateOf(prefs.getInt("today_minutes", 0))
    val book: Book get() { revision.intValue; return state.value }
    val today: Int get() = focusState.value

    init {
        if (book.volumes.isEmpty()) {
            book.volumes.add(Volume(title = "第一卷"))
            persist()
        }
    }

    private fun loadBook(): Book {
        val raw = prefs.getString("book", null) ?: return Book().apply {
            volumes.add(Volume(title = "第一卷"))
        }
        return runCatching {
            val root = JSONObject(raw)
            val result = Book(title = root.optString("title", "我的小说"))
            val volumes = root.optJSONArray("volumes") ?: JSONArray()
            for (i in 0 until volumes.length()) {
                val v = volumes.getJSONObject(i)
                val volume = Volume(
                    id = v.optString("id", UUID.randomUUID().toString()),
                    title = v.optString("title", "未命名分卷"),
                    collapsed = v.optBoolean("collapsed"),
                    deleted = v.optBoolean("deleted")
                )
                val chapters = v.optJSONArray("chapters") ?: JSONArray()
                for (j in 0 until chapters.length()) {
                    val c = chapters.getJSONObject(j)
                    volume.chapters.add(Chapter(
                        id = c.optString("id", UUID.randomUUID().toString()),
                        title = c.optString("title", "未命名章节"),
                        deleted = c.optBoolean("deleted"),
                        text = c.optString("text", ""),
                        words = c.optInt("words")
                    ))
                }
                result.volumes.add(volume)
            }
            result
        }.getOrElse { Book().apply { volumes.add(Volume(title = "第一卷")) } }
    }

    private fun persist() {
        val root = JSONObject().put("title", book.title)
        val volumes = JSONArray()
        book.volumes.forEach { v ->
            val volume = JSONObject()
                .put("id", v.id).put("title", v.title)
                .put("collapsed", v.collapsed).put("deleted", v.deleted)
            val chapters = JSONArray()
            v.chapters.forEach { c ->
                chapters.put(JSONObject().put("id", c.id).put("title", c.title)
                    .put("deleted", c.deleted).put("text", c.text).put("words", c.words))
            }
            volume.put("chapters", chapters)
            volumes.put(volume)
        }
        root.put("volumes", volumes)
        prefs.edit().putString("book", root.toString()).apply()
        state.value = book
        revision.intValue++
    }

    fun text(id: String): String = findChapter(id)?.text ?: ""
    fun getText(id: String): String = text(id)

    fun saveText(id: String, content: String) {
        findChapter(id)?.let { it.text = content; it.words = content.count { ch -> !ch.isWhitespace() } }
        persist()
    }

    fun addVolume(name: String) {
        if (name.isBlank()) return
        book.volumes.add(Volume(title = name.trim())); persist()
    }

    fun addChapter(volumeId: String, name: String) {
        val volume = book.volumes.firstOrNull { it.id == volumeId } ?: return
        if (name.isBlank()) return
        volume.chapters.add(Chapter(title = name.trim())); volume.collapsed = false; persist()
    }

    fun renameBook(name: String) { if (name.isNotBlank()) { book.title = name.trim(); persist() } }
    fun renameVolume(id: String, name: String) { if (name.isNotBlank()) book.volumes.firstOrNull { it.id == id }?.title = name.trim(); persist() }
    fun renameChapter(id: String, name: String) { if (name.isNotBlank()) findChapter(id)?.title = name.trim(); persist() }
    fun toggle(id: String) { book.volumes.firstOrNull { it.id == id }?.let { it.collapsed = !it.collapsed }; persist() }
    fun moveVolume(id: String, up: Boolean) {
        val i = book.volumes.indexOfFirst { it.id == id }; val j = if (up) i - 1 else i + 1
        if (i >= 0 && j in book.volumes.indices) { val item = book.volumes.removeAt(i); book.volumes.add(j, item); persist() }
    }
    fun moveChapter(volumeId: String, chapterId: String, up: Boolean) {
        val list = book.volumes.firstOrNull { it.id == volumeId }?.chapters ?: return
        val i = list.indexOfFirst { it.id == chapterId }; val j = if (up) i - 1 else i + 1
        if (i >= 0 && j in list.indices) { val item = list.removeAt(i); list.add(j, item); persist() }
    }
    fun delChapter(id: String) { findChapter(id)?.deleted = true; persist() }
    fun restoreChapter(id: String) { findChapter(id)?.deleted = false; persist() }
    fun restoreVolume(id: String) { book.volumes.firstOrNull { it.id == id }?.deleted = false; persist() }
    fun emptyTrash() { book.volumes.removeAll { it.deleted }; book.volumes.forEach { v -> v.chapters.removeAll { it.deleted } }; persist() }
    fun addFocus(id: String, minutes: Int) { focusState.value += minutes; prefs.edit().putInt("today_minutes", focusState.value).apply() }

    private fun findChapter(id: String): Chapter? = book.volumes.asSequence().flatMap { it.chapters.asSequence() }.firstOrNull { it.id == id }

    fun nameOf(kind: String, id: String): String = if (kind == "c") findChapter(id)?.title ?: "chapter" else book.title
    fun export(kind: String, id: String): String {
        if (kind == "c") return findChapter(id)?.let { "${it.title}\n\n${it.text}" } ?: ""
        return buildString {
            append(book.title).append("\n\n")
            book.volumes.filter { !it.deleted }.forEach { v ->
                append("# ").append(v.title).append("\n\n")
                v.chapters.filter { !it.deleted }.forEach { c -> append("## ").append(c.title).append("\n\n").append(c.text).append("\n\n") }
            }
        }
    }
    fun backup(output: OutputStream) { output.write(export("b", "").toByteArray(Charsets.UTF_8)) }
    fun restore(input: InputStream): Boolean = runCatching {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val v = book.volumes.firstOrNull { !it.deleted } ?: Volume(title = "恢复内容").also { book.volumes.add(it) }
        v.chapters.add(Chapter(title = "恢复的文本", text = text, words = text.count { !it.isWhitespace() }))
        persist(); true
    }.getOrDefault(false)
}

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("quill_preferences", Context.MODE_PRIVATE)
    var bg by mutableStateOf(prefs.getInt("bg", 0xFFF5ECCB.toInt()))
    var dark by mutableStateOf(prefs.getBoolean("dark", false))
    var size by mutableStateOf(prefs.getFloat("size", 18f))
    val fontSize: Float get() = size
    fun save() { prefs.edit().putInt("bg", bg).putBoolean("dark", dark).putFloat("size", size).apply() }
}
