package com.shohan.fingercam.engine

import android.graphics.Bitmap
import android.util.Base64
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

private const val DESC_BYTES = 32

/** Keypoint positions (x0,y0,x1,y1,...) and ORB descriptors (32 bytes per keypoint). */
class Template(val points: FloatArray, val descriptors: ByteArray) {

    val count: Int get() = points.size / 2

    fun encode(): String {
        val buffer = ByteBuffer.allocate(4 + points.size * 4 + descriptors.size)
        buffer.putInt(count)
        for (value in points) buffer.putFloat(value)
        buffer.put(descriptors)
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    companion object {
        fun decode(text: String): Template {
            val buffer = ByteBuffer.wrap(Base64.decode(text, Base64.NO_WRAP))
            val n = buffer.getInt()
            val points = FloatArray(n * 2) { buffer.getFloat() }
            val descriptors = ByteArray(n * DESC_BYTES)
            buffer.get(descriptors)
            return Template(points, descriptors)
        }
    }
}

object FingerprintEngine {

    private const val SIZE = 512
    private const val MIN_KEYPOINTS = 50
    private const val RATIO = 0.8f
    private val WAVELENGTHS = doubleArrayOf(7.0, 10.0, 14.0)
    private const val ORIENTATIONS = 8

    @Volatile
    private var loaded = false

    private fun ensureLoaded(): Boolean {
        if (!loaded) loaded = OpenCVLoader.initLocal()
        return loaded
    }

    /**
     * Turns a photo of a fingertip into a template:
     * gray -> resize -> CLAHE -> remove lighting -> Gabor ridge filter bank -> ORB keypoints.
     * Returns null when the ridges could not be read.
     */
    fun extract(source: Bitmap): Template? {
        if (!ensureLoaded()) return null

        val rgba = Mat()
        val gray = Mat()
        val small = Mat()
        val equalized = Mat()
        val floatImg = Mat()
        val background = Mat()
        val normalized = Mat()
        val best = Mat.zeros(SIZE, SIZE, CvType.CV_32F)
        val response = Mat()
        val energy = Mat()
        val scaled = Mat()
        val ridge = Mat()
        val keypointMat = MatOfKeyPoint()
        val descriptors = Mat()
        val mask = Mat()

        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.resize(
                gray, small, Size(SIZE.toDouble(), SIZE.toDouble()),
                0.0, 0.0, Imgproc.INTER_AREA
            )

            val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
            clahe.apply(small, equalized)

            equalized.convertTo(floatImg, CvType.CV_32F, 1.0 / 255.0)
            Imgproc.GaussianBlur(floatImg, background, Size(0.0, 0.0), 12.0)
            Core.subtract(floatImg, background, normalized)

            for (lambda in WAVELENGTHS) {
                for (k in 0 until ORIENTATIONS) {
                    val theta = k * Math.PI / ORIENTATIONS
                    val kernel = Imgproc.getGaborKernel(
                        Size(25.0, 25.0), 4.5, theta, lambda, 0.5, 0.0, CvType.CV_32F
                    )
                    Imgproc.filter2D(normalized, response, CvType.CV_32F, kernel)
                    Core.multiply(response, response, energy)
                    Core.max(best, energy, best)
                    kernel.release()
                }
            }

            Core.normalize(best, scaled, 0.0, 255.0, Core.NORM_MINMAX)
            scaled.convertTo(ridge, CvType.CV_8U)

            val orb = ORB.create(1000, 1.2f, 8, 31, 0, 2, ORB.HARRIS_SCORE, 31, 10)
            orb.detectAndCompute(ridge, mask, keypointMat, descriptors)

            val keypoints = keypointMat.toArray()
            if (descriptors.empty() || keypoints.size < MIN_KEYPOINTS) return null
            if (descriptors.cols() != DESC_BYTES || descriptors.rows() != keypoints.size) return null

            val points = FloatArray(keypoints.size * 2)
            for (i in keypoints.indices) {
                points[2 * i] = keypoints[i].pt.x.toFloat()
                points[2 * i + 1] = keypoints[i].pt.y.toFloat()
            }
            val bytes = ByteArray(keypoints.size * DESC_BYTES)
            descriptors.get(0, 0, bytes)
            return Template(points, bytes)
        } finally {
            rgba.release(); gray.release(); small.release(); equalized.release()
            floatImg.release(); background.release(); normalized.release(); best.release()
            response.release(); energy.release(); scaled.release(); ridge.release()
            keypointMat.release(); descriptors.release(); mask.release()
        }
    }

    private fun descriptorMat(bytes: ByteArray, count: Int): Mat {
        val mat = Mat(count, DESC_BYTES, CvType.CV_8U)
        mat.put(0, 0, bytes)
        return mat
    }

    /**
     * Similarity score = number of keypoint matches that agree on one
     * rotation/translation/scale (RANSAC inliers). Higher = more similar.
     */
    fun score(query: Template, stored: Template): Int {
        if (!ensureLoaded()) return 0
        if (query.count < 8 || stored.count < 8) return 0

        val queryMat = descriptorMat(query.descriptors, query.count)
        val storedMat = descriptorMat(stored.descriptors, stored.count)
        val inliers = Mat()
        val from = MatOfPoint2f()
        val to = MatOfPoint2f()
        try {
            val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
            val knn = ArrayList<MatOfDMatch>()
            matcher.knnMatch(queryMat, storedMat, knn, 2)

            val src = ArrayList<Point>()
            val dst = ArrayList<Point>()
            for (pair in knn) {
                val m = pair.toArray()
                if (m.size >= 2 && m[0].distance < RATIO * m[1].distance) {
                    val q = m[0].queryIdx
                    val t = m[0].trainIdx
                    src.add(Point(query.points[2 * q].toDouble(), query.points[2 * q + 1].toDouble()))
                    dst.add(Point(stored.points[2 * t].toDouble(), stored.points[2 * t + 1].toDouble()))
                }
                pair.release()
            }
            if (src.size < 4) return 0

            from.fromList(src)
            to.fromList(dst)
            val transform = Calib3d.estimateAffinePartial2D(from, to, inliers, Calib3d.RANSAC, 4.0)
            if (transform.empty()) return 0
            val count = Core.countNonZero(inliers)
            transform.release()
            return count
        } finally {
            queryMat.release(); storedMat.release(); inliers.release()
            from.release(); to.release()
        }
    }
}
