package com.surendramaran.yolov8tflite

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.navigation.NavigationView
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.surendramaran.yolov8tflite.Constants.LABELS_PATH
import com.surendramaran.yolov8tflite.Constants.MODEL_PATH
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.isNotEmpty

class MainActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityMainBinding
    private val isFrontCamera = false
    private lateinit var locationManager: LocationManager

    private lateinit var firebaseStorage: FirebaseStorage
    private lateinit var storageReference: StorageReference
    private lateinit var firebaseDatabase: FirebaseDatabase
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val latitude = location.latitude
            val longitude = location.longitude
            val address = getAddressFromLocation(latitude, longitude)
            updateUI(latitude, longitude, address)
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var detector: Detector? = null

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private lateinit var swipeHintImage: ImageView
    private lateinit var swipeHintText: TextView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            handleNavigationItemSelected(menuItem)
            drawerLayout.closeDrawers()
            true
        }

        // Initialize swipe hint elements
        swipeHintImage = findViewById(R.id.swipe_hint)
        swipeHintText = findViewById(R.id.swipe_hint_text)

        // Initialize Firebase Storage and Database
        firebaseStorage = FirebaseStorage.getInstance()
        storageReference = firebaseStorage.reference
        firebaseDatabase = FirebaseDatabase.getInstance()

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Create the animation for the swipe hint arrow
        val animation = ObjectAnimator.ofFloat(swipeHintImage, "translationX", 0f, 20f, 0f)
        animation.duration = 500  // Adjust duration to your liking
        animation.repeatCount = ValueAnimator.INFINITE  // Make it repeat infinitely
        animation.repeatMode = ValueAnimator.REVERSE  // Bounce back and forth
        animation.start()


        // Drawer setup
        drawerLayout = findViewById(R.id.drawer_layout)
        actionBarDrawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, R.string.nav_open, R.string.nav_close
        )
        drawerLayout.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)


        // Navigation item selection
        navigationView.setBackgroundResource(R.drawable.nav_border)
        navigationView.setPadding(0, 25, 0, 0)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            handleNavigationItemSelected(menuItem)
            true
        }



        // Camera and detector setup
        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor.execute {
            detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
            detector?.setup()
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        bindListeners()
    }

    private fun handleNavigationItemSelected(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.map -> {

                val intent = Intent(this@MainActivity, LeafLetMap::class.java)
                startActivity(intent)

            }

            R.id.detect -> {

            }

            R.id.close -> {
                finish() // Close the app
            }
        }
        drawerLayout.closeDrawers() // Always close the drawer after handling the item
    }


    private fun bindListeners() {
        binding.apply {
            isGpu.setOnCheckedChangeListener { buttonView, isChecked ->
                cameraExecutor.submit {
                    detector?.setup(isGpu = isChecked)
                }
                if (isChecked) {
                    buttonView.setBackgroundColor(
                        ContextCompat.getColor(
                            baseContext,
                            R.color.orange
                        )
                    )
                } else {
                    buttonView.setBackgroundColor(ContextCompat.getColor(baseContext, R.color.gray))
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            val image = imageProxy.image
            if (image != null) {
                val buffer = image.planes[0].buffer
                val bitmapBuffer = Bitmap.createBitmap(
                    imageProxy.width,
                    imageProxy.height,
                    Bitmap.Config.ARGB_8888
                )
                buffer.rewind()
                bitmapBuffer.copyPixelsFromBuffer(buffer)

                val matrix = Matrix().apply {
                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                    if (isFrontCamera) {
                        postScale(-1f, 1f, imageProxy.width / 2f, imageProxy.height / 2f)
                    }
                }

                val rotatedBitmap = Bitmap.createBitmap(
                    bitmapBuffer,
                    0,
                    0,
                    bitmapBuffer.width,
                    bitmapBuffer.height,
                    matrix,
                    true
                )

                // Save the rotated bitmap to Firebase Storage
                detector?.detect(rotatedBitmap)
                saveDetectionData(rotatedBitmap, getLastKnownLocation())
            }
            imageProxy.close()
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it[Manifest.permission.CAMERA] == true) {
            startCamera()
        }
        if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true || it[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            requestLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
            requestLocationUpdates()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    override fun onEmptyDetect() {
        runOnUiThread {
            binding.overlay.clear()
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            binding.overlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }

            // Capture image when a pothole is detected
            if (boundingBoxes.isNotEmpty()) {
                val bitmap = getBitmapFromView(binding.viewFinder)
                val location = getLastKnownLocation()
                saveDetectionData(bitmap, location)
            }
        }
    }


    private fun requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000, // interval (1 second)
                10f, // distance (10 meters)
                locationListener
            )
        }
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double): String {
        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
        return if (addresses != null && addresses.isNotEmpty()) {
            val address = addresses[0]
            "${address.getAddressLine(0)}, ${address.adminArea}, ${address.countryName}"
        } else {
            "Unknown location"
        }
    }

    private fun updateUI(latitude: Double, longitude: Double, address: String) {
        runOnUiThread {
            val textView = findViewById<TextView>(R.id.location_textview)
            textView.text = "Latitude: $latitude, Longitude: $longitude\nAddress: $address"
        }
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }


    private fun getLastKnownLocation(): Location? {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }
        return null
    }

    // Saving bitmap to Firebase Storage
    private fun saveDetectionData(bitmap: Bitmap, location: Location?) {
        if (bitmap.isRecycled) {
            Log.e(TAG, "Bitmap is recycled")
            return
        }

        val filename = "${System.currentTimeMillis()}.png"
        val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), filename)

        try {
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                Log.d(TAG, "Image saved to file: ${file.absolutePath}")
            }

            val imageUri = Uri.fromFile(file)
            val storageRef = FirebaseStorage.getInstance().reference.child("images/$filename")

            storageRef.putFile(imageUri).addOnCompleteListener { uploadTask ->
                if (uploadTask.isSuccessful) {
                    // Retrieve the download URL after successful upload
                    storageRef.downloadUrl.addOnCompleteListener { urlTask ->
                        if (urlTask.isSuccessful) {
                            val imageUrl = urlTask.result.toString()
                            Log.d(TAG, "Image URL: $imageUrl")
                            // Save additional data with the correct URL
                            saveAdditionalData(filename, imageUrl, location)
                        } else {
                            Log.e(TAG, "Failed to get download URL: ${urlTask.exception}")
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to upload image: ${uploadTask.exception}")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun saveAdditionalData(filename: String, imageUrl: String?, location: Location?) {
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("images")

        val latitude = location?.latitude ?: "unknown"
        val longitude = location?.longitude ?: "unknown"
        val address =
            location?.let { getAddressFromLocation(it.latitude, it.longitude) } ?: "unknown"
        val timestamp = System.currentTimeMillis()

        // Add status field with default value "unfixed"
        val data = mapOf(
            "filename" to filename,
            "imageUrl" to imageUrl,
            "latitude" to latitude,
            "longitude" to longitude,
            "address" to address,
            "timestamp" to timestamp,
            "status" to "unfixed" // Default status
        )

        ref.push().setValue(data).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Additional data saved successfully")
            } else {
                Log.e(TAG, "Failed to save additional data: ${task.exception}")
            }
        }
    }


}

