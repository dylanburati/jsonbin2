package com.dylanburati.jsonbin2.entities.messages

import com.dylanburati.jsonbin2.InstantToEpoch
import com.dylanburati.jsonbin2.entities.conversations.ConversationUser
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import me.liuwj.ktorm.jackson.json
import me.liuwj.ktorm.schema.Table
import me.liuwj.ktorm.schema.text
import me.liuwj.ktorm.schema.timestamp
import me.liuwj.ktorm.schema.varchar
import java.time.Instant

data class Message(
  var id: String,
  @JsonIgnoreProperties("id", "conversationId", "isOwner")
  var sender: ConversationUser,
  @JsonSerialize(using = InstantToEpoch::class)
  var time: Instant,
  var target: String,
  var content: JsonNode
) {
  object TABLE : Table<Nothing>("message") {
    val id = varchar("id").primaryKey()
    val senderId = varchar("sender_id")
    val time = timestamp("time")
    val target = text("target")
    val content = json<JsonNode>("content")
  }
}

