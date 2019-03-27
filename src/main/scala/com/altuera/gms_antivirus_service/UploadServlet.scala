// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import java.io.File

import com.altuera.gms_antivirus_service.tpapi._
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
  private val log = LoggerFactory.getLogger(this.getClass)

  @MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 10, //10 MB
    maxFileSize = 1024 * 1024 * 50, //50 MB
    maxRequestSize = 1024 * 1024 * 100) //100MB
  override def doPost(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): Unit = {
    servletResponse.setStatus(STATUS_CODE_OK)
    servletResponse.setContentType("application/json")
    val teClient = new Antivirus()

    try {
      val manager = new RequestReplyManager(servletRequest, servletResponse)
      manager.validateServletRequestData()
      val originalFile = manager.getOriginalFile()
      val fileType = detectFileType(originalFile)

      var threadExtraction: Option[ExtractionResultData] = None
      if (fileType == FileTypes.IMAGE || fileType == FileTypes.EXCEL) {
        threadExtraction = teClient.threadExtractionQueryRetry(originalFile, convertToPdf = false)
      }
      else {
        threadExtraction = teClient.threadExtractionQueryRetry(originalFile, convertToPdf = true)
      }

      if (threadExtraction.isDefined) {
        val teResultObj = threadExtraction.get
        val success = teResultObj.extract_result.equalsIgnoreCase(ExtractResultsStatuses.CP_EXTRACT_RESULT_SUCCESS)
        if (teResultObj.extracted_file_download_id.isDefined) {
          val resFile = {
            teClient.download(teResultObj.extracted_file_download_id.getOrElse(""), Some(teResultObj.output_file_name))
          }
          if (resFile.isDefined) {
            if (fileType == FileTypes.DOC) {
              //доставлена безопасная копия с линком для получения оригинального файла
              manager.sendCustomNoticeToChat(Configuration.messageIsSafeFileAndLinkToOriginal)
            }
            else {
              //доставлена безопасная копия
              manager.sendCustomNoticeToChat(Configuration.messageIsSafeFile)
            }
            val genesysUploadResponse = manager.uploadFileToChat(resFile.get)
            manager.copyGenesysResponseToServletResponse(genesysUploadResponse)
          }
        }
        else {
          log.trace("очищенный файл не получен: нечего удалять, или произошла ошибка, или сервис не отвечает более 60 секунд, или файл зашифрован, или другая причина") //todo https://sc1.checkpoint.com/documents/TPAPI/CP_1.0_ThreatPreventionAPI_APIRefGuide/html_frameset.htm?topic=documents/TPAPI/CP_1.0_ThreatPreventionAPI_APIRefGuide/189793
          //manager.sendCustomNoticeToChat(Configuration.messageFileNotFound)
        }

        if (fileType == FileTypes.DOC) {
          manager.sendCustomNoticeToChat("Ожидание проверки на вирусы")
          val emulation: Option[EmulationResultData] = teClient.threadEmulationQueryRetry(originalFile)
          if (emulation.isDefined) {
            if (!emulation.get.combined_verdict.equalsIgnoreCase(VerdictValues.BENIGN)) {
              manager.uploadFileToChat(originalFile)
            }
            else {
              manager.sendCustomNoticeToChat(Configuration.messageIsInfectedFile)
            }
          }
          else {
            log.trace("не удалось вовремя получить ответ")
          }
        }
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

  def detectFileType(file: File): String = {
    val filelExt = Utils.extractFileExt(file.getName)
    if (Configuration.avTypesImages.contains(filelExt)) {
      FileTypes.IMAGE
    }
    else if (Configuration.avTypesDocs.contains(filelExt)) {
      FileTypes.DOC
    }
    else if (Configuration.avTypesOthers.contains(filelExt)) {
      FileTypes.OTHER
    }
    else {
      FileTypes.UNSUPPORTED
    }
  }

  private def makeErrorResponse(message: String, response: HttpServletResponse) = {
    response.setStatus(STATUS_CODE_FAILED)
    response.setContentType("application/json")
    response.getWriter.write(JsObject("result" -> JsString("error"), "message" -> JsString(message)).toString())
  }

}


