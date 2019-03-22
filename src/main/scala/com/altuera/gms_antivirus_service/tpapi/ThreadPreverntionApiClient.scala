// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service.tpapi

import java.io.File
import java.lang.invoke.MethodHandles

import com.altuera.gms_antivirus_service.tpapi.Models.QuotaResponse
import com.altuera.gms_antivirus_service.tpapi.Models.QuotaResponseItemProtocol._
import com.altuera.gms_antivirus_service.{Configuration, Utils}
import com.softwaremill.sttp.{multipart, _}
import org.slf4j.LoggerFactory
import spray.json.{JsObject, _}

class ThreadPreverntionApiClient() {

  val baseDirectoryForTemporaryDirs = Utils.createDirIfNotExist(Configuration.uploadDir)
  private val DOMAIN = Configuration.teApiServerAddress
  private val API_PATH = s"/tecloud/api/${Configuration.teApiVersion}/file/"
  private val TE_API_URL = "https://" + DOMAIN + API_PATH
  private val UPLOAD_URL = uri"$TE_API_URL".path("upload")
  private val QUERY_URL = uri"$TE_API_URL".path("query")
  private val DOWNLOAD_PATH = uri"$API_PATH".path("download")
  private val QUOTA_PATH = uri"$API_PATH".path("quota")
  private val API_KEY = Configuration.teApiKey

  private val log = LoggerFactory.getLogger(MethodHandles.lookup.lookupClass)
  implicit val backend = HttpURLConnectionBackend()

  /**
    * //HTTP POST: https://<service_address>/tecloud/api/<version>/file/upload
    * @param file
    * @return
    */
  def upload(file: File): com.softwaremill.sttp.Response[String] = {
    val fileName = file.getName //"example: MyFile.docx"
    val fileType = extractExt(fileName) //example: "docx"
    val reportType = "xml"
    val jsonPart = JsObject("request" ->
      JsObject(
        "file_name" -> JsString(fileName),
        "file_type" -> JsString(fileType),
        "features" -> JsArray(JsString("te")),
        "te" ->
          JsObject("reports" ->
            JsArray(JsString(reportType))
          )
      )
    )

    val result = sttp.multipartBody(
      multipart("request", jsonPart.toString).contentType("application/json"),
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

  /**
    * //HTTP GET: https://<service_address>/tecloud/api/<version>/file/quota
    *
    * @return
    */
  def quota(): Option[QuotaResponse] = {
    val response = sttp
      .get(QUOTA_PATH)
      .header("Authorization", API_KEY)
      .send()
    response.body match {
      case Left(obj) => {
        log.warn(obj)
        None
      }
      case Right(obj) => {
        log.trace(obj)
        Some(obj.parseJson.asJsObject.fields("response").convertTo[QuotaResponse])
      }
    }
  }

  /**
    * Этот метод предназначен для загрузки файлов созданных сервисом Check Point Thread Prevention
    * Это могут быть отчеты, вывод Threat Emulation sandbox и др.
    *
    * HTTP GET: https://<service_address>/tecloud/api/<version>/file/download?id=<id>
    *
    * @param id для того чтобы загрузить файл вам необходимо указать его идентификатор, который задаётся этим значением
    *           Изначально идентификатор получается из запросов #query и #upload
    * @return
    */
  def download(id: String, fileName: Option[String]): Option[File] = {
    val file = Utils.createNewTempDirAndTempFileInDir(baseDirectoryForTemporaryDirs, fileName.getOrElse(id))
    val response = sttp
      .get(DOWNLOAD_PATH.params("id" -> id))
      .header("Authorization", API_KEY)
      .response(asFile(file = file, overwrite = true))
      .send()
    Some(file)
  }

  /**
    * * HTTP POST: https://<service_address>/tecloud/api/<version>/file/query
    * * @param checksum md5 | sha1 | sha256 (Only one digest is mandatory.Note - On local gateways, only sha1 digest format is supported.)
    *
    * @param fileName    Mandatory for extraction, it is the same file as given with the file digest
    * @param fileType    Service identifies the type. Note - This file is required in requests to local gateways.
    * @param reportTypes pdf | xml | tar | summary
    *                    PDF reports are not available in the new Threat Emulation reports format.
    *                    Requesting for PDF and summary reports simultaneously is not supported.
    * @return
    */
  def query(checksum: String, fileName: Option[String], fileType: Option[String], reportTypes: Vector[String]): String = {
    val jsonPart = JsObject("request" ->
      JsObject(
        "md5" -> JsString(checksum),
        "file_name" -> JsString(fileName.getOrElse("")),
        "file_type" -> JsString(fileType.getOrElse("")),
        "features" -> JsArray(JsString("te"), JsString("av"), JsString("tex")),
        "quota" -> JsBoolean(true), //If true, response delivers the quota data (for cloud services only).
        "te" ->
          JsObject("reports" ->
            JsArray(reportTypes.map(elem => JsString(elem)))
          )
      )
    )

    val response = sttp
      .get(QUERY_URL)
      .header("Authorization", API_KEY)
      .send()
    response.unsafeBody
  }
}
