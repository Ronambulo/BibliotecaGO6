package com.bibliotecago.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTextTest {

    @Test fun `parrafos y saltos de linea`() {
        assertEquals(
            "Primer párrafo.\n\nSegundo, con salto\nde línea.",
            HtmlText.toPlainText("<div><p>Primer  párrafo.</p><p>Segundo, con salto<br/>de línea.</p></div>"),
        )
    }

    @Test fun `entidades con nombre y numericas`() {
        assertEquals("Tom & Jerry «sí» — 'ok' ’ é", HtmlText.toPlainText("Tom &amp; Jerry &laquo;sí&raquo; &mdash; &#39;ok&#39; &#x2019; &#233;"))
    }

    @Test fun `listas`() {
        assertEquals("Índice:\n• uno\n• dos", HtmlText.toPlainText("Índice:<ul><li>uno</li><li>dos</li></ul>"))
    }

    @Test fun `entidad desconocida se deja tal cual`() {
        assertEquals("a &foo; b", HtmlText.toPlainText("a &foo; b"))
    }
}
