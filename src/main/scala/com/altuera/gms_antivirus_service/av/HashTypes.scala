// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service.av

/**
  * Response HTTP Status Codes of Threat Prevention API 1.0
  * https://sc1.checkpoint.com/documents/TPAPI/CP_1.0_ThreatPreventionAPI_APIRefGuide/html_frameset.htm
  *
  */

object HashTypes {

  /**
    * The MD5 message digest algorithm defined in RFC 1321.
    */
  final val MD5 = "MD5"

  /**
    * The SHA-1 hash algorithm defined in the FIPS PUB 180-2.
    */
  final val SHA_1 = "SHA-1"
}
