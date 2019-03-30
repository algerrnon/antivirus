// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service.file_storage

import java.io._

import com.altuera.gms_antivirus_service.{Configuration, Utils}
import javax.servlet.annotation.WebServlet
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import spray.json.{JsObject, JsString}

import scala.util.control.NonFatal

@WebServlet(
  name = "FileDownload",
  displayName = "FileDownload",
  description = "FileDownload",
  urlPatterns = {
    Array("/getFile/*")
  },
  loadOnStartup = 1
)
class FileDownloadServlet extends HttpServlet {
  val defaultEncoding = System.getProperty("file.encoding")
  private val log = LoggerFactory.getLogger(this.getClass)
  val baseDirectoryForStorageDirs = Utils.createDirIfNotExist(Configuration.storageBaseDir)

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handle(request, response)
    } catch {
      case NonFatal(ex) =>
        log.error("error", ex)
        makeResponse(response, ex)
    }
  }

  def handle(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    response.reset()
    val fileId = request.getParameter("fileId")
    if (!fileId.isEmpty) {
      val fileFromStorage = Utils.getFileByBaseDirAndSeparateTempDirName(baseDirectoryForStorageDirs, fileId)
      if (fileFromStorage.isPresent) {

        downloadFile(fileFromStorage.get(), response)
      }
      else {
        makeResponse(response, "не найден файл")
      }

    } else {
      makeResponse(response, "Файл еще не загружен. Попробуйте позже")
    }
  }

  def downloadFile(file: File, response: HttpServletResponse): Unit = {
    val fileName: String = file.getName
    response.setCharacterEncoding("utf-8")
    response.setContentType("application/force-download")
    //response.setContentType("application/octet-stream")
    response.setHeader("Content-Length", "" + file.length())
    response.setHeader("Content-Description", "File Transfer")
    response.setHeader("Content-Transfer-Encoding", "binary")
    response.setDateHeader("Expires", 0)
    response.setHeader("Cache-Control", "must-revalidate, post-check=0, pre-check=0")
    response.setHeader("Pragma", "public")
    response.setStatus(HttpServletResponse.SC_OK)

    if (defaultEncoding != null && defaultEncoding == "UTF-8") response.addHeader("Content-Disposition",
      "attachment;filename=" + new String(fileName.getBytes("GBK"), "iso-8859-1"))
    else response.addHeader("Content-Disposition",
      "attachment;filename=" + new String(fileName.getBytes, "iso-8859-1"))

    val fileInputStream = new FileInputStream(file)
    val servletOutputStream = response.getOutputStream
    IOUtils.copy(fileInputStream, servletOutputStream)

    fileInputStream.close()
    servletOutputStream.flush()
    servletOutputStream.close()
  }

  private def makeResponse(response: HttpServletResponse, message: String): Unit = {
    response.resetBuffer
    response.setStatus(HttpServletResponse.SC_OK)
    response.setContentType("application/json")
    response.setCharacterEncoding("UTF-8")
    response.getOutputStream.print(JsObject("message" -> JsString(message)).toString)
    response.flushBuffer //marks response as committed -- if we don't do this the request will go through normally!
  }

  private def makeResponse(response: HttpServletResponse, throwable: Throwable): Unit = {
    var message = throwable.toString
    makeResponse(response, message)
  }
}



