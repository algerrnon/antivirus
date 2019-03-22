// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service.tpapi

/**
  * Response HTTP Status Codes of Threat Prevention API 1.0
  * https://sc1.checkpoint.com/documents/TPAPI/CP_1.0_ThreatPreventionAPI_APIRefGuide/html_frameset.htm
  *
  */

object StatusCodes extends Enumeration {
  /**
    * Request served successfully.
    */
  val OK = Value(200)

  /**
    * Service is not available anymore.
    */
  val MOVED_PERMANENTLY = Value(301)

  /**
    * Incorrect request format.
    */
  val BAD_REQUEST = Value(400)

  /**
    * Authentication failed.
    */
  val UNAUTHORIZED = Value(401)

  /**
    * Unauthorized access to the service.
    */
  val FORBIDDEN = Value(403)

  /**
    * Service does not exist.
    */
  val NOT_FOUND = Value(404)

  /**
    * There was an error in the service.
    */
  val INTERNAL_SERVER_ERROR = Value(500)

  /**
    * Currently this request cannot be served.
    */
  val SERVICE_UNAVAILABLE = Value(503)
}
