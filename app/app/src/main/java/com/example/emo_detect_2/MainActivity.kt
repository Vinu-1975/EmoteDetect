package com.example.emo_detect_2


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

class MainActivity : AppCompatActivity() {
    private val REQUEST_CODE = 22
    private lateinit var buttonNavigate: Button
    private lateinit var buttonOpenGallery: Button
    private lateinit var imageView: ImageView
    private lateinit var textViewResult: TextView
    private lateinit var textViewPrimaryResult: TextView
    private lateinit var tflite: Interpreter

    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>

    private lateinit var galleryPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var galleryActivityResultLauncher: ActivityResultLauncher<Intent>


    // Define the mapping of indices to ASL characters
    private val aslCharacters = arrayOf("Anger", "Contempt", "Disgust", "Fear", "Happy", "Sadness", "Surprise") // No data for J and Z

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttonNavigate = findViewById(R.id.buttonNavigate)
        textViewResult = findViewById(R.id.textViewResult)
        textViewPrimaryResult = findViewById(R.id.textViewPrimaryResult)
        imageView = findViewById(R.id.imageView)
        buttonOpenGallery = findViewById(R.id.buttonNavigate2)
        tflite = Interpreter(loadModelFile())

        initializeCameraLauncher()
        initializeGalleryLauncher()

        setupButtons()

//        buttonNavigate.setOnClickListener {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
//                openCamera()
//            } else {
//                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
//            }
//        }
//
//        buttonOpenGallery.setOnClickListener {
//            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
//            galleryActivityResultLauncher.launch(intent)
//        }
    }

    private fun setupButtons() {
        buttonNavigate.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        buttonOpenGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryActivityResultLauncher.launch(intent)
        }
    }

    private fun initializeCameraLauncher() {
        cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to take pictures", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeGalleryLauncher() {
        galleryActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val data: Intent? = result.data
                val photo: Bitmap? = MediaStore.Images.Media.getBitmap(this.contentResolver, data?.data)
                if (photo != null) {
                    imageView.setImageBitmap(photo)
                    classifyImage(photo)
                } else {
                    Toast.makeText(this, "Failed to get image from gallery", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            val photo: Bitmap? = data?.extras?.get("data") as? Bitmap
            if (photo != null) {
                imageView.setImageBitmap(photo)
                classifyImage(photo)
            } else {
                Toast.makeText(this, "Failed to get image", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
        }
    }




//    private fun classifyImage(bitmap: Bitmap) {
//        // Resize the bitmap to 48x48
//        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
//
//        // Prepare input and output buffers
//        val input = Array(1) { Array(48) { Array(48) { FloatArray(1) } } }  // Single channel for grayscale
//        val output = Array(1) { FloatArray(7) }  // Adjusted output array size to match model output (7 classes)
//
//        // Convert bitmap to float array (normalize to [0, 1])
//        for (x in 0 until resizedBitmap.width) {
//            for (y in 0 until resizedBitmap.height) {
//                val pixel = resizedBitmap.getPixel(x, y)
//                // Convert RGB to grayscale by calculating the luminance
//                val gray = (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)) / 255.0f
//                input[0][x][y][0] = gray.toFloat()
//            }
//        }
//
//        // Run inference
//        tflite.run(input, output)
//
//        // Apply softmax to output to get probabilities
//        val probabilities = softmax(output[0])
//
//        // Find the index of the highest probability
//        val predictedIndex = probabilities.indexOfMax()
//
//        // Get the predicted character
//        val predictedCharacter = if (predictedIndex in aslCharacters.indices) {
//            aslCharacters[predictedIndex]
//        } else {
//            "Unknown"
//        }
//
//        // Display the predicted character
//        Toast.makeText(this, "Predicted character: $predictedCharacter", Toast.LENGTH_SHORT).show()
//    }

    private fun classifyImage(bitmap: Bitmap) {
        // Resize the bitmap to 48x48
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 48, 48, true)

        // Prepare input and output buffers
        val input = Array(1) { Array(48) { Array(48) { FloatArray(3) } } } // Use 3 channels for RGB
        val output = Array(1) { FloatArray(aslCharacters.size) } // Output size matches the number of classes

        // Convert bitmap to float array (normalize to [0, 1])
        for (x in 0 until resizedBitmap.width) {
            for (y in 0 until resizedBitmap.height) {
                val pixel = resizedBitmap.getPixel(x, y)
                input[0][x][y][0] = Color.red(pixel) / 255.0f
                input[0][x][y][1] = Color.green(pixel) / 255.0f
                input[0][x][y][2] = Color.blue(pixel) / 255.0f
            }
        }

        // Run inference
        tflite.run(input, output)
        val probabilities = softmax(output[0])
        val predictedIndex = probabilities.indexOfMax()
        val predictedCharacter = aslCharacters[predictedIndex]
        val emoji = getEmoji(predictedCharacter)  // This function needs to be defined to return an emoji based on the class.

        textViewPrimaryResult.text = "$predictedCharacter $emoji"
        val results = StringBuilder()
        aslCharacters.forEachIndexed { index, character ->
            val probability = probabilities[index] * 100
            results.append(String.format("%s: %.2f%%\n", character, probability))
        }
        textViewResult.text = results.toString()
    }



//    private fun classifyImage(bitmap: Bitmap) {
//        // Resize the bitmap to 48x48
//        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
//
//        // Prepare input and output buffers
//        val input = Array(1) { Array(48) { Array(48) { FloatArray(3) } } } // Use 3 channels for RGB
//        val output = Array(1) { FloatArray(7) } // Output size matches the number of classes
//
//        // Convert bitmap to float array (normalize to [0, 1])
//        for (x in 0 until resizedBitmap.width) {
//            for (y in 0 until resizedBitmap.height) {
//                val pixel = resizedBitmap.getPixel(x, y)
//                input[0][x][y][0] = Color.red(pixel) / 255.0f
//                input[0][x][y][1] = Color.green(pixel) / 255.0f
//                input[0][x][y][2] = Color.blue(pixel) / 255.0f
//            }
//        }
//
//        // Run inference
//        tflite.run(input, output)
//
//        // Apply softmax to output to get probabilities
//        val probabilities = softmax(output[0])
//
//        // Find the index of the highest probability
//        val predictedIndex = probabilities.indexOfMax()
//
//        // Get the predicted character
//        val predictedCharacter = if (predictedIndex in aslCharacters.indices) {
//            aslCharacters[predictedIndex]
//        } else {
//            "Unknown"
//        }
//        textViewResult.text = "Predicted: $predictedCharacter (${String.format("%.2f", probabilities[predictedIndex] * 100)}%)"
//        // Display the predicted character
//        Toast.makeText(this, "Predicted character: $predictedCharacter", Toast.LENGTH_SHORT).show()
//    }

    // Softmax function to convert logits into probabilities
    private fun softmax(logits: FloatArray): FloatArray {
        val expLogits = logits.map { exp(it) }
        val sumExp = expLogits.sum()
        return expLogits.map { it / sumExp }.toFloatArray()
    }

    private fun getEmoji(character: String): String {
        return when(character) {
            "Anger" -> "😠"
            "Contempt" -> "😒"
            "Disgust" -> "🤢"
            "Fear" -> "😨"
            "Happy" -> "😃"
            "Sadness" -> "😢"
            "Surprise" -> "😮"
            else -> "❓"
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = assets.openFd("model_fer_13x.tflite")
        //val assetFileDescriptor = assets.openFd("model4.tflite")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Extension function to find the index of the maximum value in an array
    private fun FloatArray.indexOfMax(): Int {
        return this.indices.maxByOrNull { this[it] } ?: -1
    }

    override fun onDestroy() {
        super.onDestroy()
        tflite.close()  // Close interpreter to free resources
    }
}