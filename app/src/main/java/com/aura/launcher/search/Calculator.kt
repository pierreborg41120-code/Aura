package com.aura.launcher.search

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.pow

/**
 * Petite calculatrice pour la recherche du tiroir : « 12*7+3 », « 15% », « (2+3)×4 », « 200+15% ».
 *
 * Grammaire (descente récursive, priorités habituelles) :
 *   expr    = terme (('+' | '-') terme)*
 *   terme   = facteur (('*' | '×' | 'x' | '/' | '÷' | ':') facteur)*
 *   facteur = unaire ('^' facteur)?          (puissance, associative à droite)
 *   unaire  = ('-' | '+') unaire | suffixe
 *   suffixe = primaire '%'*
 *   primaire = nombre | '(' expr ')'
 *
 * « a + b% » vaut a × (1 + b/100), comme sur une calculatrice de poche ; ailleurs « b% » vaut b/100.
 * La virgule et le point sont acceptés comme séparateur décimal.
 */
object Calculator {
    private val allowed = Regex("[0-9.,+\\-*/×÷xX:^%() ]+")
    private val operator = Regex("[+\\-*/×÷xX:^%]")
    private val plainNumber = Regex("^\\s*[+-]?\\d+([.,]\\d+)?\\s*$")

    /** Résultat formaté (« 87 », « 0,15 »), ou null si la saisie n'est pas un calcul. */
    fun evaluate(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.length > 120) return null
        if (!allowed.matches(text)) return null
        if (text.none { it.isDigit() } || !operator.containsMatchIn(text)) return null
        if (plainNumber.matches(text)) return null
        return try {
            val value = Parser(text).parse()
            if (value.isNaN() || value.isInfinite()) null else format(value)
        } catch (e: Exception) {
            null
        }
    }

    /** 12 chiffres significatifs, sans zéros inutiles, virgule à la française. */
    fun format(value: Double): String {
        val bd = BigDecimal(value).round(MathContext(12)).stripTrailingZeros()
        val abs = bd.abs()
        val text = if (abs >= BigDecimal("1E15") || (bd.signum() != 0 && abs < BigDecimal("1E-9"))) {
            bd.toString()
        } else {
            bd.toPlainString()
        }
        return text.replace('.', ',')
    }

    private class Operand(val value: Double, val percent: Boolean)

    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val v = expr()
            if (peek() != null) throw IllegalArgumentException("caractère inattendu")
            return v
        }

        private fun peek(): Char? {
            while (pos < s.length && s[pos] == ' ') pos++
            return s.getOrNull(pos)
        }

        private fun expr(): Double {
            var v = term().value
            while (true) {
                when (peek()) {
                    '+' -> {
                        pos++
                        val t = term()
                        v = if (t.percent) v * (1 + t.value) else v + t.value
                    }
                    '-' -> {
                        pos++
                        val t = term()
                        v = if (t.percent) v * (1 - t.value) else v - t.value
                    }
                    else -> return v
                }
            }
        }

        private fun term(): Operand {
            val first = factor()
            var value = first.value
            var percent = first.percent
            while (true) {
                when (peek()) {
                    '*', '×', 'x', 'X' -> {
                        pos++
                        value *= factor().value
                        percent = false
                    }
                    '/', '÷', ':' -> {
                        pos++
                        val d = factor().value
                        if (d == 0.0) throw ArithmeticException("division par zéro")
                        value /= d
                        percent = false
                    }
                    else -> return Operand(value, percent)
                }
            }
        }

        private fun factor(): Operand {
            val base = unary()
            if (peek() == '^') {
                pos++
                val exponent = factor().value
                return Operand(base.value.pow(exponent), false)
            }
            return base
        }

        private fun unary(): Operand = when (peek()) {
            '-' -> {
                pos++
                val o = unary()
                Operand(-o.value, o.percent)
            }
            '+' -> {
                pos++
                unary()
            }
            else -> suffix()
        }

        private fun suffix(): Operand {
            var value = primary()
            var percent = false
            while (peek() == '%') {
                pos++
                value /= 100
                percent = true
            }
            return Operand(value, percent)
        }

        private fun primary(): Double {
            val c = peek() ?: throw IllegalArgumentException("fin inattendue")
            if (c == '(') {
                pos++
                val v = expr()
                if (peek() != ')') throw IllegalArgumentException("parenthèse manquante")
                pos++
                return v
            }
            val start = pos
            var seenSeparator = false
            while (pos < s.length) {
                val ch = s[pos]
                when {
                    ch.isDigit() -> pos++
                    (ch == '.' || ch == ',') && !seenSeparator -> {
                        seenSeparator = true
                        pos++
                    }
                    else -> break
                }
            }
            val number = s.substring(start, pos).replace(',', '.')
            if (number.isEmpty() || number == ".") throw IllegalArgumentException("nombre attendu")
            return number.toDouble()
        }
    }
}
