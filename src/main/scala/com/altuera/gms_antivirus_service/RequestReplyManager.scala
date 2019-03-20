// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import java.io.{File, IOException}
import java.util
import java.util.UUID

import com.softwaremill.sttp.Response
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.fileupload.util.Streams
import org.apache.commons.fileupload.{FileItemStream, FileUploadException}
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object RequestReplyManager {
  val baseDirectoryForTemporaryDirs = Utils.createDirIfNotExist(Configuration.uploadDir)
  val genesysClient = new GenesysApiClientSttp(Configuration.genesysApiBaseUrl)

}

class RequestReplyManager(request: HttpServletRequest,
                          servletResponse: HttpServletResponse) {

  private val log = LoggerFactory.getLogger(this.getClass)
  private val data: Data = getServletRequestData()

  def sendCustomNoticeToChat(): Unit = {
    val id = String.valueOf(UUID.randomUUID)
    val cookieHeaderValue = data.requestHeaders.get("cookie")
    val success = RequestReplyManager.genesysClient.sendCustomNotice(
      id,
      data.clientId,
      Configuration.customNoticeMessage,
      data.secureKey,
      cookieHeaderValue)

    if (!success) {
      val message = "No send custom notice"
      log.warn(message)
      throw new SendCustomNoticeException(message)
    }
  }

  def uploadFileToChat(): Response[String] = {
    val cookieHeaderValue = data.requestHeaders.get("cookie")
    RequestReplyManager.genesysClient.uploadFileToChat(data.file, data.secureKey, cookieHeaderValue)
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
      val requestHeaders: util.Map[String, String] = Utils.getRequestHeaders(request)

      val upload = new ServletFileUpload()
      val iterator = upload.getItemIterator(request)

      var secureKey: String = ""
      var clientId: String = ""
      var file: Option[File] = None
      while (iterator.hasNext()) {
        val item = iterator.next()

        if (item.isFormField()) {
          val fieldName = item.getFieldName()
          log.trace("Got a form field: " + fieldName)
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
      log.trace(message)
      throw new MultipartRequestValidationException(message)
    }
  }

  private def readFileDataFromRequestAndWriteToTempFile(item: FileItemStream): File = {

    val fieldName = item.getFieldName()
    val fileName = item.getName()
    val stream = item.openStream()
    val contentType = item.getContentType
    log.trace("Content type: " + contentType)
    log.trace("Got an uploaded file: " + fieldName + ", name = " + fileName)
    log.trace("Try to write stream to file : \n->" + stream.available() + " octets \n")
    // creates the temp directory and temp file
    val tempFile: File = Utils.createNewTempDirAndTempFileInDir(RequestReplyManager.baseDirectoryForTemporaryDirs, fileName)
    FileUtils.copyInputStreamToFile(stream, tempFile)
    stream.close()
    tempFile
  }

  object DataValidator {

    def validFile(file: File): Try[File] = {
      if (file == null || !file.exists) {
        Failure(new ValidateServletRequestDataException("uploaded file is null or not exist"))
      }
      else {
        Success(file)
      }
    }

    def validSecureKey(secureKey: String): Try[String] = {
      if (secureKey == null || secureKey.isEmpty) {
        Failure(new ValidateServletRequestDataException("secure key is null or empty"))
      }
      else {
        Success(secureKey)
      }
    }

    def validClientId(clientId: String): Try[String] = {
      if (clientId == null || clientId.isEmpty) {
        Failure(new ValidateServletRequestDataException("clientId is null or empty"))
      }
      else {
        Success(clientId)
      }
    }

    def validRequestHeaders(requestHeaders: util.Map[String, String]): Try[util.Map[String, String]] = {
      if (requestHeaders == null || requestHeaders.isEmpty) {
        Failure(new ValidateServletRequestDataException("requestHeaders is null or empty"))
      }
      if (requestHeaders.containsKey("cookie")) {
        val cookie = requestHeaders.get("cookie")
        if (cookie == null || cookie.isEmpty) {
          Failure(new ValidateServletRequestDataException("cookie header is null or empty"))
        }
      }
      else {
        Failure(new ValidateServletRequestDataException("cookie header is not exist"))
      }
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

}

