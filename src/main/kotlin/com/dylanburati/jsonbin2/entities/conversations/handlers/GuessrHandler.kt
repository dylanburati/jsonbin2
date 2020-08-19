package com.dylanburati.jsonbin2.entities.conversations.handlers

import com.dylanburati.jsonbin2.entities.JsonExtended
import com.dylanburati.jsonbin2.entities.conversations.ActiveConversation
import com.dylanburati.jsonbin2.entities.conversations.ConversationUser
import com.dylanburati.jsonbin2.entities.conversations.MessageHandler
import com.dylanburati.jsonbin2.entities.conversations.SendMessageResult
import com.dylanburati.jsonbin2.entities.questions.Question
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GuessrHandler(active: ActiveConversation) : MessageHandler(active) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  data class Point(
    val x: Any,
    val y: Any,
    @JsonInclude(value = JsonInclude.Include.NON_NULL) val moreInfo: Any?
  )

  data class GuessrState(
    val convUser: ConversationUser,
    val question: Question.QuestionContent,
    val answers: HashMap<String, Answer> = HashMap()
  )

  data class Answer(val convUser: ConversationUser, val series: List<Point>)

  data class SubmitArgs(val series: List<Point>)
  data class ProgressResult(
    val progress: Int,
    val total: Int,
    val submittedUserIds: Collection<String>
  )

  data class RevealResult(val result: List<JsonExtended<SubmitArgs>>)

  private val state: AtomicReference<GuessrState?> = AtomicReference(null)
  private val answers = ConcurrentHashMap<String, Answer>()

  companion object {
    private const val ACTION_START = "guessr:start"
    private const val ACTION_SUBMIT = "guessr:submit"
    private const val ACTION_PROGRESS = "guessr:progress"
    private const val ACTION_REVEAL = "guessr:reveal"
    private const val ACTION_CANCEL = "guessr:cancel"
  }

  private fun startGame(convUser: ConversationUser, data: JsonNode) {
    val dbQuestion = services.questionService.getRandom("LineGraph")
    checkNotNull(dbQuestion) { "No questions found" }
    val question = dbQuestion.getQuestionContent()

    val success = state.compareAndSet(null, GuessrState(convUser, question))
    check(success) { "A question is already in progress" }

    val startContent = question.copy()
    startContent.data = startContent.data
      .map { m -> m.toMutableMap().filterKeys { it == "key" } }
      .toMutableList()
    val msg = services.messageService.send(
      convUser,
      ACTION_START,
      jacksonObjectMapper().valueToTree(
        JsonExtended(startContent).apply { extensions["type"] = dbQuestion.type }
      )
    )
    active.broadcast(SendMessageResult(type = "message", data = msg))
    revealIfDone() // will send initial progress
  }

  private fun submitAnswer(convUser: ConversationUser, data: JsonNode) {
    if (state.acquire == null) {
      cancel(convUser)
      return
    }

    val submission = jacksonObjectMapper()
      .runCatching { treeToValue<SubmitArgs>(data)!! }
      .getOrElse { error("Could not get series data") }

    state.updateAndGet { st ->
      st?.apply { answers[convUser.id] = Answer(convUser, submission.series) }
    }
    revealIfDone()
  }

  private fun cancel(convUser: ConversationUser) {
    val msg = services.messageService.send(
      convUser,
      ACTION_CANCEL,
      jacksonObjectMapper().valueToTree(null)
    )
    active.broadcast(SendMessageResult(type = "message", data = msg))
  }

  private fun revealIfDone() {
    val ongoing = state.acquire ?: return

    val (convUser, question, answers) = ongoing
    val activeUserIds = active.getActiveConvUserIds(Duration.ofSeconds(5))
    val totalHere = activeUserIds.size
    val answeredHere = answers.count { activeUserIds.contains(it.key) }
    if (answeredHere < totalHere) {
      val total = totalHere + answers.size - answeredHere
      val msg = services.messageService.send(
        convUser,
        ACTION_PROGRESS,
        jacksonObjectMapper().valueToTree(
          ProgressResult(
            progress = answers.size,
            total = total,
            submittedUserIds = answers.map { it.value.convUser.userId }
          )
        )
      )
      active.broadcast(SendMessageResult(type = "message", data = msg))

      return
    }

    if (totalHere == 0) {
      // no one to reveal to
      cancel(convUser)
    } else {
      // everyone answered
      val result = answers.map { (_, v) ->
        JsonExtended(SubmitArgs(v.series)).apply {
          extensions["name"] = v.convUser.nickname
          extensions["userId"] = v.convUser.userId
        }
      }.toMutableList()
      val sourceSeries = question.data.map { p ->
        val x = p["key"] ?: error("")
        val y = p["value"] ?: error("")
        Point(x, y, p["moreInfo"])
      }
      result.add(
        JsonExtended(SubmitArgs(sourceSeries)).apply { extensions["name"] = "Source" }
      )
      val msg = services.messageService.send(
        convUser,
        ACTION_REVEAL,
        jacksonObjectMapper().valueToTree(RevealResult(result = result))
      )
      active.broadcast(SendMessageResult(type = "message", data = msg))
    }
    state.set(null)
  }

  override fun onMessage(convUser: ConversationUser, action: String, data: JsonNode): Boolean {
    when (action) {
      ACTION_START -> startGame(convUser, data)
      ACTION_SUBMIT -> submitAnswer(convUser, data)
      else -> return false
    }
    return true
  }

  override fun onUserExit(convUser: ConversationUser) {
    CompletableFuture.delayedExecutor(5100L, TimeUnit.MILLISECONDS).execute {
      revealIfDone()
    }
  }
}
