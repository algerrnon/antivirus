package com.altuera.gms_antivirus_service

import javax.servlet.http.HttpServletResponse

object ScalaUtils {
  def convertResponseHeadersToString(httpServletResponse: HttpServletResponse): String = {
    import scala.collection.JavaConverters._
    val headerNames = httpServletResponse.getHeaderNames.asScala
    headerNames.map(headerName => headerName + ":" + httpServletResponse.getHeader(headerName)).mkString("|")
  }
}
