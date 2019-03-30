// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service.file_storage

import java.io._
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import com.altuera.gms_antivirus_service.{Configuration, Utils}
import javax.servlet.annotation.WebServlet
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import spray.json.{JsNumber, JsObject, JsString}

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
    //response.setContentType("application/force-download")
    //response.setContentType("application/octet-stream")
    response.setHeader("Content-Length", "" + file.length())
    response.setHeader("Content-Description", "File Transfer")
    response.setHeader("Content-Transfer-Encoding", "binary")
    response.setStatus(HttpServletResponse.SC_OK)

    val utf8FileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString).replaceAll("\\+", "%20")
    val contentDisposition = "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + utf8FileName
    response.addHeader("Content-Disposition", contentDisposition)

    val fileInputStream = new FileInputStream(file)
    val servletOutputStream = response.getOutputStream
    IOUtils.copy(fileInputStream, servletOutputStream)

    fileInputStream.close()
    servletOutputStream.flush()
    servletOutputStream.close()
  }

  private def makeResponse(response: HttpServletResponse, message: String, code: Int = 0): Unit = {
    response.resetBuffer
    response.setStatus(HttpServletResponse.SC_OK)
    response.setContentType("application/json")
    response.setCharacterEncoding("UTF-8")
    response.setHeader("Cache-Control", "no-cache")
    response.getOutputStream.print(JsObject("message" -> JsString(message), "code" -> JsNumber(code)).toString)
    response.flushBuffer //marks response as committed -- if we don't do this the request will go through normally!
  }

  private def makeResponse(response: HttpServletResponse, throwable: Throwable): Unit = {
    var message = s"${throwable.toString}:${throwable.getLocalizedMessage}"
    makeResponse(response, message, 0)
  }
}



