package com.altuera.gms_antivirus_service.tpapi

/**
  * * API Codes of Threat Prevention API 1.0
  * * https://sc1.checkpoint.com/documents/TPAPI/CP_1.0_ThreatPreventionAPI_APIRefGuide/html_frameset.htm
  */
object ApiCodes extends Enumeration {

  /**
    * Request fully answered.
    */
  val FOUND = Value(1001)

  /**
    * File uploaded successfully. Query with the same hash and file type every 30 seconds.
    */
  val UPLOAD_SUCCESS = Value(1002)

  /**
    * File is pending. Query with the same hash and file type in about 3 seconds.
    */
  val PENDING = Value(1003)

  /**
    * Request not found. Upload the file.
    */
  val NOT_FOUND = Value(1004)

  /**
    * There is no quota for this API key. Contact Check Point.
    */
  val NO_QUOTA = Value(1005)

  /**
    * Part of the request found. If the missing data is required, upload the file.
    */
  val PARTIALLY_FOUND = Value(1006)

  /**
    * File type is illegal.
    */
  val FILE_TYPE_NOT_SUPPORTED = Value(1007)

  /**
    * Request format is not valid. Make sure the request follows this documentation.
    */
  val BAD_REQUEST = Value(1008)

  /**
    * There is a temporary error with the service. Try again in a few minutes.
    */
  val INTERNAL_ERROR = Value(1009)

  /**
    * You do not have permissions to use the requested feature. Contact Check Point.
    */
  val FORBIDDEN = Value(1010)

  /**
    * There is a temporary error with the service. Try again in few seconds.
    */
  val NOT_ENOUGH_RESOURCES = Value(1011)

}
