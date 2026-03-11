package snd.komelia.utils

fun String.removeParentheses(): String {
    val index = this.indexOf('(')
    return if (index != -1) this.substring(0, index).trim() else this
}
