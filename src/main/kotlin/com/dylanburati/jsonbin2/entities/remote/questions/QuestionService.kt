package com.dylanburati.jsonbin2.entities.remote.questions

import com.dylanburati.jsonbin2.Config
import com.dylanburati.jsonbin2.entities.BaseService
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.queryOf
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.Result
import org.eclipse.jetty.client.util.BufferingResponseListener
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.ssl.SslContextFactory
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class QuestionService(container: ServiceContainer) : BaseService(container) {
  private val logger = Log.getLogger(QuestionService::class.java)

  data class GSheetLambdaError(val message: String?)
  data class GSheetLambdaData(val data: GSheetLambdaData.Data) {
    @JsonIgnoreProperties("majorDimension")
    data class ValueRange(val range: String, val values: List<List<String>>)
    data class Data(val spreadsheetId: String, val valueRanges: List<ValueRange>)
  }

  fun getMostRecentSource(): QuestionSource? {
    val findOneQuery = queryOf(
      """SELECT * FROM "question_source" ORDER BY "created_at" DESC LIMIT 1"""
    )
      .map { row ->
        QuestionSource(
          id = row.string("id"),
          title = row.string("title"),
          createdAt = row.instant("created_at")
        )
      }
      .asSingle
    return session.run(findOneQuery)
  }

  fun saveSource(questionSource: QuestionSource): QuestionSource {
    val rowsAffected = session.run(
      queryOf(
        """
        INSERT INTO "question_source" ("id", "title", "created_at")
        VALUES (?, ?, ?)
        """,
        questionSource.id,
        questionSource.title,
        questionSource.createdAt
      ).asUpdate
    )

    if (rowsAffected != 1) throw Exception("Could not insert record")
    return questionSource
  }

  fun save(question: Question): Question {
    val rowsAffected = session.run(
      queryOf(
        """
        INSERT INTO "question" ("id", "source_id", "type", "data")
        VALUES (?, ?, ?, ? ::jsonb)
        """,
        question.id,
        question.sourceId,
        question.type,
        jacksonObjectMapper().writeValueAsString(question.data)
      ).asUpdate
    )

    if (rowsAffected != 1) throw Exception("Could not insert record")
    return question
  }

  fun refresh(): Future<Int> {
    val httpClient = HttpClient(SslContextFactory.Client())
    httpClient.start()
    val uri = Config.GSheetsLambda.getUri(mapOf("worksheets" to "LineGraph,Rank"))
    val request = httpClient.newRequest(uri)
      .header("x-api-key", Config.GSheetsLambda.getApiKey())
    return CompletableFuture<Int>().also { promise ->
      request.send(object : BufferingResponseListener(2 * 1024 * 1024) {
        override fun onComplete(result: Result?) {
          check(result != null && result.response != null) { "Could not execute Google sheets lambda" }
          if (result.response.status == 200) {
            val json = jacksonObjectMapper().readValue<GSheetLambdaData>(
              InputStreamReader(this.contentAsInputStream, UTF_8)
            )
            val questions = convertSheetToQuestions(json.data)
            val questionSource = saveSource(
              QuestionSource(
                id = generateId(),
                title = "Google sheet",
                createdAt = Instant.now()
              )
            )
            questions.forEach { q ->
              q.sourceId = questionSource.id
              save(q)
            }
          } else {
            val json = jacksonObjectMapper().readValue<GSheetLambdaError>(this.contentAsInputStream)
            logger.warn("Refresh Error: ${json.message}")
          }
          promise.complete(result.response?.status)
        }
      })
    }
  }

  private fun camelCase(str: String): String =
    str.replace(Regex("[^a-zA-Z0-9\\s]"), "").split(Regex("\\s")).foldIndexed("") { i, acc, cur ->
      val word = if (i == 0) cur.toLowerCase() else cur.toLowerCase().capitalize()
      acc + word
    }

  fun convertSheetToQuestions(sheet: GSheetLambdaData.Data): List<Question> {
    return sheet.valueRanges.filter { it.values.size >= 2 }.flatMap { vr ->
      val type = vr.range.split("!").first()
      val columnMap = vr.values[0].mapIndexed { i, label -> i to camelCase(label) }.associate { it }

      val questions = ArrayList<Question>()
      var startRow = 1
      while (startRow < vr.values.size) {
        val rest = vr.values.drop(startRow)
        val rowGroup = rest.takeLastWhile { row ->
          row == rest.first() || row.isEmpty() || row[0].isBlank()
        }
        val multiValMap = rowGroup.fold(HashMap<String, LinkedList<String>>()) { acc, row ->
          row.forEachIndexed { colIdx, cell ->
            if (cell.isNotBlank()) {
              val colName = columnMap[colIdx] ?: "unlabelled$colIdx"
              acc.compute(colName) { k, v ->
                v?.apply { add(cell) } ?: LinkedList(listOf(cell))
              }
            }
          }
          acc
        }
        val questionData = multiValMap.entries.associate { (k, v) ->
          val jsonValue = if (v.size == 1) v.first else v
          k to jsonValue
        }

        questions.add(Question(
          id = generateId(),
          sourceId = "",
          type = type,
          data = jacksonObjectMapper().valueToTree(questionData)
        ))
        startRow += rowGroup.size
      }
      questions
    }
  }
}
