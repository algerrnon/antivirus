// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import java.io.File

import com.altuera.gms_antivirus_service.av._
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
        threadEmulation(manager, originalFile)
      }
      //todo Оригиналы храним в Application несколько дней (нужно предусмотреть хранилище).
      //manager.deleteFolderRecursively()
    }
    catch {
      case NonFatal(ex) =>
        log.error("error", ex)
        makeErrorResponse(ex.getLocalizedMessage, servletResponse)
    }
  }

  private def threadEmulation(manager: RequestReplyManager, originalFile: File) = {
    antivirus.upload(originalFile, List(ApiFeatures.THREAT_EMULATION))
    val emulation: Option[EmulationResultData] = antivirus.threadEmulationQueryRetry(originalFile)
    processResultEmulation(manager, originalFile, emulation, Configuration.messageIsSafeFile)
  }

  private def processResultEmulation(manager: RequestReplyManager, originalFile: File, emulation: Option[EmulationResultData], message: String) = {
    val verdict = emulation.map(_.combined_verdict).getOrElse("")
    if (verdict.equalsIgnoreCase(VerdictValues.BENIGN)) {
      //Если вердикт отрицательный (не вирус) – направляем в GMS оригинальный документ.
      manager.sendCustomNoticeToChat(message)
      val genesysUploadResponse = manager.uploadFileToChat(originalFile)
      manager.copyGenesysResponseToServletResponse(genesysUploadResponse)
    } else if (verdict.equalsIgnoreCase(VerdictValues.MALICIOUS)) {
      //Если вердикт положительный (вирус) – направляем кастомные сообщения получателю и отправителю.
      manager.sendCustomNoticeToChat(Configuration.messageIsInfectedFile)
    } else {
      //что-то пошло не так, например сервис возвращает VerdictValues.UNKNOWN
    }
  }

  private def threadExtraction(manager: RequestReplyManager, originalFile: File, fileType: String) = {
    val extractionMethod = if (forConvertToPdf(fileType)) "pdf" else "clean"
    antivirus.upload(originalFile, List(ApiFeatures.THREAT_EXTRACTION), extractionMethod)
    val threadExtraction = antivirus.threadExtractionQueryRetry(originalFile, extractionMethod)

    val file: File = downloadFile(originalFile.getName, threadExtraction)

    //Полученный очищенный файл направляем получателю через GMS вместе с кастомным сообщением, что доставлена безопасная копия.
    manager.sendCustomNoticeToChat(Configuration.messageIsSafeFile)
    val genesysUploadResponse = manager.uploadFileToChat(file)
    manager.copyGenesysResponseToServletResponse(genesysUploadResponse)

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
    response.setStatus(STATUS_CODE_FAILED)
    response.setContentType("application/json")
    response.getWriter.write(JsObject("result" -> JsString("error"), "message" -> JsString(message)).toString())
  }

}


