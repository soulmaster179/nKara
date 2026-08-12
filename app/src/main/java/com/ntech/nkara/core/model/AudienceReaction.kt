package com.ntech.nkara.core.model

enum class AudienceReaction(val emoji: String, val label: String) {
    Flower("🌹", "Tặng hoa"),
    Applause("👏", "Vỗ tay"),
    Cheer("📣", "Hú hét"),
    Heart("❤️", "Thả tim"),
    Fire("🔥", "Quá cháy"),
    Star("⭐", "Tuyệt vời"),
    Encore("🎤", "Hát nữa đi"),
    Dance("💃", "Quẩy lên"),
    Laugh("😂", "Vui quá"),
    Cheers("🥂", "Cạn ly"),
}

data class AudienceReactionEvent(
    val reaction: AudienceReaction,
    val nonce: Long = System.nanoTime(),
)
