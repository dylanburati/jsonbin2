package com.dylanburati.jsonbin2.entities

import com.dylanburati.jsonbin2.Config
import com.dylanburati.jsonbin2.entities.conversations.ConversationService
import com.dylanburati.jsonbin2.entities.messages.MessageService
import com.dylanburati.jsonbin2.entities.questions.QuestionService
import com.dylanburati.jsonbin2.entities.remote.imgur.ImgurService
import com.dylanburati.jsonbin2.entities.users.UserService
import kotliquery.Session
import kotliquery.sessionOf
import org.flywaydb.core.Flyway

object ServiceContainer {
  private fun initSession(): Session {
    return Config.Database.run {
      Flyway.configure().dataSource(url, user, password).load().migrate()
      sessionOf(url, user, password)
    }
  }

  val session = this.initSession()
  val userService = UserService(this)
  val conversationService = ConversationService(this)
  val messageService = MessageService(this)

  // Stateless
  val imgurService = ImgurService(this)

  // App: guessr
  val questionService =
    QuestionService(this)
}
