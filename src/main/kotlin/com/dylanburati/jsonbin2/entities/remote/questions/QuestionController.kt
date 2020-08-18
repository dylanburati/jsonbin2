package com.dylanburati.jsonbin2.entities.remote.questions

import com.dylanburati.jsonbin2.TooManyRequestsResponse
import com.dylanburati.jsonbin2.entities.ServiceContainer
import io.javalin.http.Context
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture

object QuestionController {
  private val services = ServiceContainer

  data class RefreshResult(
    val success: Boolean,
    val lambdaStatus: Int
  )

  fun refresh(ctx: Context) {
    val last = services.questionService.getMostRecentSource()
    if (last != null) {
      val elapsedWait = Duration.between(last.createdAt, Instant.now())
      if (elapsedWait < Duration.ofMinutes(10)) {
        val remainingWait = Duration.ofMinutes(10) - elapsedWait
        throw TooManyRequestsResponse(
          message = "Question refresh cooldown ends in ${remainingWait.seconds + 1} seconds",
          retryAfterSeconds = remainingWait.seconds + 1
        )
      }
    }
    ctx.json(
      CompletableFuture.supplyAsync {
        val status = services.questionService.refresh().get()
        RefreshResult(success = status == 200, lambdaStatus = status)
      }
    )
  }
}
