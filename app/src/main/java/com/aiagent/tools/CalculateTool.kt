package com.aiagent.tools

/**
 * 计算器工具：安全地解析和计算数学表达式
 * 支持加减乘除、括号、幂运算、逗号分隔的多参数函数
 */
class CalculateTool : BaseTool() {

    override val name = "calculate"
    override val description = "执行数学计算，支持加减乘除、括号、幂运算和常用数学函数（如 sin, cos, sqrt, abs, log, pow 等）"

    override val parametersSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "expression" to mapOf(
                "type" to "string",
                "description" to "数学表达式，例如 '2+2*3', '(1+2)*4', 'sqrt(16)', 'pow(2,3)'"
            )
        ),
        "required" to listOf("expression")
    )

    override suspend fun execute(params: Map<String, Any>, context: ToolContext): String {
        val expression = params["expression"] as? String ?: return "错误：缺少数学表达式"
        return try {
            val sanitized = sanitizeExpression(expression)
            val result = evaluateExpression(sanitized)
            "计算结果: $expression = $result"
        } catch (e: Exception) {
            "计算出错: ${e.message}。请检查表达式是否合法。"
        }
    }

    private fun sanitizeExpression(expr: String): String {
        val sanitized = expr.lowercase().trim()
            .replace("π", "pi")
            .replace("×", "*")
            .replace("÷", "/")

        // 验证所有字符都是允许的
        val allChars = sanitized.filterNot { it.isWhitespace() }
        val safeChars = Regex("""[0-9+\-*/().%^,a-z]""")
        if (!allChars.all { safeChars.matches(it.toString()) }) {
            throw SecurityException("表达式包含不允许的字符")
        }
        return sanitized
    }

    private fun evaluateExpression(expr: String): Double {
        val parser = ExpressionParser(expr)
        return parser.parse()
    }

    /**
     * 递归下降表达式解析器，支持多参数函数
     */
    private class ExpressionParser(private val expr: String) {
        private var pos = 0

        fun parse(): Double {
            val result = parseAddSub()
            if (pos < expr.length) {
                throw IllegalArgumentException("意外的字符: '${expr[pos]}' 在位置 $pos")
            }
            return result
        }

        private fun parseAddSub(): Double {
            var left = parseMulDiv()
            while (pos < expr.length) {
                skipWhitespace()
                if (pos < expr.length && expr[pos] == '+') {
                    pos++
                    left += parseMulDiv()
                } else if (pos < expr.length && expr[pos] == '-') {
                    pos++
                    left -= parseMulDiv()
                } else break
            }
            return left
        }

        private fun parseMulDiv(): Double {
            var left = parsePower()
            while (pos < expr.length) {
                skipWhitespace()
                if (pos < expr.length && expr[pos] == '*') {
                    pos++
                    left *= parsePower()
                } else if (pos < expr.length && expr[pos] == '/') {
                    pos++
                    val right = parsePower()
                    if (right == 0.0) throw ArithmeticException("除以零")
                    left /= right
                } else if (pos < expr.length && expr[pos] == '%') {
                    pos++
                    left %= parsePower()
                } else break
            }
            return left
        }

        private fun parsePower(): Double {
            var base = parseUnary()
            skipWhitespace()
            if (pos < expr.length && expr[pos] == '^') {
                pos++
                val exp = parseUnary()
                return Math.pow(base, exp)
            }
            return base
        }

        private fun parseUnary(): Double {
            skipWhitespace()
            if (pos < expr.length && expr[pos] == '-') {
                pos++
                return -parseAtom()
            }
            if (pos < expr.length && expr[pos] == '+') {
                pos++
            }
            return parseAtom()
        }

        private fun parseAtom(): Double {
            skipWhitespace()

            // 检查多参数函数调用（pow, max, min）
            val multiFuncMatch = Regex("^(pow|max|min)").find(expr.substring(pos))
            if (multiFuncMatch != null) {
                val funcName = multiFuncMatch.value
                pos += funcName.length
                skipWhitespace()
                if (pos < expr.length && expr[pos] == '(') {
                    pos++ // skip (
                    val arg1 = parseAddSub()
                    skipWhitespace()
                    if (pos < expr.length && expr[pos] == ',') {
                        pos++ // skip ,
                        val arg2 = parseAddSub()
                        skipWhitespace()
                        if (pos < expr.length && expr[pos] == ')') pos++
                        return applyBinaryFunction(funcName, arg1, arg2)
                    }
                    // 只有一个参数时的降级
                    skipWhitespace()
                    if (pos < expr.length && expr[pos] == ')') pos++
                    return arg1
                }
            }

            // 检查单参数函数调用
            val funcMatch = Regex("^(sin|cos|tan|sqrt|abs|log|ln|exp|floor|ceil|round)").find(expr.substring(pos))
            if (funcMatch != null) {
                val funcName = funcMatch.value
                pos += funcName.length
                skipWhitespace()
                if (pos < expr.length && expr[pos] == '(') {
                    pos++ // skip (
                    val arg = parseAddSub()
                    skipWhitespace()
                    if (pos < expr.length && expr[pos] == ')') pos++
                    return applyFunction(funcName, arg)
                }
            }

            // 检查常量 pi
            if (pos + 1 < expr.length && expr[pos] == 'p' && expr[pos + 1] == 'i') {
                pos += 2
                return Math.PI
            }
            // 检查常量 e（不跟字母时）
            if (pos < expr.length && expr[pos] == 'e' && (pos + 1 >= expr.length || !expr[pos + 1].isLetter())) {
                pos++
                return Math.E
            }

            // 检查括号
            if (pos < expr.length && expr[pos] == '(') {
                pos++
                val result = parseAddSub()
                skipWhitespace()
                if (pos < expr.length && expr[pos] == ')') pos++
                return result
            }

            return parseNumber()
        }

        private fun parseNumber(): Double {
            skipWhitespace()
            val start = pos
            while (pos < expr.length && (expr[pos].isDigit() || expr[pos] == '.')) {
                pos++
            }
            if (start == pos) {
                if (pos < expr.length) {
                    throw IllegalArgumentException("期望数字，但在位置 $pos 找到: '${expr[pos]}'")
                } else {
                    throw IllegalArgumentException("表达式不完整")
                }
            }
            return expr.substring(start, pos).toDouble()
        }

        private fun skipWhitespace() {
            while (pos < expr.length && expr[pos].isWhitespace()) pos++
        }

        private fun applyFunction(name: String, arg: Double): Double = when (name) {
            "sin" -> Math.sin(Math.toRadians(arg))
            "cos" -> Math.cos(Math.toRadians(arg))
            "tan" -> Math.tan(Math.toRadians(arg))
            "sqrt" -> Math.sqrt(arg)
            "abs" -> Math.abs(arg)
            "log" -> Math.log10(arg)
            "ln" -> Math.log(arg)
            "exp" -> Math.exp(arg)
            "floor" -> Math.floor(arg)
            "ceil" -> Math.ceil(arg)
            "round" -> Math.round(arg).toDouble()
            else -> throw IllegalArgumentException("未知函数: $name")
        }

        private fun applyBinaryFunction(name: String, a: Double, b: Double): Double = when (name) {
            "pow" -> Math.pow(a, b)
            "max" -> maxOf(a, b)
            "min" -> minOf(a, b)
            else -> throw IllegalArgumentException("未知函数: $name")
        }
    }
}
