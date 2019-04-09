// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import java.io.File
import java.lang.invoke.MethodHandles

import com.softwaremill.sttp._
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._
import spray.json._

class GenesysFileUploader(url: String) {
  private val baseUri = uri"$url"
  private val uploadFileToChatUri = baseUri.path("genesys/2/chat-ntf")

  private val log = LoggerFactory.getLogger(MethodHandles.lookup.lookupClass)
  implicit val backend = HttpURLConnectionBackend()


  def uploadFileToChat(file: File, secureKey: String, cookieHeaderValue: String): com.softwaremill.sttp.Response[String] = {
    val result = sttp.multipartBody(
      multipart("operation", "fileUpload").contentType("multipart/form-data"),
      multipart("secureKey", secureKey).contentType("multipart/form-data"),
      multipartFile("file", file).fileName(file.getName).contentType("multipart/form-data")
    )
      .post(uploadFileToChatUri)
      .header("cookie", cookieHeaderValue)
      .send()
    log.trace(s"send file to chat (Genesys Chat API), result = $result")
    result
  }
}
