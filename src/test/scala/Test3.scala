import java.io.File

import com.altuera.gms_antivirus_service.tpapi.{Antivirus, ExtractResultsStatuses, ExtractionResultData}

object Test3 {

  def main(args: Array[String]): Unit = {
    val client = new Antivirus()
    val file: File = new File("/home/algernon/av/112313.png")
    val convertToPdf = false

    println(client.upload(file, convertToPdf))
    val extractionResultData: Option[ExtractionResultData] = client.threadExtractionQueryRetry(file, convertToPdf)
    if (extractionResultData.isDefined) {
      println(extractionResultData.get)
      val extrResultObj = extractionResultData.get
      val success = extrResultObj.extract_result.equalsIgnoreCase(ExtractResultsStatuses.CP_EXTRACT_RESULT_SUCCESS)
      if (extrResultObj.extracted_file_download_id.isDefined) {
        val resFile = client.download(extrResultObj.extracted_file_download_id.getOrElse(""), Some(extrResultObj.output_file_name))
        println(resFile)
      }
      else {
        println("очищенный файл не получен: нечего удалять, или произошла ошибка, или сервис не отвечает более 60 секунд, или файл зашифрован, или другая причина")
        //todo https://sc1.checkpoint.com/documents/TPAPI/CP_1.0_ThreatPreventionAPI_APIRefGuide/html_frameset.htm?topic=documents/TPAPI/CP_1.0_ThreatPreventionAPI_APIRefGuide/189793
      }
    }
  }
}
