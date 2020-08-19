package com.dylanburati.jsonbin2.entities.remote.imgur

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ImgurAsset(
  var id: String,
  var type: String,
  var link: String,
  var deletehash: String?
)
