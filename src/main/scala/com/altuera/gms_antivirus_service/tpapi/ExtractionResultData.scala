// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service.tpapi

import spray.json.{DefaultJsonProtocol, RootJsonFormat}

final case class ExtractionResultData(method: String, //example "pdf"
                                      extract_result: String, // example "CP_EXTRACT_RESULT_SUCCESS"
                                      extracted_file_download_id: Option[String], //example "1cf99a33-e3f2-4f73-ad29-31c0afb419db"
                                      output_file_name: String, //example "Selection_023.cleaned.png.pdf"
                                      time: Option[String], //example "0.733"
                                      extract_content: Option[String])

object ExtractionResultData extends DefaultJsonProtocol {
  implicit val jsonFormat: RootJsonFormat[ExtractionResultData] = jsonFormat6(ExtractionResultData.apply)

}
