package com.example.carlicense

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.carlicense.R
import com.example.carlicense.network.ApiResponse
import com.example.carlicense.network.ApiService
import com.example.carlicense.network.RetrofitClient
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var resultImageView1: ImageView
    private lateinit var resultImageView2: ImageView
    private lateinit var selectedImageUri: Uri
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var textView: TextView
    private var currentCall: Call<ApiResponse>? = null

    private val REQUEST_GALLERY = 2
    private val REQUEST_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        resultImageView1 = findViewById(R.id.resultImageView1)
        resultImageView2 = findViewById(R.id.resultImageView2)
        textView = findViewById(R.id.textView)

        val buttonSelect = findViewById<Button>(R.id.buttonSelect)
        val buttonSend = findViewById<Button>(R.id.buttonSend)

        // 권한 요청
        if (!checkPermissions()) {
            requestPermissions()
        }

        buttonSelect.setOnClickListener {
            selectImage()
        }

        buttonSend.setOnClickListener {
            uploadImage()
        }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        swipeRefreshLayout.setOnRefreshListener {
            // 새로고침 동작 처리
            refreshScreen()
        }
    }

    private fun refreshScreen() {
        // 기존 데이터 초기화
        imageView.setImageResource(0) // 선택된 이미지를 초기화
        resultImageView1.setImageResource(0) // 서버 응답 이미지1 초기화
        resultImageView2.setImageResource(0) // 서버 응답 이미지2 초기화
        textView.text = "" // 텍스트 초기화
        currentCall?.cancel()

        // 애니메이션 종료
        swipeRefreshLayout.isRefreshing = false
    }

    private fun selectImage() {
        // 카메라 인텐트
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        // 갤러리 인텐트
        val pickPhotoIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickPhotoIntent.type = "image/*"

        // 인텐트 배열로 카메라와 갤러리 인텐트를 묶어줍니다
        val chooserIntent = Intent.createChooser(pickPhotoIntent, "사진 선택")
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent))

        // 인텐트 실행
        startActivityForResult(chooserIntent, REQUEST_GALLERY)
    }

    private fun setScaledBitmap(imageBase64: String, imageView: ImageView) {
        val decodedString: ByteArray = Base64.decode(imageBase64, Base64.DEFAULT)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size, options)

        // 적절한 크기로 스케일링
        options.inSampleSize = calculateInSampleSize(options, 500, 500) // 원하는 크기로 변경 가능
        options.inJustDecodeBounds = false

        val scaledBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size, options)
        imageView.setImageBitmap(scaledBitmap)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_GALLERY -> {
                    if (data != null && data.data != null) {
                        // 갤러리에서 이미지를 선택한 경우
                        val selectedImageUri = data.data
                        imageView.setImageURI(selectedImageUri)
                        selectedImageUri?.let {
                            this.selectedImageUri = it
                        }
                    } else {
                        // 카메라로 사진을 찍은 경우
                        val photo = data?.extras?.get("data") as? Bitmap
                        photo?.let {
                            imageView.setImageBitmap(it)
                            selectedImageUri = saveBitmapToUri(it)
                        }
                    }
                }
            }
        }
    }

    private fun saveBitmapToUri(bitmap: Bitmap): Uri {
        val file = File(cacheDir, "image.jpg")
        val fileOutputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
        fileOutputStream.flush()
        fileOutputStream.close()
        return Uri.fromFile(file)
    }

    private fun uploadImage() {

        if (!::selectedImageUri.isInitialized) {
            showErrorDialog("사진을 먼저 선택해주세요")
            return
        }

        contentResolver.openInputStream(selectedImageUri)?.let { inputStream ->
            val file = File(cacheDir, "upload_image.jpg")
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            outputStream.close()

            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE

            val apiService = RetrofitClient.instance.create(ApiService::class.java)
            currentCall  = apiService.uploadImage(body)

            currentCall?.enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {

                    findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE


                    if (response.isSuccessful) {
                        val result = response.body()
                        result?.let {
                            // prediction 이미지 표시
                            it.prediction?.let { base64Image ->
                                setScaledBitmap(base64Image, resultImageView1)
                                resultImageView1.visibility = View.VISIBLE
                            }

                            // license_plate_image 표시
                            it.license_plate_image?.let { base64Image ->
                                setScaledBitmap(base64Image, resultImageView2)
                                resultImageView2.visibility = View.VISIBLE
                            }

                            // 텍스트 출력
                            val texts = it.texts?.joinToString(separator = "\n")
                            textView.text = texts
                        }
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE

                    if (call.isCanceled) {
                        Log.i("Upload Cancelled", "Request was cancelled.")
                        return
                    }

                    Log.i("Upload Error", t.message.toString())
                    if (t is SocketTimeoutException) {
                        showErrorDialog("서버요청시간 초과 관리자에게 문의해주세요")
                    } else {
                        showErrorDialog(t.message ?: "알 수 없는 에러가 발생했습니다.")
                    }
                }
            })
        } ?: Log.e("Upload Error", "Failed to open input stream")
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("에러!")
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun checkPermissions(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val readStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        val writeStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        return cameraPermission == PackageManager.PERMISSION_GRANTED &&
                readStoragePermission == PackageManager.PERMISSION_GRANTED &&
                writeStoragePermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            REQUEST_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // 모든 권한이 허용되었습니다.
            } else {
                // 일부 권한이 거부되었습니다.
            }
        }
    }
}
