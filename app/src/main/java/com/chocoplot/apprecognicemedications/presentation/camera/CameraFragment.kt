// app/src/main/java/com/chocoplot/apprecognicemedications/presentation/camera/CameraFragment.kt
package com.chocoplot.apprecognicemedications.presentation.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.chocoplot.apprecognicemedications.R
import com.chocoplot.apprecognicemedications.databinding.FragmentCameraBinding
import com.chocoplot.apprecognicemedications.ml.Detector
import com.chocoplot.apprecognicemedications.ml.DetectorListener
import com.chocoplot.apprecognicemedications.ml.model.BoundingBox
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class CameraFragment : Fragment(), DetectorListener {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CameraViewModel by viewModels()

    private var detector: Detector? = null
    private var cameraExecutor: ExecutorService? = null

    private val requestCameraPermission = registerForActivityResult(RequestPermission()) { isGranted ->
        if (isGranted) startCamera()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cameraExecutor = Executors.newSingleThreadExecutor()
        detector = viewModel.createDetector(requireContext(), this)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.captureButton.setOnClickListener { viewModel.captureSingleFrame() }

        if (hasCameraPermission()) startCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)

        viewModel.inferenceTime.observe(viewLifecycleOwner) { ms ->
            binding.inferenceTime.text = getString(R.string.inference_time, ms)
        }
        viewModel.results.observe(viewLifecycleOwner) { boxes ->
            binding.overlay.setResults(boxes)
            binding.overlay.invalidate()
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build()
                .also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(cameraExecutor!!, ::analyzeFrame) }

            val selector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(viewLifecycleOwner, selector, preview, analysis)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun analyzeFrame(image: ImageProxy) {
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        image.planes[0].buffer.run { bitmap.copyPixelsFromBuffer(this) }
        val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        image.close()
        detector?.detect(rotated)
    }

    override fun onEmptyDetect() {
        viewModel.updateResults(emptyList(), 0L)
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        viewModel.updateResults(boundingBoxes, inferenceTime)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        detector?.clear()
        cameraExecutor?.shutdown()
        _binding = null
    }
}
