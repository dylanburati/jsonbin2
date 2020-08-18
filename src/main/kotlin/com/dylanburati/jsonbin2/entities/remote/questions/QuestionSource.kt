package com.dylanburati.jsonbin2.entities.remote.questions

import java.time.Instant

data class QuestionSource(
  var id: String,
  var title: String,
  var createdAt: Instant
)
