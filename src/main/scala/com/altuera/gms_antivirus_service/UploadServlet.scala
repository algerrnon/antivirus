// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import java.io.File

import com.altuera.gms_antivirus_service.av._
import javax.servlet.ServletConfig
import javax.servlet.annotation.{MultipartConfig, WebServlet}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.slf4j.LoggerFactory
import spray.json.{JsObject, JsString}

import scala.util.control.NonFatal

final case class MultipartRequestValidationException(message: String = "", cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class GetDataFromServletRequestException(message: String = "", cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class ValidateServletRequestDataException(message: String = "", cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class CreateTempDirAndTempFileException(message: String = "", cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class CopyInputStreamToFileException(message: String = "", cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class SendCustomNoticeException(message: String = "", cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class UploadFileToChatException(message: String = "", cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class CopyGenesysResponseToServletResponseException(message: String = "", cause: Throwable = None.orNull)
  extends Exception(message, cause)

@WebServlet(
  name = "CheckUpload",
  displayName = "CheckUpload",
  description = "Check the content of file before upload",
  urlPatterns = {
    Array("/upload/*")
  },
  loadOnStartup = 1)
class UploadServlet extends HttpServlet {

  val STATUS_CODE_FAILED = 500
  val STATUS_CODE_OK = 200
  val antivirus = new Antivirus()
  val baseDirectoryForStorageTemporaryDirs = Utils.createDirIfNotExist(Configuration.storageBaseDir)
  private val log = LoggerFactory.getLogger(this.getClass)

  override def init(config: ServletConfig) = {
    log.info("============ GMS Antivirus Service status = STARTED ============")
  }

  @MultipartConfig(
    fileSizeThreshold = 10485760, //1024 * 1024 * 10, //10 MB
    maxFileSize = 52428800, //1024 * 1024 * 50, //50 MB
    maxRequestSize = 104857600) //1024 * 1024 * 100) //100MB
  override def doPost(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): Unit = {
    servletResponse.setStatus(STATUS_CODE_OK)
    servletResponse.setContentType("application/json")

    try {
      val baseUrl = getBaseUrl(servletRequest)
      log.trace(s"base url = $baseUrl")
      val manager = new RequestReplyManager(servletRequest, servletResponse)
      manager.validateServletRequestData()
      val originalFile = manager.getOriginalFile()
      val fileExtension = Utils.extractFileExt(originalFile.getName)
      if (forThreadExtraction(fileExtension)) {
        //В зависимости от типа файла будет очистка с сохранением исходного формата или конвертация в PDF.
        threadExtraction(manager, originalFile, fileExtension, baseUrl, servletResponse)
      } else {
        threadEmulation(manager, originalFile, baseUrl, servletResponse)
      }
      //todo удаляем файлы из uploadBaseDir по завершении работы JVM, файлы storageBaseDir в настоящий момент не удаляем
      manager.deleteFolderRecursively()
    }
    catch {
      case NonFatal(ex) =>
        log.error("error", ex)
        makeErrorResponse(ex.getLocalizedMessage, servletResponse)
    }
  }

  private def threadEmulation(manager: RequestReplyManager,
                              originalFile: File,
                              baseUrl: String,
                              httpServletResponse: HttpServletResponse) = {
    //генерируем директорию в хранилище, в которую будет помещен файл после проверки
    val storageDir = Utils.createTempDirectoryInsideBaseDir(baseDirectoryForStorageTemporaryDirs)
    //генеририруем ссылку по которой можно будет загрузить файл
    val storageHttpLink: String = generateStorageHttpLink(baseUrl, storageDir)
    //"Запущена проверка на вирусы оригинала файла. После успешной проверки оригинал файла будет доступен по ссылке"
    val message = Configuration.messageCheckingStartedAndLinkToOriginal.concat(storageHttpLink)
    log.trace(s"storageHttpLink = $storageHttpLink")
    log.trace("Configuration.messageCheckingStartedAndLinkToOriginal")
    manager.sendCustomNoticeToChat(message)
    antivirus.upload(originalFile, List(ApiFeatures.THREAT_EMULATION))
    log.trace("uploaded file to THREAT_EMULATION")
    val emulation: Option[EmulationResultData] = antivirus.threadEmulationQueryRetry(originalFile)
    log.trace(s"got emulation results $emulation")
    processResultEmulation(manager, originalFile, storageDir, storageHttpLink, emulation, httpServletResponse)
    log.trace("processed emulation result")
  }

  private def processResultEmulation(manager: RequestReplyManager,
                                     originalFile: File,
                                     storageDir: File,
                                     storageHttpLink: String,
                                     emulation: Option[EmulationResultData],
                                     httpServletResponse: HttpServletResponse) = {
    val verdict = emulation.map(_.combined_verdict).getOrElse("")
    log.trace(s"combined_verdict = $verdict")
    if (verdict.equalsIgnoreCase(VerdictValues.BENIGN)) {
      log.trace("negative verdict (not a virus) - send the original document to the GMS")
      //копируем из upload файл в storage и он становится доступен для скачивания по ссылке
      val storageFile = Utils.copyFileToDir(originalFile, storageDir)
      manager.sendCustomNoticeToChat(Configuration.messageIsSafeFileAndLinkToOriginal.concat(storageHttpLink))
      log.trace("Configuration.messageIsSafeFileAndLinkToOriginal")
      val genesysUploadResponse = manager.uploadFileToChat(originalFile)
      log.trace(s"file $originalFile loaded into chat")
      manager.copyGenesysResponseToServletResponse(genesysUploadResponse)
      log.trace("Copied Genesys API response to the response sent to the initiator of the file download")
    } else if (verdict.equalsIgnoreCase(VerdictValues.MALICIOUS)) {
      log.trace("If the verdict is positive (virus), we send custom messages to the recipient and the sender.")
      log.trace("Configuration.messageIsInfectedFile")
      manager.sendCustomNoticeToChat(Configuration.messageIsInfectedFile)
      makeErrorResponse(Configuration.messageIsInfectedFile, httpServletResponse)
    } else {
      log.debug(s"emulation did not return the result Verdict = $verdict")
      //что-то пошло не так, например сервис возвращает VerdictValues.UNKNOWN
      manager.sendCustomNoticeToChat(Configuration.messageFileNotFound)
      makeErrorResponse(Configuration.messageFileNotFound, httpServletResponse)
    }
  }

  private def threadExtraction(manager: RequestReplyManager,
                               originalFile: File,
                               fileExtension: String,
                               baseUrl: String,
                               httpServletResponse: HttpServletResponse
                              ) = {
    //определяем нужно ли конвертировать файл в PDF или необходимо попытаться очистить файл от вредоносных участков
    val extractionMethod = if (forConvertToPdf(fileExtension)) ExtractionMethod.PDF else ExtractionMethod.CLEAN
    //загружаем файл для проверки
    antivirus.upload(originalFile, List(ApiFeatures.THREAT_EXTRACTION), extractionMethod)
    //опрашиваем Thread Prevention API повторяющимися запросами о наличии результата проверки
    val threadExtraction = antivirus.threadExtractionQueryRetry(originalFile, extractionMethod)
    if (threadExtraction.exists(_.extract_result.equalsIgnoreCase(ExtractResultsStatuses.CP_EXTRACT_RESULT_SUCCESS))) {
      val file: File = downloadFile(originalFile.getName, threadExtraction)
      //Отправляем сообщение"Вам был отправлен файл. Антивирусная система предоставит вам безопасную копию файла")
      manager.sendCustomNoticeToChat(Configuration.messageIsSafeFileCopy)
      log.trace("Configuration.messageIsSafeFileCopy")
      //Полученный очищенный файл направляем получателю
      val genesysUploadResponse = manager.uploadFileToChat(file)
      log.trace("The received cleared file is sent to the recipient with the message that a secure copy is delivered.")

      //Если файл был forThreadEmulation, то в кастомном сообщении должна содержаться ссылка/кнопка запросить оригинал.
      if (forThreadEmulation(fileExtension)) {
        log.trace("forThreadEmulation")

        //генерируем директорию в хранилище, в которую будет помещен файл после проверки
        val storageDir = Utils.createTempDirectoryInsideBaseDir(baseDirectoryForStorageTemporaryDirs)
        //генеририруем ссылку по которой можно будет загрузить файл
        val storageHttpLink: String = generateStorageHttpLink(baseUrl, storageDir)

        //"Запущена проверка на вирусы оригинала файла. После успешной проверки оригинал файла будет доступен по ссылке"
        val message = Configuration.messageCheckingStartedAndLinkToOriginal.concat(storageHttpLink)
        log.trace(s"storageHttpLink = $storageHttpLink")
        manager.sendCustomNoticeToChat(message)
        threadEmulation(manager, originalFile, baseUrl, httpServletResponse)
      }
      else {
        manager.copyGenesysResponseToServletResponse(genesysUploadResponse)
      }
    }
    else {
      log.warn(s"ThreatPrevention API could not perform extraction. Response $threadExtraction")
      log.trace(s"extract_result is not CP_EXTRACT_RESULT_SUCCESS, extracted_file_download_id not defined")
      manager.sendCustomNoticeToChat(Configuration.messageFileNotFound)
      makeErrorResponse(Configuration.messageFileNotFound, httpServletResponse)
    }

  }

  private def generateStorageHttpLink(baseUrl: String, storageDir: File) = {
    val storageHttpLink = baseUrl.concat("/getFile?fileId=").concat(storageDir.getName)
    storageHttpLink
  }

  private def downloadFile(originalFileName: String, threadExtraction: Option[ExtractionResultData]) = {
    val fileId: String = threadExtraction.flatMap(_.extracted_file_download_id)
      .getOrElse(throw new Exception("file id not found"))
    val outputFileName: String = threadExtraction.map(_.output_file_name)
      .getOrElse(originalFileName)

    antivirus.download(fileId, outputFileName)
      .getOrElse(throw new Exception("file not found"))
  }

  private def forConvertToPdf(fileExtension: String): Boolean = {
    Configuration.avExtensionsForConvertToPdf.contains(fileExtension)
  }

  private def forThreadEmulation(fileExtension: String): Boolean = {
    Configuration.avExtensionsForThreadEmulation.contains(fileExtension)
  }

  private def forThreadExtraction(fileExtension: String): Boolean = {
    Configuration.avExtensionsForThreadExtraction.contains(fileExtension)
  }

  private def makeErrorResponse(message: String, response: HttpServletResponse) = {
    log.trace("error message")
    response.setCharacterEncoding("UTF-8")
    response.setContentType("application/json; charset=utf-8")
    response.setStatus(STATUS_CODE_FAILED)
    response.setContentType("application/json")
    response.getWriter.write(JsObject("result" -> JsString("error"), "message" -> JsString(message)).toString())
  }

  /**
    * Возвращаемое значение не содержит символ "/" в конце строки
    *
    * @param request
    * @return
    */
  def getBaseUrl(request: HttpServletRequest): String = {
    request.getRequestURL.substring(0, request.getRequestURL.length - request.getRequestURI.length) + request.getContextPath
  }

  override def destroy() = {
    log.info("============ GMS Antivirus Service status = STOPPED ============")
  }
}


