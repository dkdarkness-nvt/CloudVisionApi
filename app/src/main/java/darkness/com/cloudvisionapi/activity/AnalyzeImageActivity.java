package darkness.com.cloudvisionapi.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.google.gson.Gson;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalysisResult;
import com.microsoft.projectoxford.vision.contract.Category;
import com.microsoft.projectoxford.vision.contract.Face;
import com.microsoft.projectoxford.vision.contract.FaceRectangle;
import com.microsoft.projectoxford.vision.rest.VisionServiceException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import darkness.com.cloudvisionapi.R;
import darkness.com.cloudvisionapi.constant.Constant;
import darkness.com.cloudvisionapi.utils.ImageHelperUtil;

public class AnalyzeImageActivity extends AppCompatActivity {
    private Uri mImageUri;
    private Bitmap mBitmap;
    private VisionServiceClient mVisionServiceClient;

    @BindView(R.id.etResultData)
    EditText etResultData;
    @BindView(R.id.ivSelectedImage)
    ImageView ivSelectedImage;
    @BindView(R.id.btnSelectImg)
    Button btnSelectImg;
    @BindView(R.id.btnSendRequest)
    Button btnSendRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyze);
        ButterKnife.bind(this);
        if (mVisionServiceClient == null) {
            mVisionServiceClient = new VisionServiceRestClient(Constant.API_KEY,Constant.VISION_ENDPOINT);
        }
    }

    @OnClick(R.id.btnSelectImg)
    public void selectImage(View view) {
        etResultData.setText("");
        Intent intent = new Intent(AnalyzeImageActivity.this, SelectImageActivity.class);
        startActivityForResult(intent, Constant.REQUEST_SELECT_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Constant.REQUEST_SELECT_IMAGE:
                if (resultCode == RESULT_OK) {
                    mImageUri = data.getData();
                    mBitmap = ImageHelperUtil
                            .loadSizeLimitedBitmapFromUri(mImageUri, getContentResolver());

                    if (mBitmap != null) {
                        ivSelectedImage.setImageBitmap(mBitmap);
                        Log.d("AnalyzeImageActivity", "Image: " + mImageUri + " resized to " + mBitmap.getWidth()
                                + "x" + mBitmap.getHeight());

                        btnSendRequest.setEnabled(true);
                    }
                }
                break;
            default:
                break;
        }
    }

    @OnClick(R.id.btnSendRequest)
    public void sendRequest(View view) {
        doRecognize();
    }

    public void doRecognize() {
        btnSelectImg.setEnabled(false);

        try {
            new doRequest(AnalyzeImageActivity.this).execute();
        } catch (Exception e) {
            etResultData.setText("Error encountered. Exception is: " + e.toString());
        }
    }

    private String process() throws VisionServiceException, IOException {
        Gson gson = new Gson();
        String[] features = {"ImageType", "Color", "Faces", "Adult", "Categories"};
        String[] details = {};

        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        AnalysisResult v = mVisionServiceClient.analyzeImage(inputStream, features, details);

        String result = gson.toJson(v);
        Log.d("result", result);

        return result;
    }

    private Bitmap drawFace(Bitmap mBitmap, List<Face> faces){
        Bitmap bitmap = mBitmap.copy(Bitmap.Config.ARGB_8888,true);
        Canvas mCanvas = new Canvas(bitmap);
        Paint mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(android.graphics.Color.GREEN);
        int strokeWidth = 5;
        mPaint.setStrokeWidth(strokeWidth);
        if(faces!=null){
            for (Face face: faces){
                FaceRectangle mFaceRectangle = face.faceRectangle;
                mCanvas.drawRect(mFaceRectangle.left,
                        mFaceRectangle.top,
                        mFaceRectangle.left+mFaceRectangle.width,
                        mFaceRectangle.top+mFaceRectangle.height,
                        mPaint);
            }
        }

        return bitmap;
    }

    private class doRequest extends AsyncTask<String, String, String> {
        private Exception e = null;
        private Context context;
        private ProgressDialog dialog;

        public doRequest(Context mContext) {
            this.context = mContext;
        }

        @Override
        protected void onPreExecute() {
            dialog = new ProgressDialog(context);
            dialog.setMessage("Please wait...");
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.show();
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... args) {
            try {
                return process();
            } catch (Exception e) {
                this.e = e;
            }

            return null;
        }

        @Override
        protected void onPostExecute(String data) {
            super.onPostExecute(data);

            Log.d("DATA", data);

            etResultData.setText("");
            if (e != null) {
                etResultData.setText("Error: " + e.getMessage());
                this.e = null;
            } else {
                Gson gson = new Gson();
                AnalysisResult result = gson.fromJson(data, AnalysisResult.class);

                etResultData.append("Image format: " + result.metadata.format + "\n");
                etResultData.append("Image width: " + result.metadata.width + ", height:" + result.metadata.height + "\n");
                etResultData.append("Clip Art Type: " + result.imageType.clipArtType + "\n");
                etResultData.append("Line Drawing Type: " + result.imageType.lineDrawingType + "\n");
                etResultData.append("Is Adult Content:" + result.adult.isAdultContent + "\n");
                etResultData.append("Adult score:" + result.adult.adultScore + "\n");
                etResultData.append("Is Racy Content:" + result.adult.isRacyContent + "\n");
                etResultData.append("Racy score:" + result.adult.racyScore + "\n\n");

                for (Category category : result.categories) {
                    etResultData.append("Category: " + category.name + ", score: " + category.score + "\n");
                }
                etResultData.append("\n");

                etResultData.append("\nDominant Color Foreground :" + result.color.dominantColorForeground + "\n");
                etResultData.append("Dominant Color Background :" + result.color.dominantColorBackground + "\n");

                etResultData.append("\n--- Raw Data ---\n\n");
                etResultData.append(data);
                etResultData.setSelection(0);

                ivSelectedImage.setImageBitmap(drawFace(mBitmap,result.faces));
            }

            btnSelectImg.setEnabled(true);
            btnSendRequest.setEnabled(false);
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }

}
