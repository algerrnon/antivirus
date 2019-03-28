// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service.av

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
    implicit val ResponseItemFormat = jsonFormat(QuotaResponse,
      "remain_quota_hour",
      "remain_quota_month",
      "assigned_quota_hour",
      "assigned_quota_month",
      "hourly_quota_next_reset",
      "monthly_quota_next_reset",
      "cloud_monthly_quota_period_start",
      "cloud_monthly_quota_usage_for_this_gw",
      "cloud_hourly_quota_usage_for_this_gw",
      "cloud_monthly_quota_usage_for_quota_id",
      "cloud_hourly_quota_usage_for_quota_id",
      "monthly_exceeded_quota",
      "hourly_exceeded_quota",
      "cloud_quota_max_allow_to_exceed_percentage",
      "pod_time_gmt",
      "quota_expiration")
  }


  def main(args: Array[String]): Unit = {


    //    println(QuotaResponse(1, 2, 3, 4, "", "", "", 5, 6, 7, 8, 9, 10, 11, "", ""))
    //    println("".toJson.convertTo[QuotaResponse])
  }
}
