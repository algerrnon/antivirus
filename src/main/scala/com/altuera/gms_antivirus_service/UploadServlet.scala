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
  private val log = LoggerFactory.getLogger(this.getClass)

  @MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 10, //10 MB
    maxFileSize = 1024 * 1024 * 50, //50 MB
    maxRequestSize = 1024 * 1024 * 100) //100MB
  override def doPost(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): Unit = {
    servletResponse.setStatus(STATUS_CODE_OK)
    servletResponse.setContentType("application/json")
    val antivirus = new Antivirus()
    try {
      val manager = new RequestReplyManager(servletRequest, servletResponse)
      manager.validateServletRequestData()
      val originalFile = manager.getOriginalFile()
      val fileType = detectFileType(originalFile)
      if (fileType == FileTypes.IMAGE || fileType == FileTypes.DOC) {

        val convertToPdf = fileType != FileTypes.IMAGE & fileType != FileTypes.EXCEL

        antivirus.upload(originalFile, convertToPdf)
        val threadExtraction = antivirus.threadExtractionQueryRetry(originalFile, convertToPdf)

        val fileId: String = threadExtraction.flatMap(_.extracted_file_download_id)
          .getOrElse(throw new Exception("file id not found"))
        val outputFileName: String = threadExtraction.map(_.output_file_name)
          .getOrElse(originalFile.getName)
        val file = antivirus.download(fileId, outputFileName)
          .getOrElse(throw new Exception("file not found"))

        //Если файл был документом,
        if (fileType == FileTypes.DOC) {
          //то в кастомном сообщении должна содержаться ссылка/кнопка запросить оригинал.
          //потому предварительно мы должны получить заключение от thead emulation о безопасности оригинального файла

          //Отправляем получателю кастомное сообщение «Ожидание проверки на вирусы»
          manager.sendCustomNoticeToChat(Configuration.messagePleaseWait)

          val emulation: Option[EmulationResultData] = antivirus.threadEmulationQueryRetry(originalFile)
          val verdict = emulation.map(_.combined_verdict).getOrElse("")
          if (verdict.equalsIgnoreCase(VerdictValues.BENIGN)) {
            //Если вердикт отрицательный (не вирус) – направляем в GMS оригинальный документ.
            manager.sendCustomNoticeToChat(Configuration.messageIsSafeFileAndLinkToOriginal)
            val genesysUploadResponse = manager.uploadFileToChat(originalFile)
            manager.copyGenesysResponseToServletResponse(genesysUploadResponse)
          } else if (verdict.equalsIgnoreCase(VerdictValues.MALICIOUS)) {
            //Если вердикт положительный (вирус) – направляем кастомные сообщения получателю и отправителю.
            manager.sendCustomNoticeToChat(Configuration.messageIsInfectedFile)
          } else {
            //что-то пошло не так, например сервис возвращает VerdictValues.UNKNOWN
          }

        } else { //Полученный очищенный файл направляем получателю через GMS вместе с кастомным сообщением, что доставлена безопасная копия.
          manager.sendCustomNoticeToChat(Configuration.messageIsSafeFile)
          val genesysUploadResponse = manager.uploadFileToChat(originalFile)
          manager.copyGenesysResponseToServletResponse(genesysUploadResponse)
        }
      }
      else {
        val emulation: Option[EmulationResultData] = antivirus.threadEmulationQueryRetry(originalFile)
        val verdict = emulation.map(_.combined_verdict).getOrElse("")
        if (verdict.equalsIgnoreCase(VerdictValues.BENIGN)) {
          //Если вердикт отрицательный (не вирус) – направляем в GMS оригинальный файл
          manager.sendCustomNoticeToChat(Configuration.messageIsSafeFile)
          val genesysUploadResponse = manager.uploadFileToChat(originalFile)
          manager.copyGenesysResponseToServletResponse(genesysUploadResponse)
        } else if (verdict.equalsIgnoreCase(VerdictValues.MALICIOUS)) {
          //Если вердикт положительный (вирус) – направляем кастомные сообщения получателю и отправителю.
          manager.sendCustomNoticeToChat(Configuration.messageIsInfectedFile)
        } else {
          //что-то пошло не так, например сервис возвращает VerdictValues.UNKNOWN
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


