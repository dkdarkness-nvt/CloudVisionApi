package darkness.com.cloudvisionapi.activity;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
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
import com.microsoft.projectoxford.vision.contract.HandwritingRecognitionOperation;
import com.microsoft.projectoxford.vision.contract.HandwritingRecognitionOperationResult;
import com.microsoft.projectoxford.vision.contract.HandwritingTextLine;
import com.microsoft.projectoxford.vision.contract.HandwritingTextWord;
import com.microsoft.projectoxford.vision.rest.VisionServiceException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import darkness.com.cloudvisionapi.R;
import darkness.com.cloudvisionapi.constant.Constant;
import darkness.com.cloudvisionapi.utils.ImageHelperUtil;

public class HandwritingRecognizeActivity extends AppCompatActivity {

    private Uri mImageUri;
    private Bitmap mBitmap;
    private VisionServiceClient mVisionServiceClient;

    //max retry times to get operation result
    private int retryCountThreshold = 30;

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
        setContentView(R.layout.activity_recognize);
        ButterKnife.bind(this);

        if(mVisionServiceClient==null)
            mVisionServiceClient = new VisionServiceRestClient(Constant.API_KEY,Constant.VISION_ENDPOINT);
    }

    @OnClick(R.id.btnSelectImg)
    public void selectImage(View view) {
        etResultData.setText("");
        Intent intent = new Intent(HandwritingRecognizeActivity.this, SelectImageActivity.class);
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
    public void sendRequest(View view){
        doRecognize();
    }

    public void doRecognize() {
        btnSelectImg.setEnabled(false);

        try {
            new HandwritingRecognizeActivity.doRequest(HandwritingRecognizeActivity.this).execute();
        } catch (Exception e)
        {
            etResultData.setText("Error encountered. Exception is: " + e.toString());
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private String process() throws VisionServiceException, IOException,InterruptedException {
        Gson gson = new Gson();

        // Put the image into an input stream for detection.
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray())) {
                //post image and got operation from API
                HandwritingRecognitionOperation operation = this.mVisionServiceClient.createHandwritingRecognitionOperationAsync(inputStream);

                HandwritingRecognitionOperationResult operationResult;
                //try to get recognition result until it finished.

                int retryCount = 0;
                do {
                    if (retryCount > retryCountThreshold) {
                        throw new InterruptedException("Can't get result after retry in time.");
                    }
                    Thread.sleep(1000);
                    operationResult = this.mVisionServiceClient.getHandwritingRecognitionOperationResultAsync(operation.Url());
                }
                while (operationResult.getStatus().equals("NotStarted") || operationResult.getStatus().equals("Running"));

                String result = gson.toJson(operationResult);
                Log.d("result", result);
                return result;

            } catch (Exception ex) {
                throw ex;
            }
        } catch (Exception ex) {
            throw ex;
        }
    }

    private class doRequest extends AsyncTask<String, String, String> {
        private Exception e = null;
        private Context context;
        private ProgressDialog dialog;
        private WeakReference<HandwritingRecognizeActivity> recognitionActivity;

        public doRequest(HandwritingRecognizeActivity activity) {
            this.context = activity;
            recognitionActivity = new WeakReference<HandwritingRecognizeActivity>(activity);
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
                if (recognitionActivity.get() != null) {
                    return recognitionActivity.get().process();
                }
            } catch (Exception e) {
                this.e = e;    // Store error
            }

            return null;
        }

        @Override
        protected void onPostExecute(String data) {
            super.onPostExecute(data);

            Log.d("DATA", data);

            if (recognitionActivity.get() == null) {
                return;
            }
            // Display based on error existence
            if (e != null) {
                recognitionActivity.get().etResultData.setText("Error: " + e.getMessage());
                this.e = null;
            } else {
                Gson gson = new Gson();
                HandwritingRecognitionOperationResult r = gson.fromJson(data, HandwritingRecognitionOperationResult.class);

                StringBuilder resultBuilder = new StringBuilder();
                //if recognition result status is failed. display failed
                if (r.getStatus().equals("Failed")) {
                    resultBuilder.append("Error: Recognition Failed");
                } else {
                    for (HandwritingTextLine line : r.getRecognitionResult().getLines()) {
                        for (HandwritingTextWord word : line.getWords()) {
                            resultBuilder.append(word.getText() + " ");
                        }
                        resultBuilder.append("\n");
                    }
                    resultBuilder.append("\n");
                }

                recognitionActivity.get().etResultData.setText(resultBuilder);
            }

            etResultData.append(data);

            btnSelectImg.setEnabled(true);
            btnSendRequest.setEnabled(false);
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }

}
