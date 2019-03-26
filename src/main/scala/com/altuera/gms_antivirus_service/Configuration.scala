// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

import com.typesafe.config.ConfigFactory

object Configuration {

  private val config = ConfigFactory.load()

  def uploadDir: String = getStringProperty("uploadDir")

  def genesysApiBaseUrl: String = getStringProperty("genesysApi.baseUrl")

  def customNoticeMessage: String = getStringProperty("customNotice.message")

  //Threat Prevention API
  def teApiServerAddress: String = getStringProperty("teApi.serverAddress")

  def teApiVersion: String = getStringProperty("teApi.apiVersion")

  def teApiKey: String = getStringProperty("teApi.apiKey")


  private def getStringProperty(path: String): String = {
    Some(path)
      .filter(config.hasPath).map(config.getString)
      .getOrElse(throw new IllegalStateException(s"Required config property [$path] missing"))
  }
}
