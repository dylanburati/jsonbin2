package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.messages.Message
import com.fasterxml.jackson.databind.JsonNode

data class SendMessageResult(val type: String, val data: Message)

abstract class MessageHandler(val active: ActiveConversation) {
  val services = ServiceContainer

  abstract fun onMessage(convUser: ConversationUser, action: String, data: JsonNode): Boolean
  open fun onUserEnter(convUser: ConversationUser) {}
  open fun onUserExit(convUser: ConversationUser) {}
  open fun onNicknameChange(convUser: ConversationUser) {}
}
