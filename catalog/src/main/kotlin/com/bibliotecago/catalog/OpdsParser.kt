package com.bibliotecago.catalog

import com.bibliotecago.core.model.Book
import com.bibliotecago.core.model.BookFile
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lector de feeds OPDS 1.x (Atom) ajustado a lo que emite Calibre-Web
 * (ver los fixtures en `src/test/resources/opds`). DOM en vez de un parser en
 * flujo: los feeds son de unas decenas de entradas y así se prueba en la JVM.
 *
 * Se navega siempre por enlaces y nunca por títulos, que Calibre-Web traduce
 * al idioma del usuario.
 */
object OpdsParser {

    fun parse(input: InputStream): OpdsFeed {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Sin DTD no hay entidades externas (XXE). Android no admite la característica: lo ignora.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val root = factory.newDocumentBuilder().parse(input).documentElement
        require(root.localName == "feed" && root.namespaceURI == ATOM) { "No es un feed Atom" }

        val links = root.children(ATOM, "link")
        val entries = root.children(ATOM, "entry")
        return OpdsFeed(
            title = root.childText(ATOM, "title").orEmpty(),
            navigation = entries.filterNot(::isAcquisition).mapNotNull(::navigation),
            books = entries.filter(::isAcquisition).map(::book),
            nextHref = links.firstOrNull { it.getAttribute("rel") == "next" }?.href(),
            searchTemplate = links.firstOrNull {
                it.getAttribute("rel") == "search" && it.getAttribute("href").contains("{searchTerms}")
            }?.getAttribute("href"),
        )
    }

    private fun isAcquisition(entry: Element) =
        entry.children(ATOM, "link").any { it.getAttribute("rel").startsWith(REL_ACQUISITION) }

    private fun navigation(entry: Element): OpdsNavigation? {
        val link = entry.children(ATOM, "link").firstOrNull { it.getAttribute("type").contains("atom+xml") }
            ?: return null
        return OpdsNavigation(
            title = entry.childText(ATOM, "title").orEmpty(),
            href = link.href() ?: return null,
            summary = entry.children(ATOM, "content").firstOrNull()?.textContent?.trim()?.ifEmpty { null },
        )
    }

    private fun book(entry: Element): Book {
        val links = entry.children(ATOM, "link")
        val content = entry.children(ATOM, "content").firstOrNull()
        return Book(
            id = BookId(entry.childText(ATOM, "id").orEmpty()),
            title = entry.childText(ATOM, "title").orEmpty(),
            authors = entry.children(ATOM, "author").mapNotNull { it.childText(ATOM, "name")?.ifEmpty { null } },
            summary = content?.let(::summary),
            tags = entry.children(ATOM, "category").mapNotNull { category ->
                category.getAttribute("label").ifEmpty { category.getAttribute("term") }.trim().ifEmpty { null }
            },
            files = links.filter { it.getAttribute("rel").startsWith(REL_ACQUISITION) }.mapNotNull(::file),
            coverPath = (links.firstOrNull { it.getAttribute("rel") == REL_IMAGE }
                ?: links.firstOrNull { it.getAttribute("rel") == REL_THUMBNAIL })?.href(),
            language = entry.childElements().firstOrNull { it.localName == "language" }?.textContent?.trim()?.ifEmpty { null },
            rating = content?.let(::rating),
        )
    }

    private fun file(link: Element): BookFile? {
        val format = when (link.getAttribute("type").substringBefore(';').trim()) {
            "application/epub+zip" -> BookFormat.EPUB
            "application/pdf" -> BookFormat.PDF
            else -> return null // AZW3, MOBI, CBZ…: la app solo lee EPUB y PDF.
        }
        return BookFile(format, link.href() ?: return null, link.getAttribute("length").toLongOrNull())
    }

    /**
     * Calibre-Web mete en `<content>` unas líneas «RATING: ★★★» y «TAGS: …» y
     * después la sinopsis dentro de un `<p>`, con su HTML escapado una vez más.
     */
    private fun summary(content: Element): String? {
        val html = when (content.getAttribute("type")) {
            "xhtml" -> {
                val div = content.childElements().firstOrNull() ?: content
                div.childElements().filter { it.localName != "br" }.joinToString("\n") { it.textContent }
            }
            else -> content.textContent
        }
        return HtmlText.toPlainText(html).ifEmpty { null }
    }

    private fun rating(content: Element): Int? {
        val stars = Regex("""RATING:\s*(★*)""").find(content.textContent)?.groupValues?.get(1) ?: return null
        return stars.length
    }

    private fun Element.href(): String? = getAttribute("href").ifEmpty { null }

    private fun Element.childElements(): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun Element.children(ns: String, name: String): List<Element> =
        childElements().filter { it.namespaceURI == ns && it.localName == name }

    private fun Element.childText(ns: String, name: String): String? =
        children(ns, name).firstOrNull()?.textContent?.trim()

    private const val ATOM = "http://www.w3.org/2005/Atom"
    private const val REL_ACQUISITION = "http://opds-spec.org/acquisition"
    private const val REL_IMAGE = "http://opds-spec.org/image"
    private const val REL_THUMBNAIL = "http://opds-spec.org/image/thumbnail"
}
