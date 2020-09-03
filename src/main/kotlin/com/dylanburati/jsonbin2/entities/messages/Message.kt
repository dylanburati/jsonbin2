package com.dylanburati.jsonbin2.entities.messages

import com.dylanburati.jsonbin2.InstantToEpoch
import com.dylanburati.jsonbin2.entities.conversations.ConversationUser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.time.Instant

data class Message(
  var id: String,
  var sender: ConversationUser,
  @JsonSerialize(using = InstantToEpoch::class)
  var time: Instant,
  var target: String,
  var content: JsonNode
)

