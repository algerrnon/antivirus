// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service.tpapi

import java.io.File
import java.lang.invoke.MethodHandles
import java.nio.file.Files
import java.security.{DigestInputStream, MessageDigest}
import java.util.concurrent.TimeUnit

import com.altuera.gms_antivirus_service.tpapi.Models.QuotaResponse
import com.altuera.gms_antivirus_service.tpapi.Models.QuotaResponseItemProtocol._
import com.altuera.gms_antivirus_service.{Configuration, Utils, tpapi}
import com.softwaremill.sttp.{multipart, _}
import org.slf4j.LoggerFactory
import spray.json.{JsObject, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try


class Antivirus {

  private val baseDirectoryForTemporaryDirs = Utils.createDirIfNotExist(Configuration.uploadDir)
  private val DOMAIN = Configuration.avApiServerAddress
  private val API_PATH = s"/tecloud/api/${Configuration.avApiVersion}/file/"
  private val TE_API_URL = "https://" + DOMAIN + API_PATH
  private val UPLOAD_URL = uri"${TE_API_URL}upload"
  private val QUERY_URL = uri"${TE_API_URL}query"
  private val DOWNLOAD_PATH = uri"${TE_API_URL}download"
  private val QUOTA_PATH = uri"${API_PATH}quota"
  private val API_KEY = Configuration.avApiKey

  private val PAUSE_BETWEEN_ATTEMPTS_MILLISECONDS = Configuration.avRetryPauseBetweenAttemptsMilliseconds
  private val MAX_NUMBER_OF_TIMES = Configuration.avRetryMaxNumberOfTimes
  private val MAXIMUM_WAIT_TIME_SECONDS = Configuration.avRetryMaximumWaitTimeSeconds

  private val log = LoggerFactory.getLogger(MethodHandles.lookup.lookupClass)
  private implicit val backend = HttpURLConnectionBackend()

  /**
    * //HTTP POST: https://<service_address>/tecloud/api/<version>/file/upload
    *
    * @param file
    * @return
    */
  def upload(file: File, convertToPdf: Boolean = true): Boolean = {
    val fileName = file.getName //"example: MyFile.docx"
    val fileType = Utils.extractFileExt(fileName) //example: "docx"
    val md5 = getMd5(file)

    val reportType = "xml"
    val jsonPart = JsObject("request" ->
      JsObject(
        "file_name" -> JsString(fileName),
        "file_type" -> JsString(fileType),
        "md5" -> JsString(md5),
        "features" -> JsArray(JsString(ApiFeatures.THREAT_EMULATION),
          JsString(ApiFeatures.THREAT_EXTRACTION),
          JsString(ApiFeatures.ANTI_VIRUS)),

        "extraction" ->
          JsObject("method" -> JsString(if (convertToPdf) "pdf" else "clean"),
            "extracted_parts_codes" -> JsArray()), //todo
        "te" ->
          JsObject("reports" ->
            JsArray(
              JsString("te")
            )
          )
      )
    )

    val response = sttp.multipartBody(
      multipart("request", jsonPart.toString).contentType("application/json"),
      multipartFile("file", file).fileName(fileName).contentType("multipart/form-data")
    )
      .post(UPLOAD_URL)
      .header("Authorization", API_KEY)
      .send()

    response.body match {
      case Left(obj) => {
        log.warn(s"code=${response.code}; statusText=${response.statusText}")
        false
      }
      case Right(obj) => {
        log.info(response.unsafeBody)
        val apiCode = response.unsafeBody.parseJson.asJsObject.
          fields("response").asJsObject.
          fields("status").asJsObject.
          fields("code").convertTo[Int]

        apiCode == tpapi.ApiCodes.FOUND || apiCode == ApiCodes.UPLOAD_SUCCESS
      }
    }
  }

  def getMd5(file: File): String = {
    val md = MessageDigest.getInstance(HashTypes.MD5)
    val dis = new DigestInputStream(Files.newInputStream(file.toPath), md)
    // fully consume the inputstream
    while (dis.available > 0) {
      dis.read
    }
    dis.close
    md.digest.map(b => String.format("%02x", Byte.box(b))).mkString
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

  def threadExtractionQueryRetry(file: File, convertToPdf: Boolean): Option[ExtractionResultData] = {
    implicit val isDefined = retry.Success[Option[(Boolean, ExtractionResultData)]](x => x != null && x.isDefined && x.get._1)

    def doRetry() = {
      retry.Pause(
        delay = Duration(PAUSE_BETWEEN_ATTEMPTS_MILLISECONDS, TimeUnit.MILLISECONDS),
        max = MAX_NUMBER_OF_TIMES) //количество попыток (на единицу больше чем max)
        
        .apply(() => Future {
        log.trace("retry")
        threadExtractionQuery(file, convertToPdf)
      })
    }

    Try(Await.result(doRetry(), Duration(MAXIMUM_WAIT_TIME_SECONDS, TimeUnit.SECONDS))) match {
      case scala.util.Success(fx) => if (fx.isDefined) {
        Some(fx.get._2)
      } else {
        None
      }
      case _ => None
    }
  }

  /**
    *
    * extracted_parts_codes Values - Only relevant if method = clean
    *
    * @param convertToPdf
    * @return
    */
  def threadExtractionQuery(file: File, convertToPdf: Boolean): Option[(Boolean, ExtractionResultData)] = {
    val checksum = getMd5(file)
    val fileName = file.getName
    val fileType = Utils.extractFileExt(fileName)
    val extractedParts = Vector()
    val jsonPart = JsObject("request" ->
      JsObject(
        "md5" -> JsString(checksum),
        "file_name" -> JsString(fileName),
        "file_type" -> JsString(fileType),
        "features" -> JsArray(JsString(ApiFeatures.THREAT_EXTRACTION), JsString(ApiFeatures.ANTI_VIRUS)
        ),
        "extraction" ->
          JsObject("method" -> JsString(if (convertToPdf) "pdf" else "clean"),
            "extracted_parts_codes" -> JsArray(extractedParts)) //todo
      )
    )

    val response = sttp
      .body(jsonPart.toString)
      .post(QUERY_URL)
      .contentType("application/json")
      .header("Authorization", API_KEY)
      .send()

    response.body match {
      case Left(obj) => {
        log.warn(s"code=${response.code}; statusText=${response.statusText}")
        None
      }
      case Right(obj) => {
        log.info(response.unsafeBody)
        val jsResponse = response.unsafeBody.parseJson.asJsObject.fields("response").asJsObject
        val isOk = jsResponse.fields("status").asJsObject.fields("code").convertTo[Int] == (ApiCodes.FOUND)
        if (isOk) {
          val extractionResultData: ExtractionResultData = jsResponse.fields("extraction").convertTo[ExtractionResultData]
          Some(isOk, extractionResultData)
        }
        else {
          None
        }
      }
    }
  }

  def threadEmulationQueryRetry(file: File): Option[EmulationResultData] = {
    implicit val isDefined = retry.Success[Option[(Boolean, EmulationResultData)]](x => x != null && x.isDefined && x.get._1)

    def doRetry() = {
      retry.Pause(
        delay = Duration(PAUSE_BETWEEN_ATTEMPTS_MILLISECONDS, TimeUnit.MILLISECONDS),
        max = MAX_NUMBER_OF_TIMES)

        .apply(() => Future {
        log.trace("retry")
        threadEmulationQuery(file)
      })
    }

    Try(Await.result(doRetry(), Duration(MAXIMUM_WAIT_TIME_SECONDS, TimeUnit.SECONDS))) match {
      case scala.util.Success(fx) => if (fx.isDefined) {
        Some(fx.get._2)
      } else {
        None
      }
      case _ => None
    }
  }

  /**
    *
    *
    *
    * @return
    */
  def threadEmulationQuery(file: File): Option[(Boolean, EmulationResultData)] = {
    val checksum = getMd5(file)
    val fileName = file.getName
    val fileType = Utils.extractFileExt(fileName)
    val extractedParts = Vector()
    val jsonPart = JsObject("request" ->
      JsObject(
        "md5" -> JsString(checksum), //sha1
        "file_name" -> JsString(fileName),
        "file_type" -> JsString(fileType),
        "features" -> JsArray(JsString(ApiFeatures.THREAT_EMULATION)
        )
      )
    )

    val response = sttp
      .body(jsonPart.toString)
      .post(QUERY_URL)
      .contentType("application/json")
      .header("Authorization", API_KEY)
      .send()


    response.body match {
      case Left(obj) => {
        log.warn(s"code=${response.code}; statusText=${response.statusText}")
        None
      }
      case Right(obj) => {
        log.info(response.unsafeBody)
        val jsResponse = response.unsafeBody.parseJson.asJsObject.fields("response").asJsObject
        val isCorrect = jsResponse.fields("status").asJsObject.fields("code").convertTo[Int] == (ApiCodes.FOUND)
        if (isCorrect) {
          val verdict = jsResponse.fields("te").asJsObject.fields("combined_verdict").convertTo[String]
          Some(isCorrect, EmulationResultData(combined_verdict = verdict))
        }
        else {
          None
        }
      }
    }
  }
}

