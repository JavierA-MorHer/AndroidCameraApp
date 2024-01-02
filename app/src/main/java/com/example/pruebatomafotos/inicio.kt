package com.example.pruebatomafotos

import android.app.ProgressDialog
import android.content.ContentValues.TAG
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.UUID

class inicio : AppCompatActivity() {
    private lateinit var chooseImg: Button
    private lateinit var uploadImg: Button
    private lateinit var openCamera: ImageButton

    private lateinit var imageView: ImageView
    private var fileUri: Uri? = null
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio)

        chooseImg = findViewById(R.id.choose_image)
        uploadImg = findViewById(R.id.upload_image)
        openCamera = findViewById(R.id.openCamera)

        imageView = findViewById(R.id.image_view)
        imageView.rotation = 360F;

        auth = FirebaseAuth.getInstance()

        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {

                    Log.d(TAG, "signInAnonymously:success")
                    val user = auth.currentUser
                    if (user != null) {
                        Log.d(TAG, "${user.email}")
                    }
                } else {
                    Log.w(TAG, "signInAnonymously:failure", task.exception)
                    Toast.makeText(
                        baseContext,
                        "Authentication failed.",
                        Toast.LENGTH_SHORT,
                    ).show()

                }
            }

       /* val email = "javiermorales1127@gmail.com"
        val password = "123456"


        signInWithEmailAndPassword(email, password)*/

        chooseImg.setOnClickListener {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(
                Intent.createChooser(intent, "Choose Image to Upload"), 0
            )
        }
        uploadImg.setOnClickListener {
            if (fileUri != null) {
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    uploadImage()
                } else {
                    Toast.makeText(this, "NO AUTENTICADO", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(applicationContext, "Please Select Image to Upload", Toast.LENGTH_LONG).show()
            }
        }
        openCamera.setOnClickListener {
            openCameraActivity()
        }
    }

    private fun openCameraActivity() {
        val intent = Intent(this, CameraOpenActivity::class.java)
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0 && resultCode == RESULT_OK && data != null && data.data != null) {
            fileUri = data.data
            try {
                val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, fileUri)
                imageView.setImageBitmap(bitmap)
            } catch (_: Exception) {
            }
        }
    }

    private fun signInWithEmailAndPassword(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                } else {
                    Log.w("Authentication", "signInWithEmail:failure", task.exception)
                    Toast.makeText(baseContext, "Authentication failed.",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun uploadImage() {
        if (fileUri != null) {
            val progressDialog = ProgressDialog(this)
            progressDialog.setTitle("Uploading Image...")
            progressDialog.setMessage("Processing...")
            progressDialog.show()

            val ref: StorageReference = FirebaseStorage.getInstance().getReference()
                .child("images/${UUID.randomUUID()}")
            ref.putFile(fileUri!!).addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(applicationContext, "File Uploaded Successfully", Toast.LENGTH_LONG)
                    .show()
            }.addOnFailureListener {
                progressDialog.dismiss()
                Toast.makeText(applicationContext, "File Upload Failed...", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }
}