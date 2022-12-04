package com.ispsolutionsofficial.drawingapp

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import android.app.AlertDialog as AlertDialog

class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null
    private var progressBarDialog: Dialog? = null

    private val openGalleryLauncher:ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result ->
        if(result.resultCode == RESULT_OK && result.data != null) {
            val ivBackground: ImageView = findViewById(R.id.ivBackground)
            ivBackground.setImageURI(result.data?.data)
        }
    }

    private val requestPermission: ActivityResultLauncher<Array<String>> = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
        permissions.entries.forEach {
            val permissionName = it.key
            val isGranted = it.value
            if(isGranted) {
                Toast.makeText(this, "Permission Granted for storage access", Toast.LENGTH_LONG).show()
                val pickIntent = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryLauncher.launch(pickIntent)
            } else {
                if(permissionName==Manifest.permission.READ_EXTERNAL_STORAGE) {
                    Toast.makeText(this,"Oops! You just denied the permission", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawingView)
        drawingView?.setSizeForBrush(20.toFloat())



        val linearLayout: LinearLayout = findViewById(R.id.llPaintColors)
        mImageButtonCurrentPaint = linearLayout[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )

        val ibBrush:ImageButton = findViewById(R.id.ibBrush)
        ibBrush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val ibUndo:ImageButton = findViewById(R.id.ibUndo)
        ibUndo.setOnClickListener {
            drawingView?.onClickUndo()
        }

        val ibGallery:ImageButton = findViewById(R.id.ibGallery)
        ibGallery.setOnClickListener {
            requestStoragePermission()
        }

        val ibSave:ImageButton = findViewById(R.id.ibSave)
        ibSave.setOnClickListener {
            if (isReadStorageAllowed()) {
                showProgressBarDialog()
                lifecycleScope.launch {
                    val flDrawingView:FrameLayout = findViewById(R.id.flDrawingViewContainer)
                    saveBitmapFile(getBitmapFromView(flDrawingView))
                }
            }
        }
    }

    private fun isReadStorageAllowed(): Boolean {
        val result = ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showRationaleDialog("Drawing App", "App needs to access your external storage")
        } else {
            requestPermission.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog:Dialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")
        val smallBtn: ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn: ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn: ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view: View) {
        if(view !== mImageButtonCurrentPaint) {
            val imageButton: ImageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)
            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
            )
            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )
            mImageButtonCurrentPaint = view
        }
    }

    private fun showRationaleDialog(title:String, message:String) {
        val builder:AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Cancel") { dialog, _->
            dialog.dismiss()
        }
        builder.create().show()
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if(bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String {
        var result = ""
        withContext(Dispatchers.IO) {
            if(mBitmap!=null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
                    val file = File(externalCacheDir?.absoluteFile.toString() + File.separator + "DrawingApp_"
                            + System.currentTimeMillis()/1000 + ".png")
                    val fileOutput = FileOutputStream(file)
                    fileOutput.write(bytes.toByteArray())
                    fileOutput.close()
                    result = file.absolutePath

                    runOnUiThread {
                        progressBarDialog?.dismiss()
                        if(result != "") {
                            Toast.makeText(this@MainActivity, "File Saved Successfully at $result", Toast.LENGTH_LONG).show()
                            shareImage(result)
                        } else {
                            Toast.makeText(this@MainActivity, "Something went wrong while saving the file!", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showProgressBarDialog() {
        progressBarDialog = Dialog(this@MainActivity)
        progressBarDialog?.setContentView(R.layout.progress_bar_dialog)
        progressBarDialog?.show()
    }

    private fun shareImage(result:String) {
        MediaScannerConnection.scanFile(this, arrayOf(result), null) {
            path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share"))
        }
    }
}