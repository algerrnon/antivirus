// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import java.io.File

import scala.collection.mutable

final case class Data(secureKey: String, clientId: String, requestHeaders: mutable.Map[String, String], file: File)

