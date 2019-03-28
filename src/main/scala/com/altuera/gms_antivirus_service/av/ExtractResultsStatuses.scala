// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service.av

object ExtractResultsStatuses {

  /**
    * Default - returned if the POD did not receive an answer from the Threat Extraction engine in 60 seconds
    */
  final val CP_EXTRACT_RESULT_UNKNOWN = "CP_EXTRACT_RESULT_UNKNOWN"

  /**
    * The download id of the extracted file, for download request.
    * Only sent when extract_result = CP_EXTRACT_RESULT_SUCCESS
    */
  final val CP_EXTRACT_RESULT_SUCCESS = "CP_EXTRACT_RESULT_SUCCESS"

  final val CP_EXTRACT_RESULT_FAILURE = "CP_EXTRACT_RESULT_FAILURE"
  final val CP_EXTRACT_RESULT_TIMEOUT = "CP_EXTRACT_RESULT_TIMEOUT"
  final val CP_EXTRACT_RESULT_UNSUPPORTED_FILE = "CP_EXTRACT_RESULT_UNSUPPORTED_FILE"
  final val CP_EXTRACT_RESULT_NOT_SCRUBBED = "CP_EXTRACT_RESULT_NOT_SCRUBBED"
  final val CP_EXTRACT_RESULT_INTERNAL_ERROR = "CP_EXTRACT_RESULT_INTERNAL_ERROR"
  final val CP_EXTRACT_RESULT_DISK_LIMIT_REACHED = "CP_EXTRACT_RESULT_DISK_LIMIT_REACHED"
  final val CP_EXTRACT_RESULT_ENCRYPTED_FILE = "CP_EXTRACT_RESULT_ENCRYPTED_FILE"
  final val CP_EXTRACT_RESULT_DOCSEC_FILE = "CP_EXTRACT_RESULT_DOCSEC_FILE"
  final val CP_EXTRACT_RESULT_OUT_OF_MEMORY = "CP_EXTRACT_RESULT_OUT_OF_MEMORY"
}
