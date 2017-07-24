package com.noodoe.robert.azuresample

import android.app.Activity.RESULT_OK
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
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
import com.jakewharton.rxbinding2.view.RxView
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

    private var mListener: OnFragmentInteractionListener? = null

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

    private lateinit var button: Button
    private lateinit var imageView: ImageView
    private lateinit var faceServiceClient: FaceServiceClient

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        detectionProgressDialog = ProgressDialog(context)

        button = view?.findViewById<Button>(R.id.button1) as Button
        imageView = view?.findViewById<ImageView>(R.id.imageView1)

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
        val detectTask = object : AsyncTask<InputStream, String, Array<Face>>() {
            override fun doInBackground(vararg params: InputStream): Array<Face>? {
                try {
                    publishProgress("Detecting...")
                    val result = faceServiceClient.detect(
                            params[0],
                            true, // returnFaceId
                            false, null// returnFaceAttributes: a string like "age, gender"
                    )// returnFaceLandmarks
                    if (result == null) {
                        publishProgress("Detection Finished. Nothing detected")
                        return null
                    }
                    publishProgress(
                            String.format("Detection Finished. %d face(s) detected",
                                    result!!.size))
                    return result
                } catch (e: Exception) {
                    publishProgress("Detection failed")
                    return null
                }

            }

            override fun onPreExecute() {
                detectionProgressDialog?.show()
            }

            override fun onProgressUpdate(vararg progress: String) {
                detectionProgressDialog?.setMessage(progress[0])
            }

            override fun onPostExecute(result: Array<Face>) {
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
        val stokeWidth = 2
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

    // TODO: Rename method, update argument and hook method into UI event
    fun onButtonPressed(uri: Uri) {
        if (mListener != null) {
            mListener!!.onFragmentInteraction(uri)
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            mListener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     *
     *
     * See the Android Training lesson [Communicating with Other Fragments](http://developer.android.com/training/basics/fragments/communicating.html) for more information.
     */
    interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        fun onFragmentInteraction(uri: Uri)
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
    }
}// Required empty public constructor
