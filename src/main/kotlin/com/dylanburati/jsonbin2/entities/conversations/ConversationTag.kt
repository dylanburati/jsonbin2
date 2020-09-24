package com.dylanburati.jsonbin2.entities.conversations

import me.liuwj.ktorm.schema.Table
import me.liuwj.ktorm.schema.varchar

data class ConversationTag(
  var conversationId: String,
  var tag: String
) {
  object TABLE : Table<Nothing>("conversation_tag") {
    val conversationId = varchar("conversation_id")
    val tag = varchar("tag")
  }
}
