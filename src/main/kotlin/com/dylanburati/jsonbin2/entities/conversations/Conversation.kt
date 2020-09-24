package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.InstantToEpoch
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import me.liuwj.ktorm.schema.Table
import me.liuwj.ktorm.schema.boolean
import me.liuwj.ktorm.schema.varchar
import java.time.Instant

data class Conversation(
  var id: String,
  var title: String,
  var isPrivate: Boolean
) {
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  @JsonSerialize(using = InstantToEpoch::class)
  var updatedAt: Instant? = null

  var tags: List<String> = listOf()

  object TABLE : Table<Nothing>("conversation") {
    val id = varchar("id").primaryKey()
    val title = varchar("title")
    val isPrivate = boolean("is_private")
  }
}
