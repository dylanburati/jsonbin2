package com.dylanburati.jsonbin2.entities.remote.questions

import com.fasterxml.jackson.databind.JsonNode

data class Question(
  var id: String,
  var sourceId: String,
  var type: String,
  var data: JsonNode
)
