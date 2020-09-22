package com.dylanburati.jsonbin2.entities.questions

import me.liuwj.ktorm.schema.Table
import me.liuwj.ktorm.schema.timestamp
import me.liuwj.ktorm.schema.varchar
import java.time.Instant

data class QuestionSource(
  var id: String,
  var title: String,
  var createdAt: Instant
) {
  object TABLE : Table<Nothing>("question_source") {
    val id = varchar("id").primaryKey()
    val title = varchar("title")
    val createdAt = timestamp("created_at")
  }
}
