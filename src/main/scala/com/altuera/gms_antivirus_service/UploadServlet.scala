package com.altuera.gms_antivirus_service

import javax.servlet.annotation.{MultipartConfig, WebServlet}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.slf4j.LoggerFactory
import spray.json.{JsObject, JsString}

case class MultipartRequestValidationException(message: String) extends Exception(message)

case class GetDataFromServletRequestException(message: String) extends Exception(message)

case class ValidateServletRequestDataException(message: String) extends Exception(message)

case class CreateTempDirAndTempFileException(message: String) extends Exception(message)

case class CopyInputStreamToFileException(message: String) extends Exception(message)

case class SendCustomNoticeException(message: String) extends Exception(message)

case class UploadFileToChatException(message: String) extends Exception(message)

case class CopyGenesysResponseToServletResponseException(message: String) extends Exception(message)

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

  def makeErrorResponse(message: String, response: HttpServletResponse) = {
    response.setStatus(500)
    response.setContentType("application/json")
    response.getWriter.write(JsObject("result" -> JsString("error"), "message" -> JsString(message)).toString())
  }
}


