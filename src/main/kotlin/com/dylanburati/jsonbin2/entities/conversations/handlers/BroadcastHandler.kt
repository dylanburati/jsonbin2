package com.dylanburati.jsonbin2.entities.conversations.handlers

import com.dylanburati.jsonbin2.entities.conversations.*
import com.fasterxml.jackson.databind.JsonNode

class BroadcastHandler(active: ActiveConversation) : MessageHandler(active) {
  override fun onMessage(convUser: ConversationUser, action: String, data: JsonNode): Boolean {
    val msg = services.messageService.send(convUser, action, data)
    active.broadcast(
      SendMessageResult(
        type = "message",
        data = msg
      )
    )
    return true
  }
}
