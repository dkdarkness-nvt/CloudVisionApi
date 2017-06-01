package darkness.com.cloudvisionapi.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalysisInDomainResult;
import com.microsoft.projectoxford.vision.rest.VisionServiceException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import darkness.com.cloudvisionapi.R;
import darkness.com.cloudvisionapi.constant.Constant;
import darkness.com.cloudvisionapi.utils.ImageHelperUtil;

public class AnalyzeCelebritiesActivity extends AppCompatActivity {
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
        setContentView(R.layout.activity_analyze_in_domain);
        ButterKnife.bind(this);

        if (mVisionServiceClient == null)
            mVisionServiceClient = new VisionServiceRestClient(Constant.API_KEY,Constant.VISION_ENDPOINT);
    }

    @OnClick(R.id.btnSelectImg)
    public void selectImage(View view) {
        etResultData.setText("");
        Intent intent = new Intent(AnalyzeCelebritiesActivity.this, SelectImageActivity.class);
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
            new doRequest(AnalyzeCelebritiesActivity.this).execute();
        } catch (Exception e) {
            etResultData.setText("Error encountered. Exception is: " + e.toString());
        }
    }

    private String process() throws VisionServiceException, IOException {
        Gson gson = new Gson();

        String model = "celebrities";

        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        AnalysisInDomainResult v = mVisionServiceClient.analyzeImageInDomain(inputStream, model);
        String result = gson.toJson(v);
        Log.d("result", result);

        return result;
    }

    private class doRequest extends AsyncTask<String, String, String> {
        // Store error message
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
                this.e = e;    // Store error
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
                AnalysisInDomainResult result = gson.fromJson(data, AnalysisInDomainResult.class);

                JsonArray detectedCelebs = result.result.get("celebrities").getAsJsonArray();
                etResultData.append("Celebrities detected: " + detectedCelebs.size() + "\n");

                for (JsonElement celebElement : detectedCelebs) {
                    JsonObject celeb = celebElement.getAsJsonObject();
                    etResultData.append("Name: " + celeb.get("name").getAsString()
                            + ", score: " + celeb.get("confidence").getAsString() + "\n");
                }

                etResultData.append("\n--- Raw Data ---\n\n");
                etResultData.append(data);
                etResultData.setSelection(0);

            }
            btnSelectImg.setEnabled(true);
            btnSendRequest.setEnabled(false);
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }
}
