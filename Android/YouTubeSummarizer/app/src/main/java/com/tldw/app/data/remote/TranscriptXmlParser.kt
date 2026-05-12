package com.tldw.app.data.remote

import com.tldw.app.data.dto.TranscriptSnippetDto
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Parses the raw transcript XML returned by the YouTube caption track endpoint.
 *
 * The XML has the following structure:
 * ```xml
 * <transcript>
 *   <text start="0.0" dur="2.5">Hello world</text>
 *   ...
 * </transcript>
 * ```
 */
class TranscriptXmlParser {

    fun parse(xml: String): List<TranscriptSnippetDto> {
        val inputStream = xml.byteInputStream(Charsets.UTF_8)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream)
        val elements = document.getElementsByTagName("text")
        val snippets = mutableListOf<TranscriptSnippetDto>()
        for (i in 0 until elements.length) {
            val element = elements.item(i) as? Element ?: continue
            val rawText = element.textContent ?: continue
            val text = decodeHtml(rawText)
            if (text.isBlank()) continue
            val start = element.getAttribute("start").toFloatOrNull() ?: 0f
            val duration = element.getAttribute("dur").toFloatOrNull() ?: 0f
            snippets.add(TranscriptSnippetDto(text = text, start = start, duration = duration))
        }
        return snippets
    }

    private fun decodeHtml(input: String): String =
        input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("\n", " ")
            .trim()
}
