// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service


import spray.json._

final case class CustomNoticeResponse
(
  channel: String,
  id: String,
  successful: Boolean
)

object CustomNoticeResponse {

  implicit object CustomNoticeResponse extends RootJsonFormat[CustomNoticeResponse] {
    def write(c: CustomNoticeResponse): JsValue = JsObject(
      "channel" -> JsString(c.channel),
      "id" -> JsString(c.id),
      "successful" -> JsBoolean(c.successful)
    )

    def read(value: JsValue): CustomNoticeResponse = {
      value.asJsObject.getFields("channel", "id", "successful") match {
        case Seq(JsString(channel), JsString(id), JsBoolean(succesful)) =>
          new CustomNoticeResponse(channel, id, succesful)
        case _ => throw new DeserializationException("CustomNoticeResponse expected")
      }
    }
  }

}

