package darkness.com.cloudvisionapi.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.google.gson.Gson;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.LanguageCodes;
import com.microsoft.projectoxford.vision.contract.Line;
import com.microsoft.projectoxford.vision.contract.OCR;
import com.microsoft.projectoxford.vision.contract.Region;
import com.microsoft.projectoxford.vision.contract.Word;
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

public class AnalyzeTextActivity extends AppCompatActivity {
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
        setContentView(R.layout.activity_recognize);
        ButterKnife.bind(this);

        if(mVisionServiceClient==null)
            mVisionServiceClient = new VisionServiceRestClient(Constant.API_KEY,Constant.VISION_ENDPOINT);
    }

    @OnClick(R.id.btnSelectImg)
    public void selectImage(View view) {
        etResultData.setText("");
        Intent intent = new Intent(AnalyzeTextActivity.this, SelectImageActivity.class);
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
            new doRequest(AnalyzeTextActivity.this).execute();
        } catch (Exception e)
        {
            etResultData.setText("Error encountered. Exception is: " + e.toString());
        }
    }

    private String process() throws VisionServiceException, IOException {
        Gson gson = new Gson();

        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        OCR ocr;
        ocr = mVisionServiceClient.recognizeText(inputStream, LanguageCodes.AutoDetect, true);

        String result = gson.toJson(ocr);
        Log.d("result", result);

        return result;
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

            if (e != null) {
                etResultData.setText("Error: " + e.getMessage());
                this.e = null;
            } else {
                Gson gson = new Gson();
                OCR r = gson.fromJson(data, OCR.class);

                String result = "";
                etResultData.setText("");
                for (Region reg : r.regions) {
                    for (Line line : reg.lines) {
                        for (Word word : line.words) {
                            result += word.text + " ";
                        }
                        result += "\n";
                    }
                    result += "\n\n";
                }

                etResultData.append(result);
                etResultData.append("\n\n--- Raw Data ---\n");
                etResultData.append(data);
            }
            btnSelectImg.setEnabled(true);
            btnSendRequest.setEnabled(false);
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }




}
