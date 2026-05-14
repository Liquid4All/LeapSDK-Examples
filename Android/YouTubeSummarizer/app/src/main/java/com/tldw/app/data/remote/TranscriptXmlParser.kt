package com.tldw.app.data.remote

import com.tldw.app.data.dto.TranscriptSnippetDto
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class TranscriptXmlParser {

  fun parse(xml: String): List<TranscriptSnippetDto> {
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(xml.reader())

    val snippets = mutableListOf<TranscriptSnippetDto>()
    var currentStart = 0f
    var currentDuration = 0f
    var inTextTag = false

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG if parser.name == "text" -> {
          currentStart = parser.getAttributeValue(null, "start").toFloatOrNull() ?: 0f
          currentDuration = parser.getAttributeValue(null, "dur").toFloatOrNull() ?: 0f
          inTextTag = true
        }
        XmlPullParser.TEXT if inTextTag -> {
          val text = (parser.text ?: "").replace("\n", " ").trim()
          if (text.isNotBlank()) {
            snippets.add(
              TranscriptSnippetDto(text = text, start = currentStart, duration = currentDuration)
            )
          }
        }
        XmlPullParser.END_TAG if parser.name == "text" -> {
          inTextTag = false
        }
      }
      eventType = parser.next()
    }

    return snippets
  }
}
