package com.dylanburati.jsonbin2

import me.liuwj.ktorm.dsl.QueryRowSet
import me.liuwj.ktorm.schema.Column

fun <C: Any> QueryRowSet.nonNull(column: Column<C>): C {
  return this[column] ?: error("Null encountered for column ${column.name}")
}
