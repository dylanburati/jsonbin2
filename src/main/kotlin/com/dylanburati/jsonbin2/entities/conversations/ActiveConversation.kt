package com.dylanburati.jsonbin2.entities.conversations

import io.javalin.websocket.WsContext
import java.util.concurrent.ConcurrentHashMap

class ActiveConversation(val conversation: Conversation) {
  val userMap = ConcurrentHashMap<ConversationUser, WsContext>()
}
