package com.dylanburati.jsonbin2.entities.messages

import com.dylanburati.jsonbin2.entities.conversations.ConversationUser
import java.time.Instant

data class Message(
  var id: String,
  var sender: ConversationUser,
  var time: Instant,
  var target: String,
  var content: Map<String, Any>
)

