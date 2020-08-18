package com.dylanburati.jsonbin2.entities

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonUnwrapped
import java.util.*


class JsonExtended<T>(@JsonUnwrapped val base: T) {
  @JsonAnySetter
  @get:JsonAnyGetter
  var extensions: HashMap<String, Any?> = HashMap()
}
