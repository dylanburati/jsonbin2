package com.dylanburati.jsonbin2

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import java.time.Instant

class InstantToEpoch : JsonSerializer<Instant>() {
  override fun serialize(value: Instant?, gen: JsonGenerator?, serializers: SerializerProvider?) {
    if (value == null) gen!!.writeNull()
    else gen!!.writeNumber(value.toEpochMilli())
  }
}
