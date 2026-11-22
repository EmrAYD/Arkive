package com.eayd.arkive.data

/** Deneme için ödemesiz plan değiştirme. Sunucuda PLAN_SWITCH_ENABLED kapalıysa 403 döner. */
object PlanSwitcher {
    suspend fun switchTo(planName: String): MeResponse {
        val me = ApiClient.api.changePlan(ChangePlanRequest(planName))
        PlanStatus.update(me.summaryQuota())
        return me
    }
}
