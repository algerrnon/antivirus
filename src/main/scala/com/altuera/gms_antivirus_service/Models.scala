// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service

object Models {

  import spray.json.DefaultJsonProtocol

  final case class QuotaResponse(remain_quota_hour: Int = 0,
                                 remain_quota_month: Int = 0,
                                 assigned_quota_hour: Int = 0,
                                 assigned_quota_month: Int = 0,
                                 hourly_quota_next_reset: String = "0",
                                 monthly_quota_next_reset: String = "0",
                                 cloud_monthly_quota_period_start: String = "0",
                                 cloud_monthly_quota_usage_for_this_gw: Int = 0,
                                 cloud_hourly_quota_usage_for_this_gw: Int = 0,
                                 cloud_monthly_quota_usage_for_quota_id: Int = 0,
                                 cloud_hourly_quota_usage_for_quota_id: Int = 0,
                                 monthly_exceeded_quota: Int = 0,
                                 hourly_exceeded_quota: Int = 0,
                                 cloud_quota_max_allow_to_exceed_percentage: Int = 0,
                                 pod_time_gmt: String = "0",
                                 quota_expiration: String = "0")


  object QuotaResponseItemProtocol extends DefaultJsonProtocol {
    implicit val ResponseItemFormat = jsonFormat16(QuotaResponse)
  }

}
