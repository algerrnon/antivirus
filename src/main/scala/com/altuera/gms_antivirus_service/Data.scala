// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import java.io.File
import java.util

final case class Data(secureKey: String, clientId: String, requestHeaders: util.Map[String, String], file: File)
