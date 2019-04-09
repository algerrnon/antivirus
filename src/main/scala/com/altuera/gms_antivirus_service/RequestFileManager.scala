// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import java.io.{File, IOException}
import java.util.UUID

import com.softwaremill.sttp.Response
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.fileupload.util.Streams
import org.apache.commons.fileupload.{FileItemStream, FileUploadException}
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object RequestFileManager {
  val baseDirectoryForTemporaryDirs = Utils.createDirIfNotExist(Configuration.uploadBaseDir)
  val genesysFileUploader = new GenesysFileUploader(Configuration.genesysApiBaseUrl)
  val genesysMessenger = new GenesysMessenger(Configuration.genesysApiBaseUrl)
}

class RequestFileManager(request: HttpServletRequest,
                         servletResponse: HttpServletResponse) {

  private val log = LoggerFactory.getLogger(this.getClass)
  private val data: Data = getServletRequestData()

  def sendCustomNoticeToChat(message: String): Unit = {
    val id = String.valueOf(UUID.randomUUID)
    log.trace(s"id=$id")
    val cookieHeaderValue = data.requestHeaders.get("cookie")
    log.trace(s" cookie header = $cookieHeaderValue")
    val success = RequestFileManager.genesysMessenger.sendCustomNotice(
      id,
      data.clientId,
      message,
      data.secureKey,
      cookieHeaderValue)

    if (!success) {
      val message = "could not successfully send a message in a chat"
      log.warn(message)
      throw new SendCustomNoticeException(message)
    }
  }

  def uploadFileToChat(file: File): Response[String] = {
    val cookieHeaderValue = data.requestHeaders
      .getOrElse("cookie", throw new UploadFileToChatException("cookie header is empty"))
    RequestFileManager.genesysFileUploader.uploadFileToChat(file, data.secureKey, cookieHeaderValue)
  }

  def copyGenesysResponseToServletResponse(genesysFileUploadResponse: Response[String]): Unit = {
    val writer = servletResponse.getWriter
    genesysFileUploadResponse.body match {
      case Left(obj) => {
        log.warn(obj)
        writer.write(obj)
      }
      case Right(obj) => {
        log.trace(obj)
        writer.write(obj)
      }
    }
    servletResponse.setStatus(genesysFileUploadResponse.code)
  }

  /**
    * Метод удаляет рекурсивно все содержимое указанной директории по завершении работы
    */
  def deleteFolderRecursively(): Unit = {
    Utils.deleteFolderRecursively(data.file)
  }

  def validateServletRequestData(): Unit = {
    DataValidator.validateServletRequestData(this.data) match {
      case Success(lines) => log.trace("request is valid")
      case Failure(exception) =>
        log.error("Request not valid!")
        throw exception
    }
  }

  private def getServletRequestData(): Data = {
    try {
      validateMultipartContent()
      val requestHeaders: mutable.Map[String, String] = Utils.getRequestHeaders(request).asScala

      val upload = new ServletFileUpload()
      val iterator = upload.getItemIterator(request)

      var secureKey: String = ""
      var clientId: String = ""
      var file: Option[File] = None
      while (iterator.hasNext()) {
        val item = iterator.next()

        if (item.isFormField()) {
          val fieldName = item.getFieldName()
          log.trace("got a form field: " + fieldName)
          fieldName match {
            case "secureKey" => {
              secureKey = getFieldValue(item)
              log.trace("secure key = {}", secureKey)
            }
            case "clientId" => {
              clientId = getFieldValue(item)
              log.trace("clientId = {}", clientId)
            }
            case "operation" => {
              log.trace("operation = {}", getFieldValue(item))
            }
            case unexpectedField => log.trace("Unexpected field name: " + unexpectedField)
          }
        }
        else if (file.isEmpty) {
          file = Some(readFileDataFromRequestAndWriteToTempFile(item))
        }
      }
      new Data(secureKey, clientId, requestHeaders, file.getOrElse(throw new GetDataFromServletRequestException("Failed to upload file")))
    }
    catch {
      case ex: IOException =>
        log.error("Failed to upload file", ex)
        throw new GetDataFromServletRequestException("IOException, Failed to upload file")
      case ex: FileUploadException =>
        log.error("Failed to upload file", ex)
        throw new GetDataFromServletRequestException("FileUploadException, Failed to upload file")

    }
  }

  private def getFieldValue(item: FileItemStream): String = {
    val stream = item.openStream()
    val result = Streams.asString(stream)
    stream.close()
    result
  }

  private def validateMultipartContent(): Unit = {
    val isMultipart = ServletFileUpload.isMultipartContent(request)
    if (!isMultipart) {
      val message = "No file uploaded, Current request is not a multipart request"
      log.warn(message)
      throw new MultipartRequestValidationException(message)
    } else {
      log.trace("multipart request is correct")
    }
  }

  private def readFileDataFromRequestAndWriteToTempFile(item: FileItemStream): File = {

    val fieldName = item.getFieldName()
    log.trace(s"fieldName = $fieldName")
    val fileName = item.getName()
    log.trace(s"fileName = $fileName")
    val stream = item.openStream()
    log.trace("open input stream")
    val contentType = item.getContentType
    log.trace("Content type: " + contentType)
    log.trace(s"stream available data -> ${stream.available()} octets")
    // creates the temp directory and temp file
    val tempFile: File = Utils.createNewTempDirAndTempFileInDir(RequestFileManager.baseDirectoryForTemporaryDirs, fileName)
    FileUtils.copyInputStreamToFile(stream, tempFile)
    log.trace(s"copy input stream to file. tempFile= $tempFile")
    stream.close()
    log.trace("input stream is closed")
    tempFile
  }

  object DataValidator {

    def validFile(file: File): Try[File] = {
      if (file == null || !file.exists) {
        log.trace("uploaded file is null or not exist")
        Failure(new ValidateServletRequestDataException("uploaded file is null or not exist"))
      }
      else {
        log.trace("file is filled")
        Success(file)
      }
    }

    def validSecureKey(secureKey: String): Try[String] = {
      if (secureKey == null || secureKey.isEmpty) {
        log.trace("secure key is not filled")
        Failure(new ValidateServletRequestDataException("secure key is null or empty"))
      }
      else {
        log.trace("secure key is filled")
        Success(secureKey)
      }
    }

    def validClientId(clientId: String): Try[String] = {
      if (clientId == null || clientId.isEmpty) {
        log.trace("clientId is not filled")
        Failure(new ValidateServletRequestDataException("clientId is null or empty"))
      }
      else {
        log.trace("clientId is filled")
        Success(clientId)
      }
    }

    def validRequestHeaders(requestHeaders: mutable.Map[String, String]): Try[mutable.Map[String, String]] = {
      if (requestHeaders == null || requestHeaders.isEmpty) {
        log.trace("request headers is empty")
        Failure(new ValidateServletRequestDataException("requestHeaders is null or empty"))
      }
      if (requestHeaders.contains("cookie")) {
        val cookie = requestHeaders.get("cookie")
        if (cookie == null || cookie.isEmpty) {
          log.trace("cookie header is not defined")
          Failure(new ValidateServletRequestDataException("cookie header is null or empty"))
        }
        else {
          log.trace(s"cookie header is defined and contains the value $cookie")
        }
      }
      else {
        log.trace("cookie header is not exist")
        Failure(new ValidateServletRequestDataException("cookie header is not exist"))
      }
      log.trace("all required request headers are defined")
      Success(requestHeaders)
    }

    def validateServletRequestData(data: Data): Try[Data] = {

      for {
        secureKey <- validSecureKey(data.secureKey)
        clientId <- validClientId(data.clientId)
        file <- validFile(data.file)
        requestHeaders <- validRequestHeaders(data.requestHeaders)
      } yield data
    }
  }

  def getOriginalFile(): File = {
    data.file
  }

}

