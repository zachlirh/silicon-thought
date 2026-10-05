package com.siliconthought.fitform.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

/** Result of analyzing a single frame. */
data class SquatResult(
    val reps: Int,
    val kneeAngle: Float,
    val feedback: String,
    /** Text to speak aloud this frame, or null for silence. */
    val speech: String? = null
)

/**
 * Squat form analyzer (rule-based + temporal state machine).
 *
 * How it works:
 *  1. Compute the "knee angle" from landmarks = angle of hip-knee-ankle;
 *  2. A state machine uses changes in that angle to detect standing ->
 *     descending -> ascending, and counts reps;
 *  3. While descending, a set of rules flags form issues (depth, knee
 *     valgus, forward torso lean, left/right imbalance).
 *
 * Note: film the squat from the SIDE. A front-on view distorts the knee angle.
 */
class SquatAnalyzer {

    private enum class Phase { STANDING, DESCENDING, ASCENDING }

    private var phase = Phase.STANDING
    private var minKneeAngle = 180f
    // Form issues seen this rep: display text -> text to speak.
    private val repIssues = linkedMapOf<String, String>()

    var reps = 0
        private set

    fun reset() {
        phase = Phase.STANDING
        minKneeAngle = 180f
        repIssues.clear()
        reps = 0
    }

    fun analyze(pose: Pose?): SquatResult {
        var speech: String? = null

        // Record an issue once per rep and speak it the first time it appears.
        fun flagIssue(display: String, spoken: String) {
            if (repIssues.putIfAbsent(display, spoken) == null) speech = spoken
        }

        if (pose == null) {
            return SquatResult(reps, minKneeAngle, "Can't see anyone - step into the frame")
        }

        val lHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val lKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val lAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        if (lHip == null || rHip == null || lKnee == null || rKnee == null ||
            lAnkle == null || rAnkle == null || lShoulder == null || rShoulder == null
        ) {
            return SquatResult(reps, minKneeAngle, "Step back and film your full body from the side (keep hips, knees and ankles in frame)")
        }

        val lHipP = lHip.position
        val rHipP = rHip.position
        val lKneeP = lKnee.position
        val rKneeP = rKnee.position
        val lAnkleP = lAnkle.position
        val rAnkleP = rAnkle.position
        val lShoulderP = lShoulder.position
        val rShoulderP = rShoulder.position

        val leftKnee = angleDeg(Pt(lHipP.x, lHipP.y), Pt(lKneeP.x, lKneeP.y), Pt(lAnkleP.x, lAnkleP.y))
        val rightKnee = angleDeg(Pt(rHipP.x, rHipP.y), Pt(rKneeP.x, rKneeP.y), Pt(rAnkleP.x, rAnkleP.y))
        val kneeAngle = (leftKnee + rightKnee) / 2f

        // 1) Sample form issues while descending (skip when standing to avoid false positives)
        if (phase != Phase.STANDING) {
            if (kneeAngle < minKneeAngle) minKneeAngle = kneeAngle
            if (kneeAngle < 135f) {
                // Knee valgus: knee-to-knee width is too narrow relative to hip width
                val hipWidth = abs(lHipP.x - rHipP.x)
                if (hipWidth > 0.02f && abs(lKneeP.x - rKneeP.x) < hipWidth * 0.8f) {
                    flagIssue("Knee valgus", "Knees out")
                }
                // Left/right leg doing unequal work
                if (abs(leftKnee - rightKnee) > 15f) {
                    flagIssue("Uneven left/right effort", "Even out your legs")
                }
                // Excessive forward torso lean
                val tilt = torsoTiltDeg(
                    (lShoulderP.x + rShoulderP.x) / 2f, (lShoulderP.y + rShoulderP.y) / 2f,
                    (lHipP.x + rHipP.x) / 2f, (lHipP.y + rHipP.y) / 2f
                )
                if (tilt > 55f) flagIssue("Excessive forward lean", "Chest up")
            }
        }

        // 2) Advance the state machine
        val liveHint = when (phase) {
            Phase.STANDING -> "Stand tall, ready"
            Phase.DESCENDING -> "Descending..."
            Phase.ASCENDING -> "Standing up..."
        }
        var justFinished = false

        when (phase) {
            Phase.STANDING -> {
                if (kneeAngle < 150f) {
                    phase = Phase.DESCENDING
                    minKneeAngle = kneeAngle
                    repIssues.clear()
                }
            }
            Phase.DESCENDING -> {
                if (kneeAngle > minKneeAngle + 10f) phase = Phase.ASCENDING
            }
            Phase.ASCENDING -> {
                if (kneeAngle > 155f) {
                    phase = Phase.STANDING
                    reps++
                    justFinished = true
                }
            }
        }

        // 3) Score the rep once it is completed
        val feedback: String
        if (justFinished) {
            if (minKneeAngle > 120f) repIssues.putIfAbsent("Not deep enough", "Go lower")
            if (repIssues.isEmpty()) {
                feedback = "✓ Rep $reps - great form!"
                speech = "Rep $reps. Nice!"
            } else {
                feedback = "Rep $reps: ${repIssues.keys.joinToString(", ")}"
                speech = "Rep $reps. ${repIssues.values.joinToString(", ")}"
            }
        } else {
            feedback = liveHint
        }

        return SquatResult(reps, kneeAngle, feedback, speech)
    }
}
