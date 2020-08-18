package com.dylanburati.jsonbin2

import io.javalin.http.HttpResponseException
import org.eclipse.jetty.http.HttpStatus

class TooManyRequestsResponse(message: String, val retryAfterSeconds: Long) :
  HttpResponseException(HttpStatus.TOO_MANY_REQUESTS_429, message)
