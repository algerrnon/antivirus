// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import java.io.File
import java.lang.invoke.MethodHandles

import com.softwaremill.sttp._
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._
import spray.json._

class GenesysApiClient(url: String) {
  private val baseUri = uri"$url"
  private val sendCustomNoticeUri = baseUri.path("genesys/cometd")
  private val uploadFileToChatUri = baseUri.path("genesys/2/chat-ntf")

  private val log = LoggerFactory.getLogger(MethodHandles.lookup.lookupClass)
  implicit val backend = HttpURLConnectionBackend()

  def sendCustomNotice(id: String, clientId: String, message: String, secureKey: String, cookieHeaderValue: String): Boolean = {

    val requestBody =
      s"""{
         |  "channel": "/service/chatV2/request-chat-v2",
         |  "data": {
         |  "operation": "customNotice",
         |  "message": "$message",
         |  "secureKey": "$secureKey"
         |  },
         |  "id": "$id",
         |  "clientId": "$clientId"
         |}""".stripMargin

    val request = sttp
      .post(sendCustomNoticeUri)
      .contentType("application/json;charset=utf-8")
      .body(requestBody)
      .header("cookie", cookieHeaderValue)
    log.trace(s"send CustomNotice (Genesys Chat API) $request")
    val response = request.send()

    response.body match {
      case Left(obj) => {
        log.warn(obj)
        false
      }
      case Right(obj) => {
        log.trace(obj)
        //example response body:  [{"channel":"/service/chatV2/request-chat-v2","id":"123abc","error":"402::Unknown client","successful":false}]
        val headOption = obj.parseJson.convertTo[Seq[CustomNoticeResponse]].headOption
        headOption match {
          case Some(value) => {
            log.trace("success")
            value.successful
          }
          case None => {
            log.trace("fail")
            false
          }
        }
      }
    }
  }

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
