// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

object Configuration {

  private val config = ConfigFactory.load()

  def uploadDir: String = getStringProperty("uploadDir")

  def genesysApiBaseUrl: String = getStringProperty("genesysApi.baseUrl")

  //Messages
  def messagePleaseWait: String = getStringProperty("customNotices.pleaseWait")

  def messageIsSafeFile: String = getStringProperty("customNotices.isSafeFile")

  def messageIsSafeFileAndLinkToOriginal: String = getStringProperty("customNotices.isSafeFileAndLinkToOriginal")

  def messageIsInfectedFile: String = getStringProperty("customNotices.isInfectedFile")

  def messageIsSuspiciousFile: String = getStringProperty("customNotices.isSuspiciousFile")

  def messageIsCorruptedFile: String = getStringProperty("customNotices.isCorruptedFile")

  def messageFileNotFound: String = getStringProperty("customNotices.fileNotFound")

  //File types
  private val avFileTypes = config.getConfig("avApi.supportedFileTypes")

  def avTypesDocs: List[String] = avFileTypes.getStringList("docs").asScala.toList

  def avTypesImages: List[String] = avFileTypes.getStringList("images").asScala.toList

  def avTypesOthers: List[String] = avFileTypes.getStringList("others").asScala.toList


  //Threat Prevention API (Antivirus = av)
  def avApiServerAddress: String = getStringProperty("avApi.serverAddress")

  def avApiVersion: String = getStringProperty("avApi.apiVersion")

  def avApiKey: String = getStringProperty("avApi.apiKey")

  //Antivirus http-client, retry parameters
  def avRetryMaximumWaitTimeSeconds: Int = getIntProperty("avApi.retry.maximumWaitTimeSeconds")

  def avRetryPauseBetweenAttemptsMilliseconds: Int = getIntProperty("avApi.retry.pauseBetweenAttemptsMilliseconds")

  def avRetryMaxNumberOfTimes: Int = getIntProperty("avApi.retry.maxNumberOfTimes")

  //Antiviry proxy
  def avProxyHost: String = getStringProperty("avApi.proxyHost")

  def avProxyPort: Int = getIntProperty("avApi.proxyPort")

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
