package com.zpic.launcher.data

enum class AppGroup(val title: String) {
    Social("Social"), Chat("Messages"), Media("Watch & listen"), Games("Games"), Photos("Photos"),
    Shopping("Shopping"), Food("Food"), Finance("Money"), Travel("Travel"), Work("Work"),
    Learn("Learning"), Tools("Tools"), Health("Health"), News("News"), Other("Other"),
}

private val keywords: List<Pair<AppGroup, List<String>>> = listOf(
    AppGroup.Finance to listOf("bank", "money", "hdfc", "icici", "sbi", "axis", "kotak", "paytm", "phonepe", "gpay", "paisa", "wallet", "pay.", "upi", "groww", "zerodha", "kite", "crypto", "coin", "binance", "invest", "gold", "aura", "finance", "credit", "loan", "cred.", "revolut", "paypal", "venmo"),
    AppGroup.Food to listOf("domino", "pizza", "swiggy", "zomato", "ubereats", "doordash", "grubhub", "mcdonald", "kfc", "starbucks", "burger", "restaurant", "food"),
    AppGroup.Shopping to listOf("zepto", "blinkit", "instamart", "bigbasket", "amazon.mshop", "amazon shopping", "flipkart", "myntra", "meesho", "ajio", "ebay", "shop", "store.app", "nykaa", "aliexpress", "temu", "shein", "walmart", "target"),
    AppGroup.Learn to listOf("classroom", "duolingo", "coursera", "udemy", "khan", "byju", "unacademy", "edu", "school", "learn", "study", "kindle", "dictionary"),
    AppGroup.Work to listOf("company portal", "companyportal", "windowsintune", "outlook", "teams", "slack", "zoom", "office", "word", "excel", "powerpoint", "onedrive", "sharepoint", "notion", "jira", "trello", "asana", "drive", "docs", "sheets", "slides", "keep", "calendar", "linkedin", "github", "authenticator", "todoist"),
    AppGroup.Chat to listOf("whatsapp", "telegram", "signal", "messenger", "messaging", "messages", "discord", "viber", "wechat", "contacts", "dialer", "phone", "gmail", "email", "mail"),
    AppGroup.Social to listOf("instagram", "facebook", "twitter", "x.android", "threads", "snapchat", "reddit", "pinterest", "tiktok", "bluesky", "mastodon", "tumblr"),
    AppGroup.Media to listOf("youtube", "netflix", "spotify", "primevideo", "hotstar", "jiocinema", "music", "podcast", "twitch", "vlc", "player", "gaana", "wynk", "torrent", "bittorrent", "audible"),
    AppGroup.Photos to listOf("photo", "gallery", "camera", "lightroom", "snapseed", "canva", "picsart", "capcut"),
    AppGroup.Travel to listOf("maps", "uber", "ola", "rapido", "irctc", "makemytrip", "goibibo", "booking", "airbnb", "airline", "indigo", "redbus", "waze"),
    AppGroup.Health to listOf("health", "fit", "strava", "wear", "galaxy.watch", "sleep", "yoga", "headspace", "calm", "pharmeasy", "1mg"),
    AppGroup.News to listOf("news", "inshorts", "weather", "flipboard"),
    AppGroup.Tools to listOf("settings", "files", "myfiles", "clock", "calculator", "vpn", "needle", "recorder", "scanner", "notes", "safety", "support", "samsung", "galaxy", "myjio", "my jio", "jio", "airtel", "vodafone", "surface", "launcher", "bluetooth", "voice search", "sim"),
)

/**
 * capability comes from what the app registers to handle (upi://pay, mailto:, geo:), the most honest
 * metadata an offline launcher has. androidCategory is ApplicationInfo.category, which apps set themselves.
 */
fun classify(packageName: String, label: String, androidCategory: Int, capability: AppGroup? = null): AppGroup {
    val l = label.lowercase()
    val p = packageName.lowercase()
    for ((group, words) in keywords) if (words.any { it in l }) return group
    // What an app can actually do beats the category its developer picked.
    if (capability != null) return capability
    when (androidCategory) {
        0 -> return AppGroup.Games
        1, 2 -> return AppGroup.Media
        3 -> return AppGroup.Photos
        4 -> return AppGroup.Social
        5 -> return AppGroup.News
        6 -> return AppGroup.Travel
        7 -> return AppGroup.Work
    }
    for ((group, words) in keywords) if (words.any { it in p }) return group
    return AppGroup.Other
}

data class GroupInput(val key: String, val group: AppGroup)

/**
 * Groups worth showing, in one fixed order so muscle memory holds: nothing here moves with usage.
 * Apps keep the order they arrive in. A one-app group is noise, so strays move to Other.
 */
fun buildGroups(items: List<GroupInput>): List<Pair<AppGroup, List<String>>> {
    val byGroup = items.groupBy { it.group }
    val strays = byGroup.filter { (g, v) -> v.size < 2 && g != AppGroup.Other }.keys
    return AppGroup.entries.mapNotNull { g ->
        val keys = if (g == AppGroup.Other) items.filter { it.group == g || it.group in strays } else byGroup[g].orEmpty().takeIf { g !in strays }.orEmpty()
        if (keys.isEmpty()) null else g to keys.map { it.key }
    }
}

/** Saved slots stand; anything new takes the first free one. Result: key to slot. */
fun placeApps(keys: List<String>, saved: Map<String, Int>): Map<String, Int> {
    val out = HashMap<String, Int>()
    val taken = HashSet<Int>()
    for (k in keys) saved[k]?.let { if (taken.add(it)) out[k] = it }
    var next = 0
    for (k in keys) if (k !in out) {
        while (next in taken) next++
        taken += next
        out[k] = next
    }
    return out
}

/** Moves an app to a slot; if another app is there they trade places. */
fun moveApp(slots: Map<String, Int>, key: String, to: Int): Map<String, Int> {
    val from = slots[key] ?: return slots
    val other = slots.entries.firstOrNull { it.value == to && it.key != key }?.key
    return slots + (key to to) + (if (other != null) mapOf(other to from) else emptyMap())
}
