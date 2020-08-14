package com.dylanburati.jsonbin2.entities.conversations.handlers

import com.dylanburati.jsonbin2.entities.conversations.ActiveConversation
import com.dylanburati.jsonbin2.entities.conversations.ConversationUser
import com.dylanburati.jsonbin2.entities.conversations.MessageHandler
import com.dylanburati.jsonbin2.entities.conversations.SendMessageResult
import com.dylanburati.jsonbin2.entities.remote.questions.Question
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class GuessrHandler(active: ActiveConversation) : MessageHandler(active) {
  data class Point(
    val key: Any,
    val value: Any
  )

  data class GuessrState(
    val convUser: ConversationUser,
    val question: Question.QuestionContent,
    val activeUserIds: HashSet<String>
  )
  data class SubmitArgs(val series: List<Point>)

  val state: AtomicReference<GuessrState?> = AtomicReference(null)
  val answers = ConcurrentHashMap<String, SubmitArgs>()

  companion object {
    private const val ACTION_START = "guessr:start"
    private const val ACTION_SUBMIT = "guessr:submit"
    private const val ACTION_REVEAL = "guessr:reveal"
  }

  private fun startGame(convUser: ConversationUser, data: JsonNode) {
    check(state.acquire == null) { "A question is already in progress" }

    val dbQuestion = services.questionService.getRandom("LineGraph")
    checkNotNull(dbQuestion) { "No questions found" }
    val question = dbQuestion.getQuestionContent()
    val activeUserIds = active.userMap.asSequence()
      .filter { (_, v) -> v.get() > 0 }
      .map { (k) -> k }
      .toHashSet()
    state.set(GuessrState(convUser, question, activeUserIds))
    answers.clear()

    val startContent = question.copy()
    startContent.data = startContent.data
      .map { m -> m.toMutableMap().filterKeys { it == "key" } }
      .toMutableList()
    val msg = services.messageService.send(
      convUser,
      ACTION_START,
      jacksonObjectMapper().valueToTree(startContent)
    )
    active.broadcast(SendMessageResult(type = "message", data = msg))
  }

  private fun submitAnswer(convUser: ConversationUser, data: JsonNode) {
    check(state.acquire != null) { "There is no question in progress" }

    val submission = jacksonObjectMapper()
      .runCatching { treeToValue<SubmitArgs>(data)!! }
      .getOrElse { error("Could not get series data") }

    answers[convUser.id] = submission
    revealIfDone()
  }

  private fun revealIfDone() {
    val ongoing = state.acquire ?: return

    val (convUser, question, activeUserIds) = ongoing
    if (activeUserIds.size > answers.size) return
    if (activeUserIds.any { !answers.containsKey(it) }) return

    val msg = services.messageService.send(
      convUser,
      ACTION_REVEAL,
      jacksonObjectMapper().valueToTree(question)
    )
    active.broadcast(SendMessageResult(type = "message", data = msg))

    state.set(null)
    answers.clear()
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
    state.updateAndGet { st ->
      st?.apply { activeUserIds.remove(convUser.id) }
    }
    revealIfDone()
  }
}
