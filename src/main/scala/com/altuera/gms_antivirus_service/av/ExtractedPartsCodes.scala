// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service.av

/**
  * Threat Extraction cleans files. If the components of files are not given in the extracted_parts_codes field,
  * the default parts are cleaned.
  *
  * Default value of extracted_parts_codes is:
  * 1025,1026,1034,1137,1139,1141,1142,1143,1150,1151,1018,1019,1021
  */
object ExtractedPartsCodes {

  final val LINKED_OBJECTS = 1025

  final val MACROS_AND_CODE = 1026

  final val SENSITIVE_HYPERLINKS = 1034

  final val PDF_GO_TO_R_ACTIONS = 1137

  final val PDF_LAUNCH_ACTIONS = 1139

  final val PDF_URI_ACTIONS = 1141

  final val PDF_SOUND_ACTIONS = 1142

  final val PDF_MOVIE_ACTIONS = 1143

  final val PDF_JAVASCRIPT_ACTIONS = 1150

  final val PDF_SUBMIT_FORM_ACTIONS = 1151

  final val DATABASE_QUERIES = 1018

  final val EMBEDDED_OBJECTS = 1019

  final val FAST_SAVE_DATA = 1021

  final val CUSTOM_PROPERTIES = 1017

  final val STATISTIC_PROPERTIES = 1036

  final val SUMMARY_PROPERTIES = 1037
}
