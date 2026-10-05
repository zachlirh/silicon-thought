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
 *  0. Before counting anything it confirms a trustworthy full-body pose is both
 *     visible AND held in a tall standing posture for a moment ("arming").
 *     Until then reps stay frozen, which stops phantom/noisy detections from
 *     racking up fake reps.
 *  1. Compute the "knee angle" from landmarks = angle of hip-knee-ankle;
 *  2. A state machine uses changes in that angle to detect standing ->
 *     descending -> ascending, and counts reps;
 *  3. While descending, a set of rules flags form issues (depth, knee
 *     valgus, forward torso lean, left/right imbalance).
 *
 * Note: film the squat from the SIDE. A front-on view distorts the knee angle.
 */
class SquatAnalyzer {

    private enum class Phase { WAITING, STANDING, DESCENDING, ASCENDING }

    private var phase = Phase.WAITING
    private var minKneeAngle = 180f
    // Form issues seen this rep: display text -> text to speak.
    private val repIssues = linkedMapOf<String, String>()

    // Temporal filtering: frames of steady standing needed to arm, and frames
    // of bad input tolerated before we give up and wait for a good pose again.
    private var readyFrames = 0
    private var badFrames = 0

    var reps = 0
        private set

    fun reset() {
        phase = Phase.WAITING
        minKneeAngle = 180f
        repIssues.clear()
        readyFrames = 0
        badFrames = 0
        reps = 0
    }

    fun analyze(pose: Pose?): SquatResult {
        var speech: String? = null

        // Record an issue once per rep and speak it the first time it appears.
        fun flagIssue(display: String, spoken: String) {
            if (repIssues.putIfAbsent(display, spoken) == null) speech = spoken
        }

        // 1) Reject anything we can't trust before doing any geometry.
        if (pose == null) {
            return lostPose("Can't see anyone - step into the frame")
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
            return lostPose("Step back and film your full body from the side (keep hips, knees and ankles in frame)")
        }

        // Every joint we rely on must actually be visible. ML Kit returns guessed
        // landmarks for occluded joints, and those produce nonsense angles - so
        // we ignore any frame where the confidence is too low.
        val joints = listOf(lHip, rHip, lKnee, rKnee, lAnkle, rAnkle, lShoulder, rShoulder)
        if (joints.any { it.inFrameLikelihood < MIN_LIKELIHOOD }) {
            return lostPose("Move so your whole body is clearly in view")
        }

        val lHipP = lHip.position
        val rHipP = rHip.position
        val lKneeP = lKnee.position
        val rKneeP = rKnee.position
        val lAnkleP = lAnkle.position
        val rAnkleP = rAnkle.position
        val lShoulderP = lShoulder.position
        val rShoulderP = rShoulder.position

        // The body must cover a decent slice of the frame vertically, otherwise
        // it's probably a distant, partial or phantom detection.
        val bodyTopY = minOf(lShoulderP.y, rShoulderP.y)
        val bodyBottomY = maxOf(lAnkleP.y, rAnkleP.y)
        if (bodyBottomY - bodyTopY < MIN_BODY_SPAN) {
            return lostPose("Step back so your whole body fits in the frame")
        }

        // This frame is trustworthy enough to use.
        badFrames = 0

        val leftKnee = angleDeg(Pt(lHipP.x, lHipP.y), Pt(lKneeP.x, lKneeP.y), Pt(lAnkleP.x, lAnkleP.y))
        val rightKnee = angleDeg(Pt(rHipP.x, rHipP.y), Pt(rKneeP.x, rKneeP.y), Pt(rAnkleP.x, rAnkleP.y))
        val kneeAngle = (leftKnee + rightKnee) / 2f
        val torsoTilt = torsoTiltDeg(
            (lShoulderP.x + rShoulderP.x) / 2f, (lShoulderP.y + rShoulderP.y) / 2f,
            (lHipP.x + rHipP.x) / 2f, (lHipP.y + rHipP.y) / 2f
        )

        // 2) Arm only after a stable standing posture is held for a moment.
        if (phase == Phase.WAITING) {
            val standing = kneeAngle > STAND_ANGLE && torsoTilt < MAX_READY_TILT
            readyFrames = if (standing) readyFrames + 1 else 0
            if (readyFrames < READY_FRAMES) {
                return SquatResult(reps, kneeAngle, "Stand tall and hold still to begin")
            }
            phase = Phase.STANDING
            speech = "Ready. Go!"
        }

        // 3) Sample form issues while the knees are bending.
        if (phase == Phase.DESCENDING || phase == Phase.ASCENDING) {
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
                if (torsoTilt > 55f) flagIssue("Excessive forward lean", "Chest up")
            }
        }

        // 4) Advance the state machine
        val liveHint = when (phase) {
            Phase.WAITING -> "Stand tall and hold still to begin"
            Phase.STANDING -> "Stand tall, ready"
            Phase.DESCENDING -> "Descending..."
            Phase.ASCENDING -> "Standing up..."
        }
        var justFinished = false

        when (phase) {
            Phase.STANDING -> {
                if (kneeAngle < DESCEND_ANGLE) {
                    phase = Phase.DESCENDING
                    minKneeAngle = kneeAngle
                    repIssues.clear()
                }
            }
            Phase.DESCENDING -> {
                if (kneeAngle < minKneeAngle) minKneeAngle = kneeAngle
                if (kneeAngle > minKneeAngle + 10f) phase = Phase.ASCENDING
            }
            Phase.ASCENDING -> {
                if (kneeAngle > STAND_BACK_ANGLE) {
                    phase = Phase.STANDING
                    reps++
                    justFinished = true
                } else if (kneeAngle < minKneeAngle) {
                    phase = Phase.DESCENDING
                    minKneeAngle = kneeAngle
                }
            }
            Phase.WAITING -> Unit
        }

        // 5) Score the rep once it is completed
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

    /** Called when the frame can't be trusted; re-arms if it keeps happening. */
    private fun lostPose(message: String): SquatResult {
        badFrames++
        if (badFrames >= BAD_FRAMES_TO_RESET) {
            phase = Phase.WAITING
            readyFrames = 0
            repIssues.clear()
            minKneeAngle = 180f
        }
        return SquatResult(reps, minKneeAngle, message)
    }

    private companion object {
        const val MIN_LIKELIHOOD = 0.5f    // per-joint confidence that it's in frame
        const val MIN_BODY_SPAN = 0.30f    // shoulders->ankles must span this much
        const val READY_FRAMES = 12        // steady standing frames before arming
        const val BAD_FRAMES_TO_RESET = 8  // bad frames in a row before re-arming
        const val STAND_ANGLE = 160f       // knee angle that counts as "standing"
        const val MAX_READY_TILT = 30f     // max torso lean allowed to arm
        const val DESCEND_ANGLE = 150f     // crossing below this starts a rep
        const val STAND_BACK_ANGLE = 155f  // crossing above this finishes a rep
    }
}
