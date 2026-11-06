package com.zpic.launcher.data

import java.text.Normalizer

/** The rail letter for a label: accents folded, anything non-alphabetic grouped under '#'. */
fun sectionOf(label: String): Char {
    val first = Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
        .firstOrNull { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        ?.uppercaseChar() ?: return '#'
    return if (first.isLetter()) first else '#'
}

private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
    .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
    .lowercase()

/** Search score; 0 means no match. Prefix beats word start beats initials beats substring beats subsequence. */
fun rank(query: String, label: String): Int {
    val q = fold(query.trim())
    if (q.isEmpty()) return 0
    val l = fold(label)
    if (l == q) return 100
    if (l.startsWith(q)) return 90
    val words = l.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
    if (words.any { it.startsWith(q) }) return 70
    if (q.length >= 2 && words.joinToString("") { it.take(1) }.startsWith(q)) return 60
    if (q.length >= 2 && q in l) return 50
    if (q.length >= 3) {
        var i = 0
        for (c in l) if (i < q.length && c == q[i]) i++
        if (i == q.length) return 20
    }
    return 0
}

/** Evaluates arithmetic typed into search, such as 12*(3+4). Null when the text isn't a sum. */
fun evalMath(input: String): Double? {
    val text = input.replace(" ", "").replace('×', '*').replace('÷', '/').replace(',', '.')
    if (text.length < 3 || text.none { it in "+-*/%^" } || text.none(Char::isDigit) || text.any { it !in "0123456789.+-*/%^()" }) return null
    var i = 0
    fun peek() = text.getOrNull(i)
    lateinit var sum: () -> Double?
    fun atom(): Double? {
        if (peek() == '-') { i++; return atom()?.let { -it } }
        if (peek() == '(') {
            i++
            val v = sum() ?: return null
            if (peek() != ')') return null
            i++
            return v
        }
        val start = i
        while (peek()?.let { it.isDigit() || it == '.' } == true) i++
        return text.substring(start, i).toDoubleOrNull()
    }
    fun power(): Double? {
        val base = atom() ?: return null
        if (peek() != '^') return base
        i++
        return power()?.let { Math.pow(base, it) }
    }
    fun product(): Double? {
        var v = power() ?: return null
        while (peek() == '*' || peek() == '/' || peek() == '%') {
            val op = peek(); i++
            val r = power() ?: return null
            v = when (op) { '*' -> v * r; '/' -> v / r; else -> v % r }
        }
        return v
    }
    sum = {
        var v = product()
        while (v != null && (peek() == '+' || peek() == '-')) {
            val op = peek(); i++
            val r = product()
            v = if (r == null) null else if (op == '+') v + r else v - r
        }
        v
    }
    val out = sum() ?: return null
    return out.takeIf { i == text.length && !it.isNaN() && !it.isInfinite() }
}

fun formatNumber(v: Double): String =
    if (v == Math.rint(v) && Math.abs(v) < 1e15) v.toLong().toString() else "%.8f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')
