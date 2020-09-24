package com.dylanburati.jsonbin2.entities.users

import at.favre.lib.crypto.bcrypt.BCrypt
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.dylanburati.jsonbin2.Config
import com.dylanburati.jsonbin2.entities.BaseService
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.nonNull
import io.javalin.http.ForbiddenResponse
import io.javalin.http.UnauthorizedResponse
import me.liuwj.ktorm.dsl.*
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
    val findOneQuery = database.from(User.TABLE).select()
      .where { (User.TABLE.id eq id) }
      .limit(0, 1)
      .map { row ->
        User(
          id = row.nonNull(User.TABLE.id),
          username = row.nonNull(User.TABLE.username),
          authType = User.AuthType.valueOf(row.nonNull(User.TABLE.authType)),
          password = row[User.TABLE.password]
        )
      }
    return findOneQuery.firstOrNull()
  }

  fun getByUsername(username: String): User? {
    val findOneQuery = database.from(User.TABLE).select()
      .where { (User.TABLE.username eq username) }
      .limit(0, 1)
      .map { row ->
        User(
          id = row.nonNull(User.TABLE.id),
          username = row.nonNull(User.TABLE.username),
          authType = User.AuthType.valueOf(row.nonNull(User.TABLE.authType)),
          password = row[User.TABLE.password]
        )
      }
    return findOneQuery.firstOrNull()
  }

  fun save(user: User) {
    database.insert(User.TABLE) {
      set(it.id, user.id)
      set(it.username, user.username)
      set(it.authType, user.authType.name)
      set(it.password, user.password)
    }
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
    } catch (e: JWTVerificationException) {
      throw UnauthorizedResponse("Invalid JWT")
    }
  }
}
