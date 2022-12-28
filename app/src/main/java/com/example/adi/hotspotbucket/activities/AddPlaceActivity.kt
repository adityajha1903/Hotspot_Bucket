package com.example.adi.hotspotbucket.activities

import android.Manifest.permission.*
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.adi.hotspotbucket.R
import com.example.adi.hotspotbucket.database.PlaceEntity
import com.example.adi.hotspotbucket.database.PlacesDatabase
import com.example.adi.hotspotbucket.databinding.ActivityAddPlaceBinding
import com.example.adi.hotspotbucket.databinding.BottomSheetDialogBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*


class AddPlaceActivity : AppCompatActivity(), View.OnClickListener {

    private var binding: ActivityAddPlaceBinding? = null
    private val cameraReqCode = 1
    private val readReqCode = 0
    private var imageUri: Uri? = null
    private var imageFile: File? = null
    private var newPlaceId = 0
    private var latitude: Double? = 10000000.0
    private var longitude: Double? = 10000000.0

    private val resultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent: Intent? = result.data
            if (intent != null) {
                latitude = intent.getDoubleExtra(SELECTED_LOCATION_LATITUDE, 10000000.0)
                longitude = intent.getDoubleExtra(SELECTED_LOCATION_LONGITUDE, 10000000.0)
                if (longitude != 10000000.0 && latitude != 10000000.0) {
                    binding?.IBSelectLocation?.setImageResource(R.drawable.location_selected_icon)
                }
            }
        }
    }

    private val openCameraLauncher = registerForActivityResult(TakePicture()) {
        if (it) {
            binding?.placeIV?.setImageURI(imageUri)
        } else {
            imageUri = null
            imageFile = null
        }
    }

    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                if (imageUri != null && imageFile != null) {
                    if (imageFile?.exists()!!) {
                        imageFile?.delete()
                    }
                    imageUri = null
                    imageFile = null
                }
                binding?.placeIV?.setImageURI(result.data?.data)
                val viewBitmap = binding?.placeIV?.width
                    ?.let { Bitmap.createBitmap(it, binding?.placeIV?.height!!, Bitmap.Config.ARGB_8888) }
                val canvas = viewBitmap?.let { Canvas(it) }
                binding?.placeIV?.draw(canvas)
                imageUri = viewBitmap?.let { saveImageToInternalStorage(it) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_place)

        binding = ActivityAddPlaceBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        setSupportActionBar(binding?.addPlacesToolBar)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                finish()
            }
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            })
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding?.addPlacesToolBar?.setNavigationOnClickListener {
            finish()
        }

        newPlaceId = intent.getIntExtra("lats_place_id", 0) + 1

        binding?.eTDate?.setOnClickListener(this)

        binding?.selectImageBtn?.setOnClickListener {
            val dialogBinding = BottomSheetDialogBinding.inflate(layoutInflater)
            val dialog = BottomSheetDialog(this@AddPlaceActivity, R.style.BottomSheetStyle)
            dialog.setContentView(dialogBinding.root)
            dialogBinding.closeDialog.setOnClickListener { dialog.dismiss() }
            dialogBinding.cameraIB.setOnClickListener {
                askPermissionForCamera()
                dialog.dismiss()
            }
            dialogBinding.galleryIB.setOnClickListener {
                askPermissionForRead()
                dialog.dismiss()
            }
            dialog.show()
        }

        binding?.IBSelectLocation?.setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            resultLauncher.launch(intent)
        }

        binding?.saveBtn?.setOnClickListener {
            if (
                imageFile == null || binding?.eTTitle?.text.isNullOrEmpty() || binding?.eTDescription?.text.isNullOrEmpty() || binding?.eTDate?.text.isNullOrEmpty() || binding?.eTLocation?.text.isNullOrEmpty() || latitude == 10000000.0 || longitude == 10000000.0
            ) {
                Toast.makeText(this, "Please fill all the required fields", Toast.LENGTH_SHORT).show()
            } else {
                addPlaceToDatabase()
                finish()
            }
        }

    }

    private fun addPlaceToDatabase() {
        val place = PlaceEntity(newPlaceId, binding?.eTTitle?.text.toString(), imageUri.toString(), binding?.eTDescription?.text.toString(), binding?.eTDate?.text.toString(), binding?.eTLocation?.text.toString(),
            latitude!!, longitude!!)
        val db = Room.databaseBuilder(applicationContext, PlacesDatabase::class.java, "place_database").build()
        val placesDao = db.placesDao()
        lifecycleScope.launch {
            placesDao.insert(place)
        }
    }


    private fun askPermissionForRead() {
        if (ActivityCompat.checkSelfPermission(this, CAMERA) == PackageManager.PERMISSION_GRANTED) {
            pickImageUsingGallery()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(
                    READ_EXTERNAL_STORAGE
                )
            ) {
                showAlertDialog()
            } else {
                ActivityCompat.requestPermissions(
                    this@AddPlaceActivity,
                    arrayOf(CAMERA, READ_EXTERNAL_STORAGE),
                    readReqCode
                )
            }
        }
    }

    private fun askPermissionForCamera() {
        if (ActivityCompat.checkSelfPermission(this, CAMERA) == PackageManager.PERMISSION_GRANTED) {
            pickImageUsingCamera()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(CAMERA)) {
                showAlertDialog()
            } else {
                ActivityCompat.requestPermissions(
                    this@AddPlaceActivity,
                    arrayOf(CAMERA, READ_EXTERNAL_STORAGE),
                    cameraReqCode
                )
            }
        }
    }

    private fun pickImageUsingGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        openGalleryLauncher.launch(intent)
    }

    private fun pickImageUsingCamera() {
        if (imageUri != null && imageFile != null) {
            if (imageFile?.exists()!!) {
                imageFile?.delete()
            }
            imageUri = null
            imageFile = null
        }
        imageFile = File(applicationContext.filesDir, "${UUID.randomUUID()}.jpg")
        imageUri = FileProvider.getUriForFile(applicationContext, "com.example.adi.hotspotbucket.fileProvider", imageFile!!)
        openCameraLauncher.launch(imageUri)
    }

    private fun showAlertDialog() {
        AlertDialog.Builder(this).setMessage("" +
        "It looks like you have turned off permission required " +
        "for this feature. It can be enabled under the " +
        "Application Settings")
            .setPositiveButton("SETTINGS") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") {dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): Uri {
        val wrapper = ContextWrapper(applicationContext)
        imageFile = wrapper.getDir(IMAGE_DIRECTORY, Context.MODE_PRIVATE)
        imageFile = File(imageFile, "${UUID.randomUUID()}.jpg")

        try {
            val stream: OutputStream = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.flush()
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return Uri.parse(imageFile!!.absolutePath)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            cameraReqCode -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pickImageUsingCamera()
                }
            }
            readReqCode ->
                if (grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    pickImageUsingGallery()
                }
        }
    }

    override fun onClick(p0: View?) {
        when(p0?.id) {
            R.id.eTDate -> {
                val cal = Calendar.getInstance()
                DatePickerDialog(this@AddPlaceActivity, { _, year, month, dayOfMonth ->
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, month)
                    cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    val format = "dd/MM/yyyy"
                    val sdf = SimpleDateFormat(format, Locale.getDefault())
                    binding?.eTDate?.setText(sdf.format(cal.time).toString())
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    companion object {
        const val IMAGE_DIRECTORY = "image_directory"
        const val SELECTED_LOCATION_LATITUDE = "latitude"
        const val SELECTED_LOCATION_LONGITUDE = "longitude"
    }
}