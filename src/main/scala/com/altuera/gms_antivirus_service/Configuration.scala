package com.altuera.gms_antivirus_service

import com.typesafe.config.ConfigFactory

object Configuration {

  private val config = ConfigFactory.load()

  def uploadDir: String = Some("uploadDir").filter(config.hasPath).map(config.getString).get

  def genesysApiBaseUrl: String = Some("genesysApi.baseUrl").filter(config.hasPath).map(config.getString).get

  def customNoticeMessage: String = Some("customNotice.message").filter(config.hasPath).map(config.getString).get
}
