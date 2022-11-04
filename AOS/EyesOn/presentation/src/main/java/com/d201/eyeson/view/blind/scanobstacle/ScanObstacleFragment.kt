package com.d201.eyeson.view.blind.scanobstacle

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import com.d201.arcore.depth.common.OBJECT_DETECTION_CUSTOM
import com.d201.arcore.depth.common.TEXT_RECOGNITION_KOREAN
import com.d201.arcore.depth.common.getDeviceSize
import com.d201.arcore.depth.DepthTextureHandler
import com.d201.mlkit.objectdetector.ObjectGraphic
import com.d201.depth.depth.common.*
import com.d201.depth.depth.rendering.BackgroundRenderer
import com.d201.depth.depth.rendering.ObjectRenderer
import com.d201.eyeson.R
import com.d201.eyeson.base.BaseFragment
import com.d201.eyeson.databinding.FragmentScanObstacleBinding
import com.d201.mlkit.BitmapUtils.RotateBitmap
import com.d201.mlkit.BitmapUtils.imageToBitmap
import com.d201.mlkit.GraphicOverlay
import com.d201.mlkit.PreferenceUtils
import com.d201.mlkit.VisionProcessorBase
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.demo.kotlin.objectdetector.ObjectDetectorProcessor
import com.google.mlkit.vision.demo.kotlin.textdetector.TextRecognitionProcessor
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "ScanObstacleFragment__"
@AndroidEntryPoint
class ScanObstacleFragment : BaseFragment<FragmentScanObstacleBinding>(R.layout.fragment_scan_obstacle),
    GLSurfaceView.Renderer, TextToSpeech.OnInitListener {
    //private lateinit var tts: TextToSpeech

    private val scanObstacleViewModel by viewModels<ScanObstacleViewModel>()
    private var graphicOverlay: GraphicOverlay? = null
    private var imageProcessor: VisionProcessorBase<*>? = null
    private var objectImageProcessor: ObjectDetectorProcessor? = null

    private var frameWidth = 0
    private  var frameHeight = 0

    private var processing = false
    private var pending = false
    private var lastFrame: Bitmap? = null

    private var selectedModel = OBJECT_DETECTION_CUSTOM

    private lateinit var surfaceView: GLSurfaceView

    private var installRequested = false
    private var isDepthSupported = false

    private var session: Session? = null
    private val messageSnackbarHelper: SnackbarHelper = SnackbarHelper()
    private var displayRotationHelper: DisplayRotationHelper? = null
    private lateinit var trackingStateHelper: TrackingStateHelper
    private var tapHelper: TapHelper? = null

    private lateinit var depthTexture: DepthTextureHandler
    private val backgroundRenderer: BackgroundRenderer = BackgroundRenderer()
    private val virtualObject: ObjectRenderer = ObjectRenderer()

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private val anchorMatrix = FloatArray(16)

    private val SEARCHING_PLANE_MESSAGE = "Please move around slowly..."
    private val PLANES_FOUND_MESSAGE = "Tap to place objects."
    private val DEPTH_NOT_AVAILABLE_MESSAGE = "[Depth not supported on this device]"

    // Anchors created from taps used for object placing with a given color.
    private val OBJECT_COLOR = floatArrayOf(139.0f, 195.0f, 74.0f, 255.0f)
    private val anchors = ArrayList<Anchor>()

    private var showDepthMap = false

    // 바운딩 박스 중심점
//    private val centerX = MutableLiveData<Float>()
//    private val centerY = MutableLiveData<Float>()
    private var centerX : Float = 0.0f
    private var centerY : Float = 0.0f
    override fun init() {
        initView()
        initObserver()
    }

    private fun initView(){
        surfaceView = binding.surfaceview
        displayRotationHelper = DisplayRotationHelper( /*context=*/requireContext())
        trackingStateHelper = TrackingStateHelper(requireActivity())
        depthTexture = DepthTextureHandler(requireContext())

        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true)
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0) // Alpha used for plane blending.

        surfaceView.setRenderer(this)
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
        surfaceView.setWillNotDraw(false)

        installRequested = false

        val toggleDepthButton = binding.toggleDepthButton
        toggleDepthButton.setOnClickListener { view: View? ->
            if (isDepthSupported) {
                showDepthMap = !showDepthMap
                toggleDepthButton.setText(if (showDepthMap) "숨기기" else "보기")
            } else {
                showDepthMap = false
                toggleDepthButton.setText("깊이이용불가")
            }
        }

        graphicOverlay = binding.graphicOverlay
        graphicOverlay!!.bringToFront()


       // tts = TextToSpeech(requireContext(), this)
    }

    private fun initObserver(){

    }
    // MLKit
    private fun processFrame(frame: Bitmap) {
        Log.d(TAG, "processFrame")
        lastFrame = frame
        Log.d(TAG, "processFrame: ${lastFrame!!.width}")
        if (imageProcessor != null) {
            pending = processing
            if (!processing) {
                processing = true
                if (frameWidth != frame.width || frameHeight != frame.height) {
                    frameWidth = frame.width
                    frameHeight = frame.height
                    graphicOverlay!!.setImageSourceInfo(frameWidth, frameHeight, false)
                }
                imageProcessor!!.setOnProcessingCompleteListener(object :VisionProcessorBase.OnProcessingCompleteListener{
                    override fun onProcessingComplete() {
                        processing = false
                        onProcessComplete(frame)
                        if(pending) processFrame(lastFrame!!)
                    }
                })

                imageProcessor!!.processBitmap(frame, graphicOverlay!!)
            }
        }
    }

    protected fun onProcessComplete(frame: Bitmap?) {}

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        val params: ViewGroup.LayoutParams? = requireActivity().window.attributes
        val deviceWidth = getDeviceSize(requireActivity()).x
        val deviceHeight = deviceWidth / 3 * 4
        params?.width = deviceWidth
        params?.height = deviceHeight
        binding.surfaceview.layoutParams = params
        binding.surfaceview.layout(0, 0,deviceWidth,deviceHeight)

        if (session == null) {
            var exception: Exception? = null
            var message: String? = null
            try {
                when (ArCoreApk.getInstance().requestInstall(requireActivity(), !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {}
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(requireActivity())) {
                    CameraPermissionHelper.requestCameraPermission(requireActivity())
                    return
                }

                // Creates the ARCore session.
                session = Session( /* context= */requireContext())
                val config = session!!.config
                val filter = CameraConfigFilter(session)
                filter.targetFps = EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30) // 30프레임
                filter.depthSensorUsage.add(CameraConfig.DepthSensorUsage.REQUIRE_AND_USE) // tof 사용
                val cameraConfigList = session!!.getSupportedCameraConfigs(filter)
                session!!.cameraConfig = cameraConfigList[1]
                isDepthSupported = session!!.isDepthModeSupported(Config.DepthMode.AUTOMATIC)

                if (isDepthSupported) {
                    config.setDepthMode(Config.DepthMode.AUTOMATIC)
                    config.setFocusMode(Config.FocusMode.AUTO)
                } else {
                    config.setDepthMode(Config.DepthMode.DISABLED)
                }
                session!!.configure(config)
            } catch (e: UnavailableArcoreNotInstalledException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableUserDeclinedInstallationException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableApkTooOldException) {
                message = "Please update ARCore"
                exception = e
            } catch (e: UnavailableSdkTooOldException) {
                message = "Please update this app"
                exception = e
            } catch (e: UnavailableDeviceNotCompatibleException) {
                message = "This device does not support AR"
                exception = e
            } catch (e: Exception) {
                message = "Failed to create AR session"
                exception = e
            }
            if (message != null) {
                messageSnackbarHelper.showError(requireActivity(), message)
                Log.e(
                    TAG,
                    "Exception creating session",
                    exception
                )
                return
            }
            binding.tvDistance.bringToFront()
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session!!.resume()
            session!!.pause()
            session!!.resume()
        } catch (e: CameraNotAvailableException) {
            messageSnackbarHelper.showError(requireActivity(), "Camera not available. Try restarting the app.")
            session = null
            return
        }
        createImageProcessor()
        surfaceView.onResume()
        displayRotationHelper!!.onResume()

//        objectImageProcessor!!.resultData.observe(this){
//            speakOut(it)
//        }

    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        super.onPause()
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper!!.onPause()
            surfaceView.onPause()
            session!!.pause()
       //     tts.stop()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        Log.d(TAG, "onRequestPermissionsResult")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!CameraPermissionHelper.hasCameraPermission(requireActivity())) {
            Toast.makeText(
                requireContext(), "Camera permission is needed to run this application",
                Toast.LENGTH_LONG
            ).show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(requireActivity())) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(requireActivity())
            }
            requireActivity().finish()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, " onSurfaceCreated")
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // The depth texture is used for object occlusion and rendering.
            depthTexture.createOnGlThread()

            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread( /*context=*/requireContext())
            backgroundRenderer.createDepthShaders( /*context=*/requireContext(), depthTexture.getDepthTexture())
            virtualObject.createOnGlThread( /*context=*/requireContext(), "models/andy.obj", "models/andy.png")
            virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f)
        } catch (e: IOException) {
            Log.e(
                TAG,
                "Failed to read an asset file",
                e
            )
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged")
        displayRotationHelper!!.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        Log.d(TAG, "onDrawFrame")
        // Clear screen to notify driver it should not load any pixels from previous frame.
        // 이전 프레임에서 픽셀을 로드하지 않도록 드라이버에 알리기 위해 화면을 지웁니다.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (session == null) {
            return
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        // 뷰 크기가 변경되었음을 ARCore 세션에 알립니다.
        // 비디오 배경을 적절하게 조정할 수 있습니다.
        displayRotationHelper!!.updateSessionIfNeeded(session!!)
        try {
            session!!.setCameraTextureName(backgroundRenderer.getTextureId())

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            //  ARSession에서 현재 프레임을 가져옵니다.
            //  구성이 UpdateMode.BLOCKING(기본값)으로 설정되면 렌더링이 카메라 프레임 속도로 조절됩니다.
            val frame = session!!.update()
            val camera = frame.camera
            // Retrieves the latest depth image for this frame.
            // 이 프레임의 최신 깊이 이미지를 검색합니다.
            if (isDepthSupported) {
                Log.d(TAG, "centerX: $centerX, centerY: $centerY, distance: ${depthTexture.distance}")
          //      if(centerX!= null && centerY!=null)
                depthTexture.update(frame, centerX, centerY)
                Log.d(TAG, "depthTexture__centerX: $centerX, centerY: $centerY, distance: ${depthTexture.distance}")
                onUpdateDepthImage(depthTexture.distance)

//                Log.d(TAG, "onDrawFrame: ${getMillimetersDepth(frame.acquireDepthImage16Bits(), 0, 0)}")
            }

            // Handle one tap per frame.
            // 프레임당 하나의 탭을 처리합니다.
//            handleTap(frame, camera)

            // If frame is ready, render camera preview image to the GL surface.
            // 프레임이 준비되면 카메라 미리보기 이미지를 GL 표면으로 렌더링합니다.
            backgroundRenderer.draw(frame)
            if (showDepthMap) {
                backgroundRenderer.drawDepth(frame)
            }

            // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
            // 추적하는 동안 화면을 잠금 해제 상태로 유지하지만 추적이 중지되면 화면이 잠길 수 있습니다.
            trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

            // If not tracking, don't draw 3D objects, show tracking failure reason instead.
            // 추적하지 않는 경우 3D 개체를 그리지 않고 대신 추적 실패 이유를 표시합니다.
            if (camera.trackingState == TrackingState.PAUSED) {
                messageSnackbarHelper.showMessage(
                    requireActivity(), TrackingStateHelper(requireActivity()).getTrackingFailureReasonString(camera)!!
                )
                return
            }

            // Get projection matrix.
            val projmtx = FloatArray(16)
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f)

            // Get camera matrix and draw.
            val viewmtx = FloatArray(16)
            camera.getViewMatrix(viewmtx, 0)

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            // 이미지의 평균 강도에서 조명을 계산합니다.
            // 처음 세 가지 구성 요소는 색상 스케일링 요소입니다.
            // 마지막은 감마 공간의 평균 픽셀 강도입니다.
            val colorCorrectionRgba = FloatArray(4)
            frame.lightEstimate.getColorCorrection(colorCorrectionRgba, 0)

            try {
                val image = frame.acquireCameraImage()
                val bitmap = RotateBitmap(imageToBitmap(image, requireContext()), 90f)
                CoroutineScope(Dispatchers.Main).launch {
                    processFrame(bitmap)
                    image.close()
                }

            }catch (e: Exception){
                Log.d(TAG, "onDrawFrame: ${e.message}")
            }



            // No tracking error at this point. Inform user of what to do based on if planes are found.
            var messageToShow = ""
            messageToShow = if (hasTrackingPlane()) {
                PLANES_FOUND_MESSAGE
            } else {
                SEARCHING_PLANE_MESSAGE
            }
            if (!isDepthSupported) {
                messageToShow += """
                
                ${DEPTH_NOT_AVAILABLE_MESSAGE}
                """.trimIndent()
            }
            messageSnackbarHelper.showMessage(requireActivity(), messageToShow)

            // Visualize anchors created by touch.
            // 터치로 생성된 앵커를 시각화합니다.
            val scaleFactor = 1.0f
            for (anchor in anchors) {
                if (anchor.trackingState != TrackingState.TRACKING) {
                    continue
                }
                // Get the current pose of an Anchor in world space.
                // The Anchor pose is updated during calls to session.update() as ARCore refines its estimate of the world.
                // 세계 공간에서 앵커의 현재 포즈를 가져옵니다.
                // 앵커 포즈는 ARCore가 세계 추정치를 구체화함에 따라 session.update()를 호출하는 동안 업데이트됩니다.
                anchor.pose.toMatrix(anchorMatrix, 0)

                // Update and draw the model and its shadow.
                // 모델과 그림자를 업데이트하고 그립니다.
                virtualObject.updateModelMatrix(anchorMatrix, scaleFactor)
                virtualObject.draw(
                    viewmtx,
                    projmtx,
                    colorCorrectionRgba,
                    OBJECT_COLOR
                )
            }


        } catch (t: Throwable) {
            // Avoid crashing the application due to unhandled exceptions.
            // 처리되지 않은 예외로 인한 애플리케이션 충돌을 방지합니다.
            Log.e(
                TAG,
                "Exception on the OpenGL thread",
                t
            )
        }
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private fun handleTap(frame: Frame, camera: Camera) {
        val tap = tapHelper!!.poll()
        if (tap != null && camera.trackingState == TrackingState.TRACKING) {
            for (hit in frame.hitTest(tap)) {
                // Check if any plane was hit, and if it was hit inside the plane polygon
                val trackable = hit.trackable
                // Creates an anchor if a plane or an oriented point was hit.
                if ((trackable is Plane
                            && trackable.isPoseInPolygon(hit.hitPose)
                            && calculateDistanceToPlane(hit.hitPose, camera.pose) > 0)
                    || (trackable is Point
                            && trackable.orientationMode
                            == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
                ) {
                    // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    if (anchors.size >= 20) {
                        anchors[0].detach()
                        anchors.removeAt(0)
                    }

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
                    anchors.add(hit.createAnchor())
                    break
                }
            }
        }
    }

    // Checks if we detected at least one plane.
    private fun hasTrackingPlane(): Boolean {
        Log.d(TAG, "hasTrackingPlane()")
        for (plane in session!!.getAllTrackables(Plane::class.java)) {
            if (plane.trackingState == TrackingState.TRACKING) {
                return true
            }
        }
        return false
    }

    // Calculate the normal distance to plane from cameraPose, the given planePose should have y axis
    // parallel to plane's normal, for example plane's center pose or hit test pose.
    private fun calculateDistanceToPlane(planePose: Pose, cameraPose: Pose): Float {
        Log.d(TAG, "calculateDistanceToPlane")
        val normal = FloatArray(3)
        val cameraX = cameraPose.tx()
        val cameraY = cameraPose.ty()
        val cameraZ = cameraPose.tz()
        // Get transformed Y axis of plane's coordinate system.
        planePose.getTransformedAxis(1, 1.0f, normal, 0)
        // Compute dot product of plane's normal with vector from camera to plane center.
        return (cameraX - planePose.tx()) * normal[0] + (cameraY - planePose.ty()) * normal[1] + (cameraZ - planePose.tz()) * normal[2]
    }


    private fun onUpdateDepthImage(distance: Int) {
        Log.d(TAG, "onUpdateDepthImage")
        var cm = (distance / 10.0).toFloat()
        if(cm > 100){
            cm /= 100
            binding.tvDistance.text = "%.1f m".format(cm)
        }else{
            binding.tvDistance.text = "${cm.toInt()} cm"
        }
    }

    private fun createImageProcessor() {
        Log.d(TAG, "createImageProcessor()")
        stopImageProcessor()
        imageProcessor =
            try {
                when (selectedModel) {
                    OBJECT_DETECTION_CUSTOM -> {
                        Log.i(TAG, "Using Custom Object Detector (with object labeler) Processor")
                        val localModel =
                            LocalModel.Builder().setAssetFilePath("custom_models/object_labeler.tflite").build()
                        val customObjectDetectorOptions =
                            PreferenceUtils.getCustomObjectDetectorOptionsForLivePreview(requireContext(), localModel)
                        objectImageProcessor = ObjectDetectorProcessor(requireContext(), customObjectDetectorOptions)
                        objectImageProcessor!!.centerX.observe(viewLifecycleOwner){
                            centerX = it
                        }
                        objectImageProcessor!!.centerY.observe(viewLifecycleOwner){
                            centerY = it
                        }
                        objectImageProcessor
                    }
                    TEXT_RECOGNITION_KOREAN -> {
                        Log.i(TAG, "Using on-device Text recognition Processor for Latin and Korean")
                        TextRecognitionProcessor(requireContext(), KoreanTextRecognizerOptions.Builder().build())
                    }
                    else -> throw IllegalStateException("Invalid model name")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Can not create image processor: $selectedModel", e)
                Toast.makeText(
                    requireContext(),
                    "Can not create image processor: " + e.localizedMessage,
                    Toast.LENGTH_LONG
                )
                    .show()
                return
            }
    }

    private fun stopImageProcessor() {
        Log.d(TAG, "stopImageProcessor()")
        if (imageProcessor != null) {
            imageProcessor!!.stop()
            imageProcessor = null
            processing = false
            pending = false
        }
    }

    override fun onInit(p0: Int) {
//        if(p0 == TextToSpeech.SUCCESS) {
//            tts.setLanguage(Locale.KOREAN)
//            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
//                override fun onStart(p0: String?) {}
//                override fun onDone(p0: String?) {}
//                override fun onError(p0: String?) {}
//            })
//        }
    }

    fun speakOut(text: String) {
//        tts.setPitch(1f)
//        tts.setSpeechRate(1f)
//        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "id1")
    }
}