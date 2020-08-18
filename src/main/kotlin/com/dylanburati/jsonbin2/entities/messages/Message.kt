package com.dylanburati.jsonbin2.entities.messages

import com.dylanburati.jsonbin2.entities.conversations.ConversationUser
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.time.Instant

class InstantToEpoch : JsonSerializer<Instant>() {
  override fun serialize(value: Instant?, gen: JsonGenerator?, serializers: SerializerProvider?) {
    if (value == null) gen!!.writeNull()
    else gen!!.writeNumber(value.toEpochMilli())
  }
}

data class Message(
  var id: String,
  var sender: ConversationUser,
  @JsonSerialize(using = InstantToEpoch::class)
  var time: Instant,
  var target: String,
  var content: JsonNode
)

