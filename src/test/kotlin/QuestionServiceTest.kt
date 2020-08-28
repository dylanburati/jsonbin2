package com.dylanburati.jsonbin2.entities.questions

import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.nhaarman.mockitokotlin2.mock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class QuestionServiceTests {
  @Test
  fun check() {
    assert(true)
  }

  @ParameterizedTest(name = "min/max for range [{0}, {1}]")
  @CsvSource(
    "-99.1, 99.1, -100, 100",
    "0, 9.75, 0, 10",
    "30, 100, 30, 100"
  )
  fun postProcessLineGraph(first: Float, second: Float, expectedMin: Float, expectedMax: Float) {
    val containerMock = mock<ServiceContainer>()
    val service = QuestionService(containerMock)
    val data = hashMapOf<String, Any>(
      "data" to listOf(
        hashMapOf("key" to "1st", "value" to first, "moreInfo" to ""),
        hashMapOf("key" to "2nd", "value" to second, "moreInfo" to "")
      )
    )
    val processed = service.postProcessQuestionData("LineGraph", data)
    assertEquals(expectedMin, processed["yMin"]) {
      "computed min should equal $expectedMin"
    }
    assertEquals(expectedMax, processed["yMax"]) {
      "computed max should equal $expectedMax"
    }
  }
}
