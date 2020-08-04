package com.dylanburati.jsonbin2.entities.users

import at.favre.lib.crypto.bcrypt.BCrypt
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies
import com.dylanburati.jsonbin2.entities.BaseService
import com.dylanburati.jsonbin2.entities.ServiceContainer
import io.javalin.http.UnauthorizedResponse
import kotliquery.queryOf
import org.postgresql.util.Base64
import java.lang.Exception
import java.nio.charset.StandardCharsets.UTF_8
import java.security.SecureRandom

class UserService(private val container: ServiceContainer): BaseService() {
  private val session = container.session

  private val hasher: BCrypt.Hasher
    get() {
      return BCrypt.with(
        BCrypt.Version.VERSION_2Y,
        secureRandom,
        LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2Y)
      )
    }

  private val verifyer: BCrypt.Verifyer
    get() {
      return BCrypt.verifyer(
        BCrypt.Version.VERSION_2Y,
        LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2Y)
      )
    }

  fun createUser(username: String, password: String): User {
    val existsQuery = queryOf("""SELECT * FROM "user" WHERE "username" = ? LIMIT 1""", username)
      .map { row -> row.string("id") }
      .asSingle
    val exists = session.run(existsQuery)

    check(exists == null) { "Username exists" }

    val id = generateId()
    val hashed = hasher.hash(10, password.toCharArray())
    val user = User(id, username, User.AuthType.BCRYPT, hashed.toString(UTF_8))

    val rowsAffected = session.run(queryOf(
      """
        INSERT INTO "user" ("id", "username", "auth_type", "password")
        VALUES (?, ?, ?, ?)
      """,
      user.id,
      user.username,
      user.authType.name,
      user.password
    ).asUpdate)

    if (rowsAffected != 1) throw Exception("Could not insert record")

    return user;
  }

  fun authenticateUser(username: String, password: String): User {
    val findOneQuery = queryOf("""SELECT * FROM "user" WHERE "username" = ? LIMIT 1""", username)
      .map { row ->
        User(
          row.string("id"),
          row.string("username"),
          User.AuthType.valueOf(row.string("auth_type")),
          row.string("password")
        )
      }
      .asSingle
    val user = session.run(findOneQuery)

    checkNotNull(user) { "User does not exist" }

    val result =
      verifyer.verify(password.toCharArray(), user.password) ?: throw Exception("Unable to verify password")

    if (!result.verified) throw UnauthorizedResponse("Incorrect password")
    return user;
  }
}
