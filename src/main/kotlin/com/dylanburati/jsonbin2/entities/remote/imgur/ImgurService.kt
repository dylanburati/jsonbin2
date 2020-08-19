package com.dylanburati.jsonbin2.entities.remote.imgur

import com.dylanburati.jsonbin2.Config
import com.dylanburati.jsonbin2.entities.BaseService
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.javalin.http.UploadedFile
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.Result
import org.eclipse.jetty.client.util.BufferingResponseListener
import org.eclipse.jetty.client.util.InputStreamContentProvider
import org.eclipse.jetty.client.util.MultiPartContentProvider
import org.eclipse.jetty.http.HttpFields
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.http.HttpMethod
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.ssl.SslContextFactory
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.CompletableFuture

class ImgurService(container: ServiceContainer) : BaseService(container) {
  private val logger = Log.getLogger(ImgurService::class.java)

  data class ImgurOKResponse(val success: Boolean, val status: Int, val data: ImgurAsset)

  fun uploadImage(file: UploadedFile): CompletableFuture<ImgurAsset?> {
    val httpClient = HttpClient(SslContextFactory.Client())
    httpClient.start()
    val form = MultiPartContentProvider()
    form.use {
      form.addFilePart(
        "image",
        file.filename,
        InputStreamContentProvider(file.content),
        HttpFields(10).apply { add(HttpHeader.CONTENT_TYPE, file.contentType) }
      )
    }

    val request = httpClient.newRequest("https://api.imgur.com/3/upload")
      .method(HttpMethod.POST)
      .header("Authorization", Config.Imgur.getAuthKey())
      .content(form)
    return CompletableFuture<ImgurAsset?>().also { promise ->
      logger.info("send ${file.filename}")
      request.send(object : BufferingResponseListener(2 * 1024 * 1024) {
        override fun onComplete(result: Result?) {
          logger.info("onComplete ${file.filename}")
          check(result != null && result.response != null) { "Could not post to Imgur API" }
          if (result.response.status == 200) {
            try {
              val json = jacksonObjectMapper().readValue<ImgurOKResponse>(
                InputStreamReader(this.contentAsInputStream, UTF_8)
              )
              promise.complete(json.data)
            } catch (e: JsonMappingException) {
              logger.warn("Could not use Imgur JSON response", e)
            }
          } else {
            logger.warn("Upload Error: ${this.getContentAsString(UTF_8)}")
          }
          promise.complete(null)
        }
      })
    }
  }
}
