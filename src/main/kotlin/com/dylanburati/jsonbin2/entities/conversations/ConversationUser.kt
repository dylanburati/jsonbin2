package com.dylanburati.jsonbin2.entities.conversations

data class ConversationUser(
  var id: String,
  var conversationId: String,
  var userId: String,
  var nickname: String
)
