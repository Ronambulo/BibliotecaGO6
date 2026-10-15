package com.bibliotecago.server

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlsTest {

    private fun valid(raw: String) = (ServerUrls.check(raw) as ServerUrls.Check.Valid).base.toString()
    private fun invalid(raw: String) = ServerUrls.check(raw) is ServerUrls.Check.Invalid

    @Test fun `https publico se admite y se normaliza`() {
        assertEquals("https://calibre.example.com/", valid("https://calibre.example.com"))
        assertEquals("https://calibre.example.com/", valid("  https://calibre.example.com/opds/ "))
        assertEquals("https://example.com/libros/", valid("https://example.com/libros/opds?x=1#y"))
    }

    @Test fun `http solo hacia red privada`() {
        assertEquals("http://192.168.0.24:8083/", valid("http://192.168.0.24:8083"))
        assertEquals("http://10.147.13.1:8083/", valid("http://10.147.13.1:8083/opds"))
        assertEquals("http://172.20.0.5/", valid("http://172.20.0.5"))
        assertTrue(invalid("http://calibre.example.com"))
        assertTrue(invalid("http://8.8.8.8"))
        assertTrue(invalid("http://172.32.0.1"))
        assertTrue(invalid("http://192.169.0.1"))
        // Un nombre no es una IP privada aunque lo parezca.
        assertTrue(invalid("http://192.168.0.24.nip.io"))
    }

    @Test fun `entradas mal formadas`() {
        assertTrue(invalid(""))
        assertTrue(invalid("calibre.example.com"))
        assertTrue(invalid("ftp://calibre.example.com"))
        assertTrue(invalid("https://usuario:clave@calibre.example.com"))
    }

    @Test fun `direcciones privadas`() {
        assertTrue(ServerUrls.isPrivateAddress("10.0.0.1"))
        assertTrue(ServerUrls.isPrivateAddress("172.16.0.1"))
        assertTrue(ServerUrls.isPrivateAddress("172.31.255.255"))
        assertTrue(ServerUrls.isPrivateAddress("192.168.1.1"))
        assertTrue(ServerUrls.isPrivateAddress("127.0.0.1"))
        assertFalse(ServerUrls.isPrivateAddress("172.15.0.1"))
        assertFalse(ServerUrls.isPrivateAddress("192.168.1"))
        assertFalse(ServerUrls.isPrivateAddress("192.168.1.256"))
        assertFalse(ServerUrls.isPrivateAddress("localhost"))
    }

    @Test fun `los enlaces del feed valen para cualquier base`() {
        val local = "http://192.168.0.24:8083/".toHttpUrl()
        val tunnel = "https://calibre.example.com/".toHttpUrl()
        assertEquals("http://192.168.0.24:8083/opds", ServerUrls.catalogRoot(local).toString())
        assertEquals("http://192.168.0.24:8083/opds/new?offset=60", ServerUrls.resolve(local, "/opds/new?offset=60").toString())
        assertEquals("https://calibre.example.com/opds/cover/2", ServerUrls.resolve(tunnel, "/opds/cover/2").toString())
    }
}
