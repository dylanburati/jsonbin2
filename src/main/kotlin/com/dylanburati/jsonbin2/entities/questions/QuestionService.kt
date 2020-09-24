package com.dylanburati.jsonbin2.entities.questions

import com.dylanburati.jsonbin2.Config
import com.dylanburati.jsonbin2.entities.BaseService
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.nonNull
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.liuwj.ktorm.dsl.*
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.Result
import org.eclipse.jetty.client.util.BufferingResponseListener
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.ssl.SslContextFactory
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

class QuestionService(container: ServiceContainer) : BaseService(container) {
  private val logger = Log.getLogger(QuestionService::class.java)

  data class ColumnConversionResult(
    val questions: ArrayList<Question> = ArrayList(),
    val warnings: ArrayList<String> = ArrayList()
  )

  data class GSheetLambdaError(val message: String?)
  data class GSheetLambdaData(val data: Data) {
    @JsonIgnoreProperties("majorDimension")
    data class ValueRange(val range: String, val values: List<List<String>>)
    data class Data(val spreadsheetId: String, val valueRanges: List<ValueRange>)
  }

  enum class GSheetColumnType {
    BOOLEAN, FLOAT, STRING
  }

  data class GSheetColumn(
    val name: String,
    val type: GSheetColumnType,
    val isRequired: Boolean = false,
    val isList: Boolean = false
  )

  data class GSheetColumnGroup(
    val name: String,
    val children: Map<GSheetColumn, String>
  )

  private val supportedColumns: Map<String, List<GSheetColumn>>
  private val supportedColumnGroups: Map<String, List<GSheetColumnGroup>>

  init {
    // todo range
    val lineGraphCols = listOf(
      GSheetColumn(
        name = "title",
        type = GSheetColumnType.STRING,
        isRequired = true
      ),
      GSheetColumn(
        name = "subtitle",
        type = GSheetColumnType.STRING,
        isRequired = true
      ),
      GSheetColumn(
        name = "randomizeKeyOrder",
        type = GSheetColumnType.BOOLEAN
      ),
      GSheetColumn(
        name = "randomizeKeysUsed",
        type = GSheetColumnType.BOOLEAN
      ),
      GSheetColumn(
        name = "yMin",
        type = GSheetColumnType.FLOAT
      ),
      GSheetColumn(
        name = "yMax",
        type = GSheetColumnType.FLOAT
      ),
      GSheetColumn(
        name = "sourceFormat",
        type = GSheetColumnType.STRING,
        isRequired = true,
        isList = true
      ),
      GSheetColumn(
        name = "sourceLinkUrl",
        type = GSheetColumnType.STRING,
        isRequired = true,
        isList = true
      ),
      GSheetColumn(
        name = "keys",
        type = GSheetColumnType.STRING,
        isRequired = true,
        isList = true
      ),
      GSheetColumn(
        name = "values",
        type = GSheetColumnType.FLOAT,
        isRequired = true,
        isList = true
      ),
      GSheetColumn(
        name = "moreInfo",
        type = GSheetColumnType.STRING,
        isRequired = false,
        isList = true
      )
    )
    val rankCols = lineGraphCols.asSequence().filter { it.name !in listOf("yMin", "yMax") }.map {
      if (it.name == "values") {
        GSheetColumn(
          name = "values",
          type = GSheetColumnType.STRING,
          isRequired = true,
          isList = true
        )
      } else {
        GSheetColumn(
          name = it.name,
          type = it.type,
          isRequired = it.isRequired,
          isList = it.isList
        )
      }
    }.toList()

    fun buildGroups(cols: List<GSheetColumn>): List<GSheetColumnGroup> = listOf(
      GSheetColumnGroup(name = "sources",
        children = mapOf(
          (cols.find { it.name == "sourceFormat" } ?: error("")) to "format",
          (cols.find { it.name == "sourceLinkUrl" } ?: error("")) to "url"
        )),
      GSheetColumnGroup(name = "data",
        children = mapOf(
          (cols.find { it.name == "keys" } ?: error("")) to "key",
          (cols.find { it.name == "values" } ?: error("")) to "value",
          (cols.find { it.name == "moreInfo" } ?: error("")) to "moreInfo"
        ))
    )
    supportedColumns = mapOf("LineGraph" to lineGraphCols, "Rank" to rankCols)
    supportedColumnGroups = supportedColumns.mapValues { (_, v) -> buildGroups(v) }
  }

  fun getMostRecentSource(): QuestionSource? {
    val findOneQuery = database.from(QuestionSource.TABLE).select()
      .orderBy(QuestionSource.TABLE.createdAt.desc())
      .limit(0, 1)
      .map { row ->
        QuestionSource.TABLE.let { t ->
          QuestionSource(
            id = row.nonNull(t.id),
            title = row.nonNull(t.title),
            createdAt = row.nonNull(t.createdAt)
          )
        }
      }

    return findOneQuery.firstOrNull()
  }

  fun saveSource(questionSource: QuestionSource): QuestionSource {
    val rowsAffected = database.insert(QuestionSource.TABLE) {
      set(it.id, questionSource.id)
      set(it.title, questionSource.title)
      set(it.createdAt, questionSource.createdAt)
    }

    if (rowsAffected != 1) throw Exception("Could not insert record")
    return questionSource
  }

  fun getRandom(type: String): Question? {
    val source = getMostRecentSource() ?: return null

    val options = database.from(Question.TABLE).select()
      .where { (Question.TABLE.sourceId eq source.id) and (Question.TABLE.type eq type) }
      .map { row ->
        Question.TABLE.let { t ->
          Question(
            id = row.nonNull(t.id),
            sourceId = row.nonNull(t.sourceId),
            type = row.nonNull(t.type),
            data = row.nonNull(t.data)
          )
        }
      }

    return if (options.isEmpty()) null else options[secureRandom.nextInt(options.size)]
  }

  fun save(question: Question): Question {
    val rowsAffected = database.insert(Question.TABLE) {
      set(it.id, question.id)
      set(it.sourceId, question.sourceId)
      set(it.type, question.type)
      set(it.data, question.data)
    }

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
            val converted = convertSheetToQuestions(json.data)
            val questionSource = saveSource(
              QuestionSource(
                id = generateId(),
                title = "Google sheet",
                createdAt = Instant.now()
              )
            )
            converted.questions.forEach { q ->
              q.sourceId = questionSource.id
              save(q)
            }
          } else {
            var message = jacksonObjectMapper().runCatching {
              val json = readValue<GSheetLambdaError>(contentAsInputStream)
              json.message
            }.getOrNull()
            message = message ?: result.failure?.message ?: "Unknown error"
            logger.warn("Refresh Error: $message")
          }
          promise.complete(result.response?.status)
        }
      })
    }
  }

  private fun camelCase(str: String): String =
    str.replace(Regex("[^a-zA-Z0-9\\s_-]"), "").split(Regex("[\\s_-]"))
      .foldIndexed("") { i, acc, cur ->
        val word = if (i == 0) cur.toLowerCase() else cur.toLowerCase().capitalize()
        acc + word
      }

  private fun convertSingleSheetToQuestions(vr: GSheetLambdaData.ValueRange): List<Question> {
    val type = vr.range.split("!").first()
    val typeSupportedCols = supportedColumns[type] ?: error("Unsupported question type: $type")
    val typeColumnGroups = supportedColumnGroups[type] ?: error("Unsupported question type: $type")
    val columnLabelMap =
      vr.values[0].mapIndexed { i, label -> camelCase(label) to i }.associate { it }
    val columnMap = typeSupportedCols.map { col ->
      val colIdx = columnLabelMap[col.name] ?: error("Column not found for $type ${col.name}")
      colIdx to col
    }.associate { it }

    val groupMap =
      typeColumnGroups.asSequence().flatMap { g ->
        g.children.keys.asSequence().map { col -> col to g }
      }.toMap()
    val ungroupedCols = typeSupportedCols.filter { col -> !groupMap.containsKey(col) }

    val questions = ArrayList<Question>()
    var startRow = 1
    while (startRow < vr.values.size) {
      val rest = vr.values.drop(startRow)
      val rowGroup = rest.takeWhile { row ->
        row == rest.first() || row.isEmpty() || row[0].isBlank()
      }
      val rowGroupJson = HashMap<String, Any>()
      val rowGroupScalarLists = ungroupedCols.asSequence()
        .filter { col -> col.isList }
        .associateWith { col ->
          val lst = ArrayList<Any?>()
          rowGroupJson[col.name] = lst
          lst
        }
      val rowGroupObjectLists = typeColumnGroups.associateWith { group ->
        val lst = ArrayList<HashMap<String, Any?>>(rowGroup.size)
        rowGroupJson[group.name] = lst
        lst
      }
      rowGroup.forEachIndexed { rowIdx, row ->
        row.forEachIndexed { colIdx, cell ->
          val col = columnMap[colIdx]

          if (col != null) {
            val el: Any? = when {
              cell.isEmpty() -> null
              col.type == GSheetColumnType.STRING -> cell
              col.type == GSheetColumnType.FLOAT -> cell.toFloat()
              col.type == GSheetColumnType.BOOLEAN -> cell.toBoolean()
              else -> throw Exception()
            }
            val group = groupMap[col]
            if (group != null) {
              val nestedCol = group.children[col]!!
              val lst = rowGroupObjectLists[group]!!
              if (lst.size <= rowIdx) lst.add(HashMap())
              lst[rowIdx][nestedCol] = el
            } else if (col.isList) {
              val lst = rowGroupScalarLists[col]!!
              if (el != null) lst.add(el)
            } else {
              // TODO warning on overwrite
              if (el != null) rowGroupJson.putIfAbsent(col.name, el)
            }
          }
        }
      }
      // TODO check col.isRequired
      rowGroupObjectLists.values.forEach { lst ->
        lst.removeIf { obj ->
          obj.all { (_, v) -> v == null }
        }
      }

      val processed = postProcessQuestionData(type, rowGroupJson)
      questions.add(
        Question(
          id = generateId(),
          sourceId = "",
          type = type,
          data = jacksonObjectMapper().valueToTree(processed)
        )
      )
      startRow += rowGroup.size
    }

    return questions
  }

  fun postProcessQuestionData(type: String, json: HashMap<String, Any>): HashMap<String, Any> {
    if (type == "LineGraph") {
      val needsMin = !json.containsKey("yMin")
      val needsMax = !json.containsKey("yMax")
      if (needsMin || needsMax) {
        val data = when (val dataRef = json["data"]) {
          is List<*> -> dataRef
          else -> null
        }
        if (data != null) {
          val dataSeq = data.asSequence().mapNotNull { row ->
            when {
              row is HashMap<*, *> && row["value"] is Float -> row["value"] as Float
              else -> null
            }
          }
          val dataMin = dataSeq.min() ?: error("LineGraph processing can't find any data points")
          val dataMax = dataSeq.max() ?: error("LineGraph processing can't find any data points")
          val range = if (dataMin == dataMax) 1f else (dataMax - dataMin)
          val precision = 10.0f.pow(ceil(log10(range)) - 1)
          if (needsMin) json["yMin"] = floor(dataMin / precision) * precision
          if (needsMax) json["yMax"] = ceil(dataMax / precision) * precision
        }
      }
    }
    return json
  }

  private fun convertSheetToQuestions(sheet: GSheetLambdaData.Data): ColumnConversionResult {
    return sheet.valueRanges.fold(ColumnConversionResult()) { acc, vr ->
      try {
        acc.apply { questions.addAll(convertSingleSheetToQuestions(vr)) }
      } catch (e: IllegalStateException) {
        acc.apply { warnings.add(e.message ?: "Unknown error while importing ${vr.range}") }
      }
    }
  }
}
