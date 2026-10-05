package com.siliconthought.fitform.analysis

import kotlin.math.abs
import kotlin.math.atan2

/** A normalized coordinate point (0..1). */
data class Pt(val x: Float, val y: Float)

/**
 * Returns the angle at vertex [b] in degrees, for points a-b-c.
 * a, b and c are three landmarks, where [b] is the vertex
 * (e.g. for a knee angle, b = knee).
 */
fun angleDeg(a: Pt, b: Pt, c: Pt): Float {
    val ang = Math.toDegrees(
        atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble()) -
            atan2((a.y - b.y).toDouble(), (a.x - b.x).toDouble())
    )
    var r = abs(ang)
    if (r > 180.0) r = 360.0 - r
    return r.toFloat()
}

/**
 * Tilt of the torso relative to vertical, in degrees.
 * Pass the shoulder midpoint and the hip midpoint; it is near 0 when
 * standing and grows larger as the torso leans forward.
 */
fun torsoTiltDeg(shoulderX: Float, shoulderY: Float, hipX: Float, hipY: Float): Float {
    val dx = abs(shoulderX - hipX).toDouble()
    val dy = abs(shoulderY - hipY).toDouble()
    if (dy <= 1e-4) return 90f
    return Math.toDegrees(atan2(dx, dy)).toFloat()
}
