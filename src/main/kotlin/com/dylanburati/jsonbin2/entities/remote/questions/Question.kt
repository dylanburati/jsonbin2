package com.dylanburati.jsonbin2.entities.remote.questions

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import java.util.*

data class Question(
  var id: String,
  var sourceId: String,
  var type: String,
  var data: JsonNode
) {
  data class QuestionContent(
    val title: String,
    val subtitle: String = "",
    val sources: List<Map<String, String>>,
    var data: MutableList<Map<String, Any>>
  ) {
    @JsonAnySetter
    @get:JsonAnyGetter
    var extensions: HashMap<String, Any?> = HashMap()

    fun copy(): QuestionContent {
      val other = QuestionContent(title, subtitle, ArrayList(sources), ArrayList(data))
      other.extensions.putAll(extensions)
      return other
    }
  }

  fun getQuestionContent(): QuestionContent {
    val content = jacksonObjectMapper()
      .runCatching { treeToValue<QuestionContent>(data)!! }
      .getOrElse { error("Question $id has invalid data") }
    if (content.extensions["randomizeKeyOrder"] == true) {
      content.data.shuffle()
    }
    if (content.extensions["randomizeKeysUsed"] == true) {
      content.data = content.data.take(6).toMutableList()
    }
    return content
  }
}
