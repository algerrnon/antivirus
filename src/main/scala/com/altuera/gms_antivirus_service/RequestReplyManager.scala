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

object RequestReplyManager {
  val baseDirectoryForTemporaryDirs = Utils.createDirIfNotExist(Configuration.uploadBaseDir)
  val genesysClient = new GenesysApiClient(Configuration.genesysApiBaseUrl)

}

class RequestReplyManager(request: HttpServletRequest,
                          servletResponse: HttpServletResponse) {

  private val log = LoggerFactory.getLogger(this.getClass)
  private val data: Data = getServletRequestData()

  def sendCustomNoticeToChat(message: String): Unit = {
    val id = String.valueOf(UUID.randomUUID)
    log.trace(s"id=$id")
    val cookieHeaderValue = data.requestHeaders
      .getOrElse("cookie", throw new SendCustomNoticeException("cookie header is empty"))
    log.trace(s"заголовок cookie $cookieHeaderValue")
    val success = RequestReplyManager.genesysClient.sendCustomNotice(
      id,
      data.clientId,
      message,
      data.secureKey,
      cookieHeaderValue)

    if (!success) {
      val message = "не смогли успешно отправить сообщение в чат"
      log.warn(message)
      throw new SendCustomNoticeException(message)
    }
  }

  def uploadFileToChat(file: File): Response[String] = {
    val cookieHeaderValue = data.requestHeaders
      .getOrElse("cookie", throw new UploadFileToChatException("cookie header is empty"))
    RequestReplyManager.genesysClient.uploadFileToChat(file, data.secureKey, cookieHeaderValue)
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
      log.warn(message)
      throw new MultipartRequestValidationException(message)
    } else {
      log.trace("получен корректный multipart request")
    }
  }

  private def readFileDataFromRequestAndWriteToTempFile(item: FileItemStream): File = {

    val fieldName = item.getFieldName()
    log.trace(s"получили имя поля содержащего файл из данных сервлета fieldName = $fieldName")
    val fileName = item.getName()
    log.trace(s"получили имя файла fileName = $fileName")
    val stream = item.openStream()
    log.trace("открыли входной поток")
    val contentType = item.getContentType
    log.trace("Content type: " + contentType)
    log.trace("Got an uploaded file: " + fieldName + ", name = " + fileName)
    log.trace("Try to write stream to file : \n->" + stream.available() + " octets \n")
    // creates the temp directory and temp file
    val tempFile: File = Utils.createNewTempDirAndTempFileInDir(RequestReplyManager.baseDirectoryForTemporaryDirs, fileName)
    log.trace(s"создали временный файл $tempFile")
    FileUtils.copyInputStreamToFile(stream, tempFile)
    log.trace(s"копировали входной поток в файл $tempFile")
    stream.close()
    log.trace("закрыли входной поток")
    tempFile
  }

  object DataValidator {

    def validFile(file: File): Try[File] = {
      if (file == null || !file.exists) {
        log.trace("загружаемый для проверки файл отсутствует")
        Failure(new ValidateServletRequestDataException("uploaded file is null or not exist"))
      }
      else {
        log.trace("загружаемый для проверки файл в наличии")
        Success(file)
      }
    }

    def validSecureKey(secureKey: String): Try[String] = {
      if (secureKey == null || secureKey.isEmpty) {
        log.trace("secure key отсутствует")
        Failure(new ValidateServletRequestDataException("secure key is null or empty"))
      }
      else {
        log.trace("secure key в наличии")
        Success(secureKey)
      }
    }

    def validClientId(clientId: String): Try[String] = {
      if (clientId == null || clientId.isEmpty) {
        log.trace("clientId отсутствует")
        Failure(new ValidateServletRequestDataException("clientId is null or empty"))
      }
      else {
        log.trace("clientId в наличии")
        Success(clientId)
      }
    }

    def validRequestHeaders(requestHeaders: mutable.Map[String, String]): Try[mutable.Map[String, String]] = {
      if (requestHeaders == null || requestHeaders.isEmpty) {
        log.trace("нет данных о заголовках запроса")
        Failure(new ValidateServletRequestDataException("requestHeaders is null or empty"))
      }
      if (requestHeaders.contains("cookie")) {
        val cookie = requestHeaders.get("cookie")
        if (cookie == null || cookie.isEmpty) {
          log.trace("не определен заголовок cookie")
          Failure(new ValidateServletRequestDataException("cookie header is null or empty"))
        }
        else {
          log.trace(s"заголовок cookie определен и содержит значение $cookie")
        }
      }
      else {
        log.trace("заголовок cookie отсутствует")
        Failure(new ValidateServletRequestDataException("cookie header is not exist"))
      }
      log.trace("все обязательные заголовки запроса определены")
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

