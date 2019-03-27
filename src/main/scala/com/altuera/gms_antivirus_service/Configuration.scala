// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

object Configuration {

  private val config = ConfigFactory.load()

  def uploadDir: String = getStringProperty("uploadDir")

  def genesysApiBaseUrl: String = getStringProperty("genesysApi.baseUrl")

  def messagePleaseWait: String = getStringProperty("customNotices.pleaseWait")

  def messageIsSafeFile: String = getStringProperty("customNotices.isSafeFile")

  def messageIsSafeFileAndLinkToOriginal: String = getStringProperty("customNotices.isSafeFileAndLinkToOriginal")

  def messageIsInfectedFile: String = getStringProperty("customNotices.isInfectedFile")

  def messageIsSuspiciousFile: String = getStringProperty("customNotices.isSuspiciousFile")

  def messageIsCorruptedFile: String = getStringProperty("customNotices.isCorruptedFile")

  def messageFileNotFound: String = getStringProperty("customNotices.fileNotFound")

  //Threat Prevention API
  def teApiServerAddress: String = getStringProperty("teApi.serverAddress")

  def teApiVersion: String = getStringProperty("teApi.apiVersion")

  def teApiKey: String = getStringProperty("teApi.apiKey")

  def teProxyHost: String = getStringProperty("proxy.com")

  def teProxyPort: Int = getIntProperty("proxy.com")

  private val teFileTypes = config.getConfig("teApi.supportedFileTypes")

  def teTypesDocs: List[String] = teFileTypes.getStringList("docs").asScala.toList

  def teTypesImages: List[String] = teFileTypes.getStringList("images").asScala.toList

  def teTypesOthers: List[String] = teFileTypes.getStringList("others").asScala.toList

  private def getStringProperty(path: String): String = {
    Some(path)
      .filter(config.hasPath).map(config.getString)
      .getOrElse(throw new IllegalStateException(s"Required config property [$path] missing"))
  }

  private def getIntProperty(path: String): Int = {
    Some(path)
      .filter(config.hasPath).map(config.getInt)
      .getOrElse(throw new IllegalStateException(s"Required config property [$path] missing"))
  }
}
