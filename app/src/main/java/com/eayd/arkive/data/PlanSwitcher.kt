package com.eayd.arkive.data

/**
 * DENEME İÇİN plan değiştirme: ödeme almadan Plus'a geçer ya da ücretsiz plana (Free) döner (sunucuda POST /me/plan;
 * sunucuda PLAN_SWITCH_ENABLED kapatılınca 403 döner). Başarılıysa yeni haklar [PlanStatus]'a yazılır; açık olan profil
 * ve yan menü bunu görünce kendini tazeler.
 */
object PlanSwitcher {
    suspend fun switchTo(planName: String): MeResponse {
        val me = ApiClient.api.changePlan(ChangePlanRequest(planName))
        PlanStatus.update(me.summaryQuota())
        return me
    }
}
