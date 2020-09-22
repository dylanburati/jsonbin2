package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.InstantToEpoch
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
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
}
