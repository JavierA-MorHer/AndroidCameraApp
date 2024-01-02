package com.example.pruebatomafotos

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.UUID

class CameraOpenActivity : AppCompatActivity() {

    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private lateinit var capReq: CaptureRequest.Builder
    private lateinit var cameraManager: CameraManager
    private lateinit var textureView: TextureView
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var cameraDevice: CameraDevice
    lateinit var imageReader:ImageReader
    var uri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getPermissions()
        loadCameraConfig()

    }

    private fun loadCameraConfig(){
        textureView = findViewById(R.id.textureView)
        textureView.rotation = (-90).toFloat()

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        handlerThread = HandlerThread("videoThread")
        handlerThread.start()

        handler = Handler((handlerThread).looper)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                openCamera()
            }
            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {}
            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean { return false }
            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {}
        }

        imageReader = ImageReader.newInstance(3264,2448,ImageFormat.JPEG,10)
        imageReader.setOnImageAvailableListener(object :ImageReader.OnImageAvailableListener{
            override fun onImageAvailable(p0: ImageReader?) {
                val image = imageReader.acquireLatestImage()

                val buffer = image?.planes?.get(0)?.buffer
                val bytes = ByteArray(buffer?.remaining() ?: 0)
                buffer?.get(bytes)

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "${UUID.randomUUID()}.jpeg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }

                val contentResolver = applicationContext.contentResolver
                uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)


                uri?.let {

                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(bytes)
                    }

                    openModal(it)

                    // Añadir la imagen a la galería
                    /*val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    mediaScanIntent.data = uri
                    applicationContext.sendBroadcast(mediaScanIntent)*/

                } ?: run {
                    Log.e("GuardarImagen", "No se pudo obtener la URI de la imagen en la galería")
                }
                image?.close()
            }
        },handler)

        findViewById<ImageButton>(R.id.takePhoto).apply {
            setOnClickListener{
                capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                capReq.addTarget(imageReader.surface)
                cameraCaptureSession.capture(capReq.build(),null,null)
            }
        }
    }

    private fun openModal(it: Uri) {
        val dialogView = layoutInflater.inflate(R.layout.modal_subir_foto, null)
        val imageView:ImageView = dialogView.findViewById(R.id.image_viewModal)

        val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
        imageView.setImageBitmap(bitmap)

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Subir Foto") { dialog, which ->
                uploadImage()
            }
            .setNegativeButton("Reintentar Foto") { dialog, which ->
                dialog.dismiss()
            }
            .create()

        alertDialog.setCancelable(false);
        alertDialog.show()
    }

    private fun uploadImage() {
        if (uri != null) {
            val progressDialog = ProgressDialog(this)
            progressDialog.setTitle("Uploading Image...")
            progressDialog.setMessage("Processing...")
            progressDialog.show()

            val ref: StorageReference = FirebaseStorage.getInstance().getReference()
                .child("${UUID.randomUUID()}/${UUID.randomUUID()}")

            ref.putFile(uri!!)
                .addOnSuccessListener {
                    progressDialog.dismiss()

                    // Obtén el ID de la última imagen insertada
                    val cursor: Cursor? = contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Images.Media._ID),
                        null,
                        null,
                        MediaStore.Images.Media.DATE_ADDED + " DESC"
                    )

                    if (cursor != null && cursor.moveToFirst()) {
                        val id: Long = cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media._ID))

                        // Construye la URI específica para esa imagen
                        val specificImageUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())

                        // Borra la última imagen
                        deleteImage(specificImageUri)
                    }

                    Toast.makeText(applicationContext, "La foto se subió correctamente", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener {
                    progressDialog.dismiss()
                    Toast.makeText(applicationContext, "La foto no se pudo subir, inténtelo de nuevo más tarde", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun deleteImage(imageUri: Uri) {
        val contentResolver = applicationContext.contentResolver
        try {
            contentResolver.delete(imageUri, null, null)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }


    @SuppressLint("MissingPermission")
    fun openCamera() {
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0

                // Obtener el tamaño óptimo para la vista previa
                val optimalSize = getOptimalPreviewSize()

                // Calcular el tamaño deseado con relación de aspecto 16:9
                val targetWidth = optimalSize.height * 16 / 9

                // Configurar el tamaño de la TextureView
                textureView.layoutParams.width = targetWidth
                textureView.layoutParams.height = optimalSize.height

                textureView.surfaceTexture?.setDefaultBufferSize(optimalSize.width, optimalSize.height)

                capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                val surface = Surface(textureView.surfaceTexture)
                capReq.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface, imageReader.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(p0: CameraCaptureSession) {
                        cameraCaptureSession = p0
                        cameraCaptureSession.setRepeatingRequest(capReq.build(), null, null)
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {}
                }, handler)
            }

            override fun onDisconnected(p0: CameraDevice) {}
            override fun onError(p0: CameraDevice, p1: Int) {}
        }, handler)
    }

    private fun getOptimalPreviewSize(): Size {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = manager.getCameraCharacteristics(cameraManager.cameraIdList[0])
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()

        return sizes.maxByOrNull { it.width * it.height } ?: sizes[0]
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraDevice.close()
        handler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()
    }

    private fun getPermissions(){
        val permissionsList = mutableListOf<String>()

        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(android.Manifest.permission.CAMERA)
        }
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissionsList.isNotEmpty()) {
            requestPermissions(permissionsList.toTypedArray(), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            for (i in grantResults.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.e("Permission", "Permiso ${permissions[i]} denegado.")
                } else {
                    Log.d("Permission", "Permiso ${permissions[i]} concedido.")
                }
            }
        }
    }
}