package com.dylanburati.jsonbin2.entities

import com.dylanburati.jsonbin2.entities.conversations.ConversationService
import com.dylanburati.jsonbin2.entities.messages.MessageService
import com.dylanburati.jsonbin2.entities.questions.QuestionService
import com.dylanburati.jsonbin2.entities.remote.imgur.ImgurService
import com.dylanburati.jsonbin2.entities.users.UserService
import me.liuwj.ktorm.database.Database

class ServiceContainer(val builder: ServiceBuilder, val database: Database) {
  val userService = UserService(this)
  val conversationService = ConversationService(this)
  val messageService = MessageService(this)

  // Stateless
  val imgurService = ImgurService(this)

  // App: guessr
  val questionService =
    QuestionService(this)
}
