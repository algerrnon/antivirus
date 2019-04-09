package com.altuera.gms_antivirus_service

import java.lang.invoke.MethodHandles
import java.util.UUID

import com.softwaremill.sttp._
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._
import spray.json._

class GenesysMessenger(url: String) {
  private val log = LoggerFactory.getLogger(MethodHandles.lookup.lookupClass)

  private val baseUri = uri"$url"
  private val sendCustomNoticeUri = baseUri.path("genesys/cometd")

  implicit val backend = HttpURLConnectionBackend()

  def sendCustomNotice(id: String, clientId: String, message: String, secureKey: String, cookieHeaderValue: Option[String]): Boolean = {

    val requestBody =
      s"""{
         |  "channel": "/service/chatV2/request-chat-v2",
         |  "data": {
         |  "operation": "customNotice",
         |  "message": "$message",
         |  "secureKey": "$secureKey"
         |  },
         |  "id": 7,
         |  "clientId": "$clientId"
         |}""".stripMargin

    var request = sttp
      .post(sendCustomNoticeUri)
      .contentType("application/json;charset=utf-8")
      .body(requestBody)

    request = cookieHeaderValue.map(request.header("cookie", _)).getOrElse(request)
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
}
