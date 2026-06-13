package watson.resumaker

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform