package com.noodoe.robert.azuresample

import android.app.Activity.RESULT_OK
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.AsyncTask
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.jakewharton.rxbinding.view.RxView
import com.microsoft.projectoxford.face.FaceServiceClient
import com.microsoft.projectoxford.face.FaceServiceRestClient
import com.microsoft.projectoxford.face.contract.Face
import com.noodoe.robert.azuresample.configs.AzureParams
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit


/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [FaceAPIFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [FaceAPIFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class FaceAPIFragment : Fragment() {

    private val PICK_IMAGE = 1
    private var detectionProgressDialog: ProgressDialog? = null

    // TODO: Rename and change types of parameters
    private var mParam1: String? = null
    private var mParam2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            mParam1 = arguments.getString(ARG_PARAM1)
            mParam2 = arguments.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater!!.inflate(R.layout.fragment_face_api, container, false)
    }

    private lateinit var textView: TextView
    private lateinit var button: Button
    private lateinit var imageView: ImageView
    private lateinit var faceServiceClient: FaceServiceClient

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        detectionProgressDialog = ProgressDialog(context)

        button = view?.findViewById<Button>(R.id.button1) as Button
        imageView = view?.findViewById<ImageView>(R.id.imageView1)
        textView = view?.findViewById<TextView>(R.id.textView)

        RxView.clicks(button!!)
                .throttleFirst(2, TimeUnit.SECONDS)
                .subscribe {
                    val gallIntent = Intent(Intent.ACTION_GET_CONTENT)
                    gallIntent.type = "image/*"
                    startActivityForResult(Intent.createChooser(gallIntent, "Select Picture"), PICK_IMAGE)
                }

        faceServiceClient = FaceServiceRestClient(AzureParams.FACE_API_KEY_1)
    }

    private val TAG: String? = FaceAPIFragment.javaClass.simpleName

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode === PICK_IMAGE && resultCode === RESULT_OK && data != null && data.data != null) {
            val uri = data.data
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri)
                imageView.setImageBitmap(bitmap)

                detectAndFrame(bitmap)
            } catch (e: IOException) {
                Log.e(TAG, e.message)
            }
        }
    }

    private fun detectAndFrame(imageBitmap: Bitmap) {
        val outputStream = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val detectTask = object : AsyncTask<InputStream, String, Array<Face>?>() {
            var responseMessage = ""
            override fun doInBackground(vararg params: InputStream): Array<Face>? {
                try {
                    responseMessage = "Detecting..."
                    textView.text = responseMessage
                    publishProgress(responseMessage)
                    val result = faceServiceClient.detect(
                            params[0],
                            true, // returnFaceId
                            false, null// returnFaceAttributes: a string like "age, gender"
                    )// returnFaceLandmarks
                    if (result == null) {
                        responseMessage = "Detection Finished. Nothing detected"
                        publishProgress(responseMessage)
                        Log.d(TAG, responseMessage)
                        textView.text = responseMessage
                        return null
                    }
                    responseMessage = String.format("Detection Finished. %d face(s) detected",
                            result!!.size)
                    publishProgress(responseMessage)
                    Log.d(TAG, responseMessage)
                    textView.text = responseMessage
                    return result
                } catch (e: Exception) {
                    responseMessage = "Detection failed"
                    publishProgress(responseMessage)
                    Log.e(TAG, responseMessage)
                    textView.text = responseMessage
                    return null
                }
            }

            override fun onPreExecute() {
                detectionProgressDialog?.show()
            }

            override fun onProgressUpdate(vararg progress: String) {
                detectionProgressDialog?.setMessage(progress[0])
            }

            override fun onPostExecute(result: Array<Face>?) {
                detectionProgressDialog?.dismiss()
                if (result == null) return
                imageView.setImageBitmap(drawFaceRectanglesOnBitmap(imageBitmap, result))
                imageBitmap.recycle()
            }
        }
        detectTask.execute(inputStream)
    }


    private fun drawFaceRectanglesOnBitmap(originalBitmap: Bitmap, faces: Array<Face>): Bitmap? {
        val bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = Color.RED
        val stokeWidth = 5
        paint.strokeWidth = stokeWidth.toFloat()
        faces?.forEach { face ->
            val faceRectangle = face.faceRectangle
            canvas.drawRect(
                    faceRectangle.left.toFloat(),
                    faceRectangle.top.toFloat(),
                    (faceRectangle.left + faceRectangle.width).toFloat(),
                    (faceRectangle.top + faceRectangle.height).toFloat(),
                    paint)
        }
        return bitmap
    }

    companion object {
        // TODO: Rename parameter arguments, choose names that match
        // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
        private val ARG_PARAM1 = "param1"
        private val ARG_PARAM2 = "param2"

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.

         * @param param1 Parameter 1.
         * *
         * @param param2 Parameter 2.
         * *
         * @return A new instance of fragment FaceAPIFragment.
         */
        // TODO: Rename and change types and number of parameters
        fun newInstance(param1: String, param2: String): FaceAPIFragment {
            val fragment = FaceAPIFragment()
            val args = Bundle()
            args.putString(ARG_PARAM1, param1)
            args.putString(ARG_PARAM2, param2)
            fragment.arguments = args
            return fragment
        }

        fun newInstance(): FaceAPIFragment {
            val fragment = FaceAPIFragment()
            val args = Bundle()
            args.putString(ARG_PARAM1, "")
            args.putString(ARG_PARAM2, "")
            fragment.arguments = args
            return fragment
        }
    }
}// Required empty public constructor
