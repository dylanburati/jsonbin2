package com.dylanburati.jsonbin2.entities.remote.imgur

import com.dylanburati.jsonbin2.entities.ServiceContainer
import io.javalin.http.Context
import java.util.concurrent.CompletableFuture

object ImgurController {
  private val services = ServiceContainer

  data class UploadResult(
    val success: Boolean,
    val data: List<ImgurAsset>
  )

  fun upload(ctx: Context) {
    check(ctx.isMultipartFormData()) { "Upload must be multipart/form-data" }
    val files = ctx.uploadedFiles("attachments")
    check(files.size in 1..12) { "Upload must contain 1-12 attachments. Found ${files.size}" }
    val futures = files.map { services.imgurService.uploadImage(it) }
    val combined = CompletableFuture.supplyAsync {
      val assets = futures.mapNotNull { it.get() }
      UploadResult(
        success = assets.isNotEmpty(),
        data = assets
      )
    }

    ctx.json(combined)
  }
}
