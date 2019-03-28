// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service.av

import java.io.File

final case class FileToQuery(md5: String, sha1: String, file: File, fileType: String)
