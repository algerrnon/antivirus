// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service


import spray.json.{DefaultJsonProtocol, JsonFormat}


case class CustomNoticeResponse
(
  channel: String,
  id: String,
  successful: Boolean
)

object CustomNoticeResponseProtocol extends DefaultJsonProtocol {
  implicit val customNoticeResponseFormat: JsonFormat[CustomNoticeResponse] = jsonFormat3(CustomNoticeResponse)
}
