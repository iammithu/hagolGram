package com.reelchat.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Best-effort: reads the public embed page of a reel and pulls out the video file URL so the reel can
 * play full-screen in our own player. This is unofficial and may stop working whenever Instagram changes
 * the page; ReelPlayer falls back to the plain Instagram embed when this returns null.
 */
object VideoSource {
    private val cache = HashMap<String, String>()
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private val pats = listOf(
        Regex("\"video_url\"\\s*:\\s*\"(https:[^\"]+)\""),
        Regex("<video[^>]*\\ssrc=\"(https:[^\"]+)\""),
        Regex("(https:[^\"'\\s<>\\\\]+\\.mp4[^\"'\\s<>\\\\]*)")
    )

    suspend fun resolve(reelId: String): String? {
        cache[reelId]?.let { return it }
        val found = withContext(Dispatchers.IO) {
            runCatching {
                var r: String? = null
                for (path in listOf("embed/captioned/", "embed/")) {
                    r = extract(fetch("https://www.instagram.com/reel/$reelId/$path"))
                    if (r != null) break
                }
                r
            }.getOrNull()
        }
        if (found != null) cache[reelId] = found
        return found
    }

    private fun fetch(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        c.connectTimeout = 10000
        c.readTimeout = 10000
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun extract(raw: String): String? {
        val t = raw.replace("\\\"", "\"").replace("\\/", "/")
            .replace("\\u0026", "&").replace("\\u002F", "/").replace("&amp;", "&")
        for (p in pats) {
            val m = p.find(t)
            if (m != null) return m.groupValues[1].replace("\\", "")
        }
        return null
    }
}
