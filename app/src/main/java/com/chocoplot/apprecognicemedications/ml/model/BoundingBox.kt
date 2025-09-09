package com.chocoplot.apprecognicemedications.ml.model

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float,
    val label: String
)
