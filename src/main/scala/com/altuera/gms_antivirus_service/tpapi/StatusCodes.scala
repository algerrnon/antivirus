// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service.tpapi

/**
  * Response HTTP Status Codes of Threat Prevention API 1.0
  * https://sc1.checkpoint.com/documents/TPAPI/CP_1.0_ThreatPreventionAPI_APIRefGuide/html_frameset.htm
  *
  */

object StatusCodes {
  /**
    * Request served successfully.
    */
  val OK = 200

  /**
    * Service is not available anymore.
    */
  val MOVED_PERMANENTLY = 301

  /**
    * Incorrect request format.
    */
  val BAD_REQUEST = 400

  /**
    * Authentication failed.
    */
  val UNAUTHORIZED = 401

  /**
    * Unauthorized access to the service.
    */
  val FORBIDDEN = 403

  /**
    * Service does not exist.
    */
  val NOT_FOUND = 404

  /**
    * There was an error in the service.
    */
  val INTERNAL_SERVER_ERROR = 500

  /**
    * Currently this request cannot be served.
    */
  val SERVICE_UNAVAILABLE = 503
}
