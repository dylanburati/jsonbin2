package com.dylanburati.jsonbin2.entities.conversations

data class Conversation(
  var id: String,
  var title: String,
  var users: ArrayList<ConversationUser>
)
