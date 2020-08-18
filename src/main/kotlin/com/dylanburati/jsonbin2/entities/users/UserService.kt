package com.dylanburati.jsonbin2.entities.users

import at.favre.lib.crypto.bcrypt.BCrypt
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.dylanburati.jsonbin2.Config
import com.dylanburati.jsonbin2.entities.BaseService
import com.dylanburati.jsonbin2.entities.ServiceContainer
import io.javalin.http.ForbiddenResponse
import io.javalin.http.UnauthorizedResponse
import kotliquery.queryOf
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*

class UserService(container: ServiceContainer) : BaseService(container) {
  private val bcryptHasher: BCrypt.Hasher
    get() {
      return BCrypt.with(
        BCrypt.Version.VERSION_2Y,
        secureRandom,
        LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2Y)
      )
    }

  private val bcryptVerifier: BCrypt.Verifyer
    get() {
      return BCrypt.verifyer(
        BCrypt.Version.VERSION_2Y,
        LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2Y)
      )
    }

  private val jwtVerifier = JWT.require(Algorithm.HMAC256(Config.JWT.secret)).build()

  fun getById(id: String): User? {
    val findOneQuery = queryOf("""SELECT * FROM "user" WHERE "id" = ? LIMIT 1""", id)
      .map { row ->
        User(
          id = row.string("id"),
          username = row.string("username"),
          authType = User.AuthType.valueOf(row.string("auth_type")),
          password = row.stringOrNull("password")
        )
      }
      .asSingle
    return session.run(findOneQuery)
  }

  fun getByUsername(username: String): User? {
    val findOneQuery = queryOf("""SELECT * FROM "user" WHERE "username" = ? LIMIT 1""", username)
      .map { row ->
        User(
          id = row.string("id"),
          username = row.string("username"),
          authType = User.AuthType.valueOf(row.string("auth_type")),
          password = row.stringOrNull("password")
        )
      }
      .asSingle
    return session.run(findOneQuery)
  }

  fun save(user: User) {
    val rowsAffected = session.run(
      queryOf(
        """
        INSERT INTO "user" ("id", "username", "auth_type", "password")
        VALUES (?, ?, ?, ?)
        """,
        user.id,
        user.username,
        user.authType.name,
        user.password
      ).asUpdate
    )

    if (rowsAffected != 1) throw Exception("Could not insert record")
  }

  fun createUser(username: String, password: String): User {
    val exists = getByUsername(username)

    check(exists == null) { "Username exists" }

    val id = generateId()
    val hashed = bcryptHasher.hash(10, password.toCharArray())
    val user = User(id, username, User.AuthType.BCRYPT, hashed.toString(UTF_8))

    save(user)

    return user
  }

  fun createGuest(): User {
    val id = generateId()
    val username = "guest${secureRandom.nextInt(1000000).toString().padStart(6, '0')}"
    val user = User(id, username, User.AuthType.NONE, null)

    save(user)

    return user
  }

  fun authenticateUser(username: String, password: String): User {
    val user = getByUsername(username)

    checkNotNull(user) { "User does not exist" }
    check(user.authType === User.AuthType.BCRYPT) { "Can not log into guest account" }

    val result =
      bcryptVerifier.verify(password.toCharArray(), user.password)
        ?: throw Exception("Unable to verify password")

    if (!result.verified) throw ForbiddenResponse("Incorrect password")
    return user
  }

  fun generateJWT(user: User): String {
    val alg = Algorithm.HMAC256(Config.JWT.secret)
    return JWT.create()
      .withExpiresAt(Date(System.currentTimeMillis() + Config.JWT.expiresInMillis))
      .withClaim("userId", user.id)
      .sign(alg)
  }

  fun verifyJWT(token: String): User {
    try {
      val decoded = jwtVerifier.verify(token)
      val claim = decoded.getClaim("userId")
      val id = claim.asString() ?: throw UnauthorizedResponse("Invalid JWT (missing userId)")
      return this.getById(id) ?: throw UnauthorizedResponse("Invalid JWT (deleted userId)")
    } catch (e: JWTDecodeException) {
      throw UnauthorizedResponse("Invalid JWT")
    }
  }
}
