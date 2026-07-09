package blbl.cat3399.core.util

import androidx.annotation.DrawableRes
import blbl.cat3399.R
import blbl.cat3399.core.model.BangumiSeason
import blbl.cat3399.core.net.BiliClient

/**
 * PGC 访问角标（番剧/影视）的类型。
 *
 * 依赖文本角标（如 "会员"、"会员专享"、"限免"、"付费"、"预告"）判定，
 * 不同接口暴露的字段可能不同，调用方可传入多个原始角标字符串。
 */
enum class PgcAccessBadgeKind(val label: String) {
    Vip("大会员"),
    LimitedFree("限免"),
    Paid("付费"),
    Preview("预告"),
}

fun pgcAccessBadgeKindOf(rawBadges: Iterable<String?>): PgcAccessBadgeKind? {
    val badges = rawBadges.mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
    if (badges.isEmpty()) return null

    fun anyContains(token: String): Boolean = badges.any { it.contains(token) }

    return when {
        anyContains("大会员") || anyContains("会员") -> PgcAccessBadgeKind.Vip
        anyContains("限免") -> PgcAccessBadgeKind.LimitedFree
        anyContains("付费") -> PgcAccessBadgeKind.Paid
        anyContains("预告") -> PgcAccessBadgeKind.Preview
        else -> null
    }
}

fun pgcAccessBadgeKindOf(vararg rawBadges: String?): PgcAccessBadgeKind? = pgcAccessBadgeKindOf(rawBadges.asIterable())

/** 付费用橙色底、预告用红色底突出显示，其余（大会员/限免）沿用默认粉色底。 */
@DrawableRes
fun PgcAccessBadgeKind.backgroundRes(): Int =
    when (this) {
        PgcAccessBadgeKind.Paid -> R.drawable.bg_pgc_access_badge_paid
        PgcAccessBadgeKind.Preview -> R.drawable.bg_pgc_access_badge_preview
        else -> R.drawable.bg_pgc_access_badge
    }

fun pgcAccessBadgeTextOf(rawBadges: Iterable<String?>): String? = pgcAccessBadgeKindOf(rawBadges)?.label

fun pgcAccessBadgeTextOf(vararg rawBadges: String?): String? = pgcAccessBadgeKindOf(rawBadges.asIterable())?.label

/**
 * 依据设置开关过滤影视/PGC 列表中的付费、预告资源。
 * 开关默认开启（显示全部），关闭对应开关时剔除该类资源。
 */
fun List<BangumiSeason>.filterHiddenPgcAccess(): List<BangumiSeason> {
    val prefs = BiliClient.prefs
    val showPaid = prefs.pgcShowPaidEnabled
    val showPreview = prefs.pgcShowPreviewEnabled
    if (showPaid && showPreview) return this
    return filter { season ->
        when (pgcAccessBadgeKindOf(season.badgeEp, season.badge)) {
            PgcAccessBadgeKind.Paid -> showPaid
            PgcAccessBadgeKind.Preview -> showPreview
            else -> true
        }
    }
}
