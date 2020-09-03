package com.dylanburati.jsonbin2.entities.conversations

import com.fasterxml.jackson.annotation.JsonIgnore

data class ConversationUser(
  @JsonIgnore var id: String,
  @JsonIgnore var conversationId: String,
  var userId: String,
  var nickname: String,
  var isOwner: Boolean
) {
  @JsonIgnore
  var conversation: Conversation? = null
}
