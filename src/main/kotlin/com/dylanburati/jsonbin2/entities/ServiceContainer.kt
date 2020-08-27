package com.dylanburati.jsonbin2.entities

import com.dylanburati.jsonbin2.Config
import com.dylanburati.jsonbin2.entities.conversations.ConversationService
import com.dylanburati.jsonbin2.entities.messages.MessageService
import com.dylanburati.jsonbin2.entities.questions.QuestionService
import com.dylanburati.jsonbin2.entities.remote.imgur.ImgurService
import com.dylanburati.jsonbin2.entities.users.UserService
import kotliquery.HikariCP
import kotliquery.sessionOf
import org.flywaydb.core.Flyway

class ServiceContainer {
  companion object {
    init {
      Config.Database.run {
        Flyway.configure().dataSource(url, user, password).load().migrate()
        HikariCP.default(url, user, password)
      }
    }
  }

  val session = sessionOf(HikariCP.dataSource())
  val userService = UserService(this)
  val conversationService = ConversationService(this)
  val messageService = MessageService(this)

  // Stateless
  val imgurService = ImgurService(this)

  // App: guessr
  val questionService =
    QuestionService(this)

  fun close() {
    session.close()
  }
}
