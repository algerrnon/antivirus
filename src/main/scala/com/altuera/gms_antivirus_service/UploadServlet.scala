// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import javax.servlet.annotation.{MultipartConfig, WebServlet}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.slf4j.LoggerFactory
import spray.json.{JsObject, JsString}

final case class MultipartRequestValidationException(private val message: String = "",
                                                     private val cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class GetDataFromServletRequestException(private val message: String = "",
                                                    private val cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class ValidateServletRequestDataException(private val message: String = "",
                                                     private val cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class CreateTempDirAndTempFileException(private val message: String = "",
                                                   private val cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class CopyInputStreamToFileException(private val message: String = "",
                                                private val cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class SendCustomNoticeException(private val message: String = "",
                                           private val cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class UploadFileToChatException(private val message: String = "",
                                           private val cause: Throwable = None.orNull)
  extends Exception(message, cause)

final case class CopyGenesysResponseToServletResponseException(private val message: String = "",
                                                               private val cause: Throwable = None.orNull)
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

  private val log = LoggerFactory.getLogger(this.getClass)

  @MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 10, //10 MB
    maxFileSize = 1024 * 1024 * 50, //50 MB
    maxRequestSize = 1024 * 1024 * 100) //100MB
  override def doPost(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): Unit = {
    servletResponse.setStatus(200)
    servletResponse.setContentType("application/json")

    try {
      val manager = new RequestReplyManager(servletRequest, servletResponse)
      manager.validateServletRequestData()
      manager.sendCustomNoticeToChat()
      val genesysUploadResponse = manager.uploadFileToChat()
      manager.copyGenesysResponseToServletResponse(genesysUploadResponse)
      manager.deleteFolderRecursively()
    }
    catch {
      case ex: Throwable => {
        log.error("error", ex)
        makeErrorResponse(ex.getLocalizedMessage, servletResponse)
      }
    }
  }

  private def makeErrorResponse(message: String, response: HttpServletResponse) = {
    response.setStatus(500)
    response.setContentType("application/json")
    response.getWriter.write(JsObject("result" -> JsString("error"), "message" -> JsString(message)).toString())
  }
}


