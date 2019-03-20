// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import java.io.File
import java.lang.invoke.MethodHandles

import com.softwaremill.sttp.{multipart, _}
import org.slf4j.LoggerFactory

class ThreadPreverntionApiClient() {

  private val DOMAIN = Configuration.teApiServerAddress
  private val API_PATH = s"/tecloud/api/${Configuration.teApiVersion}/file/"
  private val TE_API_URL = "https://" + DOMAIN + API_PATH
  private val UPLOAD_URL = uri"$TE_API_URL".path("upload")
  private val QUERY_URL = uri"$TE_API_URL".path("query")
  private val DOWNLOAD_PATH = uri"$API_PATH".path("download")
  private val API_KEY = Configuration.teApiKey

  private val log = LoggerFactory.getLogger(MethodHandles.lookup.lookupClass)
  implicit val backend = HttpURLConnectionBackend()

  /**
    * //HTTP POST: https://<service_address>/tecloud/api/<version>/file/upload
    *
    * @param file
    * @return
    */
  def upload(file: File): com.softwaremill.sttp.Response[String] = {
    val fileName = file.getName //"example: MyFile.docx"
    val fileType = extractExt(fileName) //example: "docx"
    val reportType = "xml"
    val jsonPart =
      s"""
      {
        "request": {
          "file_name": "$fileName",
          "file_type": "$fileType",
          "features": [
            "te"
          ],
          "te": {
            "reports": [
              "$reportType"
            ]
          }
        }
      }""".stripMargin

    val result = sttp.multipartBody(
      multipart("request", jsonPart).contentType("application/json"),
      multipartFile("file", file).fileName(fileName).contentType("multipart/form-data")
    )
      .post(UPLOAD_URL)
      .header("Authorization", API_KEY)
      .send()
    result
  }

  private def extractExt(fileName: String): String = {
    val lastIndex = fileName.lastIndexOf(".")
    if (lastIndex == -1) "" else fileName.substring(lastIndex + 1)
  }

}
