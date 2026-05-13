package com.tldw.app.data.remote

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YouTubeApiServiceTest {

  private val server = MockWebServer()
  private lateinit var service: YouTubeApiService

  @Before
  fun setUp() {
    server.start()
    val client =
      OkHttpClient.Builder()
        .addInterceptor { chain ->
          val req = chain.request()
          chain.proceed(req.newBuilder().url(server.url(req.url.encodedPath)).build())
        }
        .build()
    service = YouTubeApiService(client = client)
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `fetchInnerTubeData parses video details correctly`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """
          {
            "videoDetails": {
              "videoId": "dQw4w9WgXcQ",
              "title": "Never Gonna Give You Up",
              "lengthSeconds": "212",
              "author": "RickAstleyVEVO",
              "viewCount": "1500000000"
            },
            "playabilityStatus": { "status": "OK" },
            "captions": null
          }
          """
            .trimIndent()
        )
    )

    val dto = service.fetchInnerTubeData("dQw4w9WgXcQ")

    assertNotNull(dto.videoDetails)
    assertEquals("dQw4w9WgXcQ", dto.videoDetails?.videoId)
    assertEquals("Never Gonna Give You Up", dto.videoDetails?.title)
    assertEquals("RickAstleyVEVO", dto.videoDetails?.author)
    assertEquals("212", dto.videoDetails?.lengthSeconds)
    assertEquals("1500000000", dto.videoDetails?.viewCount)
    assertEquals("OK", dto.playabilityStatus?.status)
  }

  @Test
  fun `fetchInnerTubeData sends POST with videoId in body and Accept-Language header`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"videoDetails": null, "playabilityStatus": null, "captions": null}""")
    )

    service.fetchInnerTubeData("testVideoId")

    val request = server.takeRequest()
    assertEquals("POST", request.method)
    assertEquals("en-US", request.getHeader("Accept-Language"))
    assertTrue("Request body must contain videoId", request.body.readUtf8().contains("testVideoId"))
  }

  @Test(expected = IOException::class)
  fun `fetchInnerTubeData throws IOException on HTTP error`() {
    server.enqueue(MockResponse().setResponseCode(403))
    service.fetchInnerTubeData("dQw4w9WgXcQ")
  }

  @Test
  fun `fetchInnerTubeData handles missing video details`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"playabilityStatus": {"status": "ERROR"}, "captions": null}""")
    )

    val dto = service.fetchInnerTubeData("badId")

    assertNull(dto.videoDetails)
    assertEquals("ERROR", dto.playabilityStatus?.status)
  }

  @Test
  fun `fetchVideoPageHtml returns page html`() {
    server.enqueue(MockResponse().setResponseCode(200).setBody("<html><body>YouTube</body></html>"))

    val html = service.fetchVideoPageHtml("dQw4w9WgXcQ")

    assertEquals("<html><body>YouTube</body></html>", html)
  }

  @Test
  fun `fetchVideoPageHtml sends GET with Accept-Language header`() {
    server.enqueue(MockResponse().setResponseCode(200).setBody("<html></html>"))

    service.fetchVideoPageHtml("dQw4w9WgXcQ")

    val request = server.takeRequest()
    assertEquals("GET", request.method)
    assertEquals("en-US", request.getHeader("Accept-Language"))
  }

  @Test(expected = IOException::class)
  fun `fetchVideoPageHtml throws IOException on HTTP error`() {
    server.enqueue(MockResponse().setResponseCode(404))
    service.fetchVideoPageHtml("dQw4w9WgXcQ")
  }

  @Test
  fun `fetchTranscriptXml returns xml body`() {
    val xml = "<transcript><text start=\"0.0\" dur=\"2.5\">Hello world</text></transcript>"
    server.enqueue(MockResponse().setResponseCode(200).setBody(xml))

    val result = service.fetchTranscriptXml(server.url("/timedtext").toString())

    assertEquals(xml, result)
  }

  @Test(expected = IOException::class)
  fun `fetchTranscriptXml throws IOException on HTTP error`() {
    server.enqueue(MockResponse().setResponseCode(500))
    service.fetchTranscriptXml(server.url("/timedtext").toString())
  }
}
