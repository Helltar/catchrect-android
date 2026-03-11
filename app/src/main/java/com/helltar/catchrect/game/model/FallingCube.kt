package com.helltar.catchrect.game.model

data class FallingCube(
    val type: CubeType,
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float
)
