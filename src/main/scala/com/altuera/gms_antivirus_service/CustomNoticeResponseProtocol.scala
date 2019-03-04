package com.altuera.gms_antivirus_service


import spray.json.{DefaultJsonProtocol, JsonFormat, _}


case class CustomNoticeResponse
(
	channel: String,
	id: String,
	successful: Boolean
)

object CustomNoticeResponseProtocol extends DefaultJsonProtocol {
	implicit val customNoticeResponseFormat: JsonFormat[CustomNoticeResponse] = jsonFormat3(CustomNoticeResponse)

	def main(args: Array[String]): Unit = {
		val jsonStr = """[{"channel":"channel-channel","id":"idid","successful":true}]"""
		val json = jsonStr.parseJson
		val customNoticeResponsesList = json.convertTo[List[CustomNoticeResponse]]
		customNoticeResponsesList.foreach(println)
	}
}


//object CustomNoticeResponse {
//
//	implicit object CustomNoticeResponse extends RootJsonFormat[CustomNoticeResponse] {
//		def write(c: CustomNoticeResponse): JsValue = JsObject(
//			"channel" -> JsString(c.channel),
//			"id" -> JsString(c.id),
//			"successful" -> JsBoolean(c.successful)
//		)
//
//		def read(value: JsValue): CustomNoticeResponse = {
//			value.asJsObject.getFields("channel", "id", "successful") match {
//				case Seq(JsString(channel), JsString(id), JsBoolean(succesful)) =>
//					new CustomNoticeResponse(channel, id, succesful)
//				case _ => throw new DeserializationException("CustomNoticeResponse expected")
//			}
//		}
//	}
