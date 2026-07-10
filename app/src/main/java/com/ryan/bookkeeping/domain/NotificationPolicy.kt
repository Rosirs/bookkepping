package com.ryan.bookkeeping.domain

import java.util.Locale

object NotificationPolicy {
    private const val MAX_STORED_TEXT_LENGTH = 120

    private val exactAllowedPackages = setOf(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm",
        "com.unionpay",
        "com.chinamworld.main",
        "com.cmbchina.ccd.pluto.cmbActivity",
        "cmb.pb",
        "com.icbc",
        "com.icbc.elife",
        "com.ccb.framework.security",
        "com.bankcomm.Bankcomm",
        "com.boc.bocsoft.mobile",
        "com.abchina.android.apps.abchina",
        "cn.com.spdb.mobilebank.per",
        "com.cib.cibmb",
        "com.cebbank.mobile.cemb",
        "com.hxb.mobile.client",
    )

    private val allowedPackagePrefixes = listOf(
        "com.alipay",
        "com.tencent.mm",
        "com.icbc",
        "com.cmbchina",
        "com.ccb",
        "com.boc",
        "com.abchina",
        "com.bankcomm",
        "com.spdb",
        "com.cib",
        "com.cebbank",
    )

    fun shouldInspectPackage(packageName: String): Boolean {
        val trimmed = packageName.trim()
        if (trimmed in exactAllowedPackages) return true
        val normalized = trimmed.lowercase(Locale.ROOT)
        return allowedPackagePrefixes.any { normalized.startsWith(it.lowercase(Locale.ROOT)) }
    }

    fun sanitizeForStorage(text: String): String {
        return text
            .replace(Regex("https?://\\S+"), "[链接]")
            .replace(Regex("(?<=验证码\\s?)\\d{4,8}"), "******")
            .replace(Regex("(?<=校验码\\s?)\\d{4,8}"), "******")
            .replace(Regex("(?<=动态码\\s?)\\d{4,8}"), "******")
            .replace(Regex("(?<=验证码[:：]?\\s?)\\d{4,8}"), "******")
            .replace(Regex("\\s+"), " ")
            .trim()
            .let { if (it.length <= MAX_STORED_TEXT_LENGTH) it else it.take(MAX_STORED_TEXT_LENGTH) }
    }
}
