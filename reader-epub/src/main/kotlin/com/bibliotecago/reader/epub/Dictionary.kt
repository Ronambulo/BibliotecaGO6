package com.bibliotecago.reader.epub

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Diccionarios incluidos: castellano (Wiktionary) e inglés (WordNet). */
enum class DictionaryLanguage(val code: String, val label: String) {
    Spanish("es", "Diccionario de castellano"),
    English("en", "English dictionary"),
    ;

    companion object {
        /** Por el idioma del libro (ISO 639-1 o -3); nulo si no hay diccionario para él. */
        fun of(language: String?): DictionaryLanguage? = when (language?.lowercase(Locale.ROOT)?.substringBefore('-')) {
            "es", "spa" -> Spanish
            "en", "eng" -> English
            else -> null
        }
    }
}

/** Una acepción agrupada por categoría gramatical; [formOf] si se llegó desde una forma flexionada. */
data class DictionaryEntry(
    val word: String,
    val partOfSpeech: String,
    val definitions: List<String>,
    val formOf: String? = null,
)

data class DictionaryResult(val query: String, val language: DictionaryLanguage, val entries: List<DictionaryEntry>)

/**
 * Diccionarios offline: bases SQLite generadas por tools/dictionaries/build.py.
 * Van comprimidas en el APK y se copian a disco la primera vez que se usan.
 */
class Dictionary(context: Context) {
    private val context = context.applicationContext
    private val databases = mutableMapOf<DictionaryLanguage, SQLiteDatabase>()

    /**
     * Busca la palabra en el diccionario del idioma del libro; sin idioma
     * conocido, en todos hasta encontrarla.
     */
    suspend fun lookUp(selection: String, bookLanguage: String?): DictionaryResult? = withContext(Dispatchers.IO) {
        val queries = queries(selection).ifEmpty { return@withContext null }
        val preferred = DictionaryLanguage.of(bookLanguage)
        val languages = listOfNotNull(preferred) + DictionaryLanguage.entries.filter { it != preferred }
        for (language in languages) {
            for (query in queries) {
                val entries = find(language, query)
                if (entries.isNotEmpty()) return@withContext DictionaryResult(query, language, entries)
            }
        }
        DictionaryResult(queries.last(), preferred ?: DictionaryLanguage.Spanish, emptyList())
    }

    private fun find(language: DictionaryLanguage, query: String): List<DictionaryEntry> {
        val db = database(language)
        val candidates = listOf(query) + if (language == DictionaryLanguage.English) englishBaseForms(query) else emptyList()
        for (word in candidates.distinct()) {
            val direct = entries(db, word)
            val lemmas = db.rawQuery("SELECT lemma FROM form WHERE form = ? LIMIT $MAX_LEMMAS", arrayOf(word)).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }.filter { it != word }
            val viaForms = lemmas.flatMap { lemma -> entries(db, lemma).map { it.copy(formOf = query) } }
            val viaRules = if (word != query && direct.isNotEmpty()) direct.map { it.copy(formOf = query) } else direct
            val all = viaRules + viaForms
            if (all.isNotEmpty()) return all
        }
        return emptyList()
    }

    private fun entries(db: SQLiteDatabase, word: String): List<DictionaryEntry> =
        db.rawQuery("SELECT pos, defs FROM entry WHERE word = ?", arrayOf(word)).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val definitions = c.getString(1).split('\n').filter { it.isNotBlank() }
                    if (definitions.isNotEmpty()) add(DictionaryEntry(word, c.getString(0), definitions))
                }
            }
        }

    @Synchronized
    private fun database(language: DictionaryLanguage): SQLiteDatabase = databases.getOrPut(language) {
        val dir = File(context.noBackupFilesDir, "dict")
        val file = File(dir, "${language.code}-$DATA_VERSION.db")
        if (!file.isFile) {
            dir.mkdirs()
            // Versiones anteriores de los datos: fuera.
            dir.listFiles { f -> f.name.startsWith("${language.code}-") }?.forEach { it.delete() }
            val partial = File(dir, "${file.name}.part")
            context.assets.open("dict/${language.code}.db").use { input ->
                partial.outputStream().use { input.copyTo(it) }
            }
            partial.renameTo(file)
        }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    @Synchronized
    fun close() {
        databases.values.forEach { it.close() }
        databases.clear()
    }

    companion object {
        /** Súbelo al regenerar las bases para que se vuelvan a copiar. */
        private const val DATA_VERSION = 1
        private const val MAX_LEMMAS = 4

        /**
         * Qué buscar: la selección entera si es una locución corta («sin
         * embargo») y su primera palabra, en minúsculas y sin la puntuación de
         * alrededor.
         */
        fun queries(selection: String): List<String> {
            val words = selection.replace('’', '\'').trim().split(Regex("\\s+"))
                .map { it.trim { c -> !c.isLetter() }.lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }
            if (words.isEmpty()) return emptyList()
            return listOfNotNull(words.takeIf { it.size in 2..3 }?.joinToString(" "), words.first())
        }

        /** Reglas de WordNet (morphy) para quitar flexiones regulares del inglés. */
        private val ENGLISH_SUFFIXES = listOf(
            "s" to "", "ses" to "s", "xes" to "x", "zes" to "z", "ches" to "ch", "shes" to "sh", "men" to "man", "ies" to "y",
            "es" to "e", "es" to "", "ed" to "e", "ed" to "", "ing" to "e", "ing" to "",
            "er" to "", "est" to "", "er" to "e", "est" to "e",
            "'s" to "", "s'" to "s",
        )

        private fun englishBaseForms(word: String): List<String> =
            ENGLISH_SUFFIXES.mapNotNull { (suffix, ending) ->
                if (word.length > suffix.length + 1 && word.endsWith(suffix)) word.dropLast(suffix.length) + ending else null
            }
    }
}
