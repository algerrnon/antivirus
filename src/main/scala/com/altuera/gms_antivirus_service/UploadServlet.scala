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
      val manager = new RequestReplyManager(servletRequest, servletResponse)
      manager.validateServletRequestData()
      val originalFile = manager.getOriginalFile()
      val fileExtension = Utils.extractFileExt(originalFile.getName)
      if (forThreadExtraction(fileExtension)) {
        threadExtraction(manager, originalFile, fileExtension)
      } else {
        threadEmulation(manager, originalFile, servletResponse)
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

  private def threadEmulation(manager: RequestReplyManager, originalFile: File, httpServletResponse: HttpServletResponse) = {
    antivirus.upload(originalFile, List(ApiFeatures.THREAT_EMULATION))
    log.trace("uploaded file to THREAT_EMULATION")
    val emulation: Option[EmulationResultData] = antivirus.threadEmulationQueryRetry(originalFile)
    log.trace(s"got emulation results $emulation")
    processResultEmulation(manager, originalFile, emulation, Configuration.messageIsSafeFile, httpServletResponse)
    log.trace("processed emulation result")
  }

  private def processResultEmulation(manager: RequestReplyManager, originalFile: File, emulation: Option[EmulationResultData], message: String, httpServletResponse: HttpServletResponse) = {
    val verdict = emulation.map(_.combined_verdict).getOrElse("")
    log.trace(s"combined_verdict = $verdict")
    if (verdict.equalsIgnoreCase(VerdictValues.BENIGN)) {
      log.trace("negative verdict (not a virus) - send the original document to the GMS")
      manager.sendCustomNoticeToChat(message)
      log.trace(s"message $message sent to chat")
      val genesysUploadResponse = manager.uploadFileToChat(originalFile)
      log.trace(s"file $originalFile loaded into chat")
      manager.copyGenesysResponseToServletResponse(genesysUploadResponse)
      log.trace("Copied Genesys API response to the response sent to the initiator of the file download")
    } else if (verdict.equalsIgnoreCase(VerdictValues.MALICIOUS)) {
      log.trace("If the verdict is positive (virus), we send custom messages to the recipient and the sender.")
      manager.sendCustomNoticeToChat(Configuration.messageIsInfectedFile)
      makeErrorResponse(Configuration.messageIsInfectedFile, httpServletResponse)
    } else {
      log.trace(s"emulation did not return the result Verdict = $verdict")
      //что-то пошло не так, например сервис возвращает VerdictValues.UNKNOWN
    }
  }

  private def threadExtraction(manager: RequestReplyManager, originalFile: File, fileType: String) = {
    val extractionMethod = if (forConvertToPdf(fileType)) ExtractionMethod.PDF else ExtractionMethod.CLEAN
    antivirus.upload(originalFile, List(ApiFeatures.THREAT_EXTRACTION), extractionMethod)
    val threadExtraction = antivirus.threadExtractionQueryRetry(originalFile, extractionMethod)
    if (threadExtraction.map(_.extract_result.equalsIgnoreCase(ExtractResultsStatuses.CP_EXTRACT_RESULT_SUCCESS))
      .getOrElse(false)) {
      val file: File = downloadFile(originalFile.getName, threadExtraction)

      log.trace("The received cleared file is sent to the recipient with the message that a secure copy is delivered.")
      manager.sendCustomNoticeToChat(Configuration.messageIsSafeFile)
      val genesysUploadResponse = manager.uploadFileToChat(file)
      manager.copyGenesysResponseToServletResponse(genesysUploadResponse)

    }
    else {
      log.warn(s"ThreatPrevention API could not perform extraction. Response $threadExtraction")
    }

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

  override def destroy() = {
    log.info("============ GMS Antivirus Service status = STOPPED ============")
  }
}


