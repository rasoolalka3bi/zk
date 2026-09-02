package com.faceattend.app

import java.net.NetworkInterface

object NetworkUtils {
    /** يرجّع عنوان IP المحلي للجهاز على الشبكة (عادة عبر واي فاي)، أو null
     * لو مش متصل بأي شبكة. لا يحتاج أي صلاحية خاصة (بخلاف طرق WifiManager
     * القديمة التي تتطلب صلاحية الموقع في إصدارات أندرويد الحديثة). */
    fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
