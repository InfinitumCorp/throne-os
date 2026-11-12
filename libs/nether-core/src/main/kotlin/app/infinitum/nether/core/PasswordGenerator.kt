package app.infinitum.nether.core

import java.security.SecureRandom
import kotlin.math.ln

object PasswordGenerator {
    private val random = SecureRandom()
    /** 256 distinct words: each word adds exactly 8 bits. */
    internal val words = listOf(
        "able", "acid", "acorn", "actor", "agent", "alarm", "album", "alley", "amber", "angle", "ankle", "apple", "apron", "arch", "arena", "arrow",
        "aspen", "atlas", "attic", "audio", "autumn", "award", "bacon", "badge", "bagel", "baker", "bamboo", "banjo", "barn", "basin", "basket", "beach",
        "beam", "bean", "bear", "beaver", "bell", "belt", "bench", "berry", "bike", "birch", "bison", "blade", "blank", "blaze", "bloom", "board",
        "boat", "bolt", "bone", "book", "boot", "bottle", "brain", "branch", "brass", "bread", "brick", "bridge", "brook", "broom", "brush", "bucket",
        "buffalo", "bugle", "cabin", "cable", "cactus", "camel", "camera", "candle", "canoe", "canyon", "carbon", "carpet", "carrot", "castle", "cedar", "cello",
        "chain", "chalk", "chart", "cheese", "cherry", "chess", "chimney", "cider", "cinder", "circle", "citrus", "clay", "cliff", "clock", "cloud", "clover",
        "coast", "cobalt", "cocoa", "comet", "copper", "coral", "cotton", "cougar", "crane", "crayon", "creek", "cricket", "crown", "crystal", "cube", "cushion",
        "daisy", "dawn", "delta", "desert", "diamond", "dingo", "dolphin", "donkey", "dragon", "drum", "dune", "eagle", "easel", "echo", "eclipse", "ember",
        "engine", "falcon", "fern", "ferry", "fiddle", "field", "fig", "flag", "flame", "flint", "flute", "fog", "forest", "fossil", "fox", "frost",
        "galaxy", "garden", "garlic", "gecko", "geyser", "ginger", "glacier", "glade", "globe", "glove", "goat", "gold", "gourd", "granite", "grape", "gravel",
        "grove", "guitar", "gull", "hammer", "harbor", "harp", "hawk", "hazel", "helmet", "heron", "hill", "honey", "hornet", "horse", "husky", "iceberg",
        "igloo", "inlet", "iris", "iron", "island", "ivory", "jacket", "jade", "jaguar", "jasper", "jelly", "jewel", "jungle", "kayak", "kettle", "kiwi",
        "koala", "ladder", "lagoon", "lake", "lamp", "lantern", "lark", "lava", "lemon", "lens", "lily", "lime", "linen", "lion", "lizard", "llama",
        "lobster", "locket", "lotus", "lunar", "magnet", "mango", "maple", "marble", "market", "meadow", "melon", "meteor", "mint", "mirror", "mitten", "moose",
        "mosaic", "moss", "moth", "mountain", "mule", "museum", "needle", "nest", "nickel", "noodle", "north", "oak", "oasis", "ocean", "olive", "onion",
        "opal", "orbit", "orchid", "otter", "owl", "oyster", "paddle", "palm", "panda", "paper", "parrot", "peach", "pearl", "pebble", "pecan", "pelican",
    )

    const val PASSPHRASE_WORDS: Int = 8
    private val common = setOf(
        "password", "123456", "12345678", "qwerty", "letmein", "admin", "welcome", "iloveyou",
        "monkey", "dragon", "master", "login", "abc123", "trustno1", "passw0rd",
    )

    fun password(
        length: Int = 20,
        lower: Boolean = true,
        upper: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true,
        ambiguous: Boolean = false,
    ): String {
        val pool = StringBuilder()
        if (lower) pool.append(if (ambiguous) "abcdefghijkmnopqrstuvwxyz" else "abcdefghijklmnopqrstuvwxyz")
        if (upper) pool.append(if (ambiguous) "ABCDEFGHJKLMNPQRSTUVWXYZ" else "ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        if (digits) pool.append(if (ambiguous) "23456789" else "0123456789")
        if (symbols) pool.append("!@#%^&*-_=+?")
        require(pool.isNotEmpty()) { "Pick at least one set." }
        val size = length.coerceIn(8, 128)
        val out = CharArray(size)
        val alphabet = pool.toString()
        for (i in out.indices) out[i] = alphabet[random.nextInt(alphabet.length)]
        return String(out)
    }

    /** Default 8 words from 256: 64 bits. Never fewer than 6 words (48 bits). */
    fun passphrase(wordsCount: Int = PASSPHRASE_WORDS, separator: String = "-"): String {
        val count = wordsCount.coerceIn(6, 16)
        return (1..count).joinToString(separator) { words[random.nextInt(words.size)] }
    }

    /** 0 very weak, 4 strong. Penalises common passwords, repeats, and sequences. */
    fun strength(password: String): Int {
        if (password.isEmpty()) return 0
        if (password.lowercase() in common) return 0
        var charset = 0
        if (password.any { it.isLowerCase() }) charset += 26
        if (password.any { it.isUpperCase() }) charset += 26
        if (password.any { it.isDigit() }) charset += 10
        if (password.any { !it.isLetterOrDigit() }) charset += 32
        var entropy = password.length * (ln(charset.coerceAtLeast(1).toDouble()) / ln(2.0))
        if (Regex("(.)\\1{2,}").containsMatchIn(password)) entropy -= 10
        if (Regex("abc|qwe|123|password", RegexOption.IGNORE_CASE).containsMatchIn(password)) entropy -= 12
        return when {
            entropy < 28 -> 0
            entropy < 36 -> 1
            entropy < 60 -> 2
            entropy < 80 -> 3
            else -> 4
        }
    }
}
