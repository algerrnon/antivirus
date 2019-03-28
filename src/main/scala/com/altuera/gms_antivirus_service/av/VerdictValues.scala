package com.altuera.gms_antivirus_service.av

/**
  * * API Codes of Threat Prevention API 1.0
  * * https://sc1.checkpoint.com/documents/TPAPI/CP_1.0_ThreatPreventionAPI_APIRefGuide/html_frameset.htm
  */
object VerdictValues {

  /**
    * безопасный файл
    */
  val BENIGN = "benign"

  /**
    * файл содержит вредоносный код
    */
  val MALICIOUS = "malicious"

  /**
    *
    */
  val UNKNOWN = "unknown"
}
