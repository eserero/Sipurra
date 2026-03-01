package snd.komelia.image

suspend fun KomeliaImage.getEdgeColors(vertical: Boolean): Pair<Int, Int>? {
    return if (vertical) {
        val top = extractArea(ImageRect(0, 0, width, 10.coerceAtMost(height)))
        val topColor = top.averageColor()
        top.close()

        val bottom = extractArea(ImageRect(0, (height - 10).coerceAtLeast(0), width, height))
        val bottomColor = bottom.averageColor()
        bottom.close()

        if (topColor != null && bottomColor != null) topColor to bottomColor
        else null
    } else {
        val left = extractArea(ImageRect(0, 0, 10.coerceAtMost(width), height))
        val leftColor = left.averageColor()
        left.close()

        val right = extractArea(ImageRect((width - 10).coerceAtLeast(0), 0, width, height))
        val rightColor = right.averageColor()
        right.close()

        if (leftColor != null && rightColor != null) leftColor to rightColor
        else null
    }
}
