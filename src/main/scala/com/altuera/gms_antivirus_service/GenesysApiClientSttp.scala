package com.altuera.gms_antivirus_service

import java.io.File
import java.lang.invoke.MethodHandles

import com.google.common.io.Resources
import com.softwaremill.sttp._
import org.slf4j.LoggerFactory

class GenesysApiClientSttp(url: String) {
  private val baseUri = uri"$url"
  private val sendCustomNoticeUri = baseUri.path("genesys/cometd")
  private val uploadFileToChatUri = baseUri.path("genesys/2/chat-ntf")

  private val log = LoggerFactory.getLogger(MethodHandles.lookup.lookupClass)
  implicit val backend = HttpURLConnectionBackend()

  def sendCustomNotice(id: String, clientId: String, message: String, secureKey: String, cookieHeaderValue: String): Boolean = {

    var requestBody =
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
    val response = sttp
      .post(sendCustomNoticeUri)
      .contentType("application/json;charset=utf-8")
      .body(requestBody)
      .header("cookie", cookieHeaderValue)
      .send()

    response.body match {
      case Left(obj) => {
        log.warn(obj)
        return false
      }
      case Right(obj) => {
        log.trace(obj)
        return true
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
    result
  }
}

object GenesysApiClientSttp {
  def main(args: Array[String]): Unit = {
    var secureKey = "NSgiKiYfAV4XDRMzYCVtP15EQghXRncrYWcnDUIRTQQTcn5lOXpVEQ9EVTZ0eBBodShGQ0NVQ3d+L29zXEFLFSBFcB4VDnNcRSo="
    val fileUrl = Resources.getResource("api_client/test.txt")
    val file = new File(fileUrl.getPath)
    val cookie = "BAYEUX_BROWSER=son96j6ur94c86re"
    val genesysApiClient: GenesysApiClientSttp = new GenesysApiClientSttp("http://gen01:8090/")
    println(genesysApiClient.uploadFileToChat(file, secureKey, cookie))

    val id = "sdfvsfvsfdvdf"
    val clientId = "g17gx1cpj4le9v1ua6k2ockma3k"
    val message = "sdfvsfdvsf"
    println(genesysApiClient.sendCustomNotice(id, clientId, message, secureKey, cookie))
  }
}
