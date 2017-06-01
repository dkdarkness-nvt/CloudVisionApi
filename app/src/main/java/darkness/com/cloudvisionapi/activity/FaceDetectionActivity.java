package darkness.com.cloudvisionapi.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Accessory;
import com.microsoft.projectoxford.face.contract.Emotion;
import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.contract.FacialHair;
import com.microsoft.projectoxford.face.contract.Hair;
import com.microsoft.projectoxford.face.contract.HeadPose;
import com.microsoft.projectoxford.face.contract.Makeup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;
import darkness.com.cloudvisionapi.R;
import darkness.com.cloudvisionapi.constant.Constant;
import darkness.com.cloudvisionapi.utils.ImageHelperUtil;

import static darkness.com.cloudvisionapi.constant.Constant.REQUEST_SELECT_IMAGE;

public class FaceDetectionActivity extends AppCompatActivity {

    private Uri mImageUri;
    private Bitmap mBitmap;
    ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_detection);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle("Please wait...");

        setDetectButtonEnabledStatus(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable("ImageUri", mImageUri);
    }

    // Recover the saved state when the activity is recreated.
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mImageUri = savedInstanceState.getParcelable("ImageUri");
        if (mImageUri != null) {
            mBitmap = ImageHelperUtil.loadSizeLimitedBitmapFromUri(
                    mImageUri, getContentResolver());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SELECT_IMAGE:
                if (resultCode == RESULT_OK) {
                    mImageUri = data.getData();
                    mBitmap = ImageHelperUtil.loadSizeLimitedBitmapFromUri(
                            mImageUri, getContentResolver());
                    if (mBitmap != null) {
                        ImageView imageView = (ImageView) findViewById(R.id.image);
                        imageView.setImageBitmap(mBitmap);
                    }

                    // Clear the detection result.
                    FaceListAdapter faceListAdapter = new FaceListAdapter(null);
                    ListView listView = (ListView) findViewById(R.id.list_detected_faces);
                    listView.setAdapter(faceListAdapter);

                    // Clear the information panel.
                    setInfo("");

                    // Enable button "detect" as the image is selected and not detected.
                    setDetectButtonEnabledStatus(true);
                }
                break;
            default:
                break;
        }
    }

    public void selectImage(View view) {
        Intent intent = new Intent(this, SelectImageActivity.class);
        startActivityForResult(intent, REQUEST_SELECT_IMAGE);
    }

    public void detect(View view) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        new DetectionTask().execute(inputStream);

        setAllButtonsEnabledStatus(false);
    }

    private void setUiAfterDetection(Face[] result, boolean succeed) {
        mProgressDialog.dismiss();

        setAllButtonsEnabledStatus(true);

        setDetectButtonEnabledStatus(false);

        if (succeed) {
            String detectionResult;
            if (result != null) {
                detectionResult = result.length + " face"
                        + (result.length != 1 ? "s" : "") + " detected";

                ImageView imageView = (ImageView) findViewById(R.id.image);
                imageView.setImageBitmap(ImageHelperUtil.drawFaceRectanglesOnBitmap(
                        mBitmap, result, true));

                FaceListAdapter faceListAdapter = new FaceListAdapter(result);

                ListView listView = (ListView) findViewById(R.id.list_detected_faces);
                listView.setAdapter(faceListAdapter);
            } else {
                detectionResult = "0 face detected";
            }
            setInfo(detectionResult);
        }

        mImageUri = null;
        mBitmap = null;
    }

    private void setAllButtonsEnabledStatus(boolean isEnabled) {
        Button selectImageButton = (Button) findViewById(R.id.select_image);
        selectImageButton.setEnabled(isEnabled);

        Button detectButton = (Button) findViewById(R.id.detect);
        detectButton.setEnabled(isEnabled);
    }

    private void setDetectButtonEnabledStatus(boolean isEnabled) {
        Button detectButton = (Button) findViewById(R.id.detect);
        detectButton.setEnabled(isEnabled);
    }

    private void setInfo(String info) {
        TextView textView = (TextView) findViewById(R.id.info);
        textView.setText(info);
    }

    private class FaceListAdapter extends BaseAdapter {
        // The detected faces.
        List<Face> faces;

        // The thumbnails of detected faces.
        List<Bitmap> faceThumbnails;

        // Initialize with detection result.
        FaceListAdapter(Face[] detectionResult) {
            faces = new ArrayList<>();
            faceThumbnails = new ArrayList<>();

            if (detectionResult != null) {
                faces = Arrays.asList(detectionResult);
                for (Face face : faces) {
                    try {
                        // Crop face thumbnail with five main landmarks drawn from original image.
                        faceThumbnails.add(ImageHelperUtil.generateFaceThumbnail(
                                mBitmap, face.faceRectangle));
                    } catch (IOException e) {
                        // Show the exception when generating face thumbnail fails.
                        setInfo(e.getMessage());
                    }
                }
            }
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public int getCount() {
            return faces.size();
        }

        @Override
        public Object getItem(int position) {
            return faces.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater layoutInflater =
                        (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = layoutInflater.inflate(R.layout.item_face_with_description, parent, false);
            }
            convertView.setId(position);

            ((ImageView) convertView.findViewById(R.id.face_thumbnail)).setImageBitmap(
                    faceThumbnails.get(position));

            // Show the face details.
            DecimalFormat formatter = new DecimalFormat("#0.0");
            String face_description = String.format("Age: %s  Gender: %s\nHair: %s  FacialHair: %s\nMakeup: %s\nGlassesType: %s\nAccessories: %s",
                    faces.get(position).faceAttributes.age,
                    faces.get(position).faceAttributes.gender,
                    getHair(faces.get(position).faceAttributes.hair),
                    getFacialHair(faces.get(position).faceAttributes.facialHair),
                    getMakeup((faces.get(position)).faceAttributes.makeup),
                    faces.get(position).faceAttributes.glasses,
                    getAccessories(faces.get(position).faceAttributes.accessories)
            );
            ((TextView) convertView.findViewById(R.id.text_detected_face)).setText(face_description);

            return convertView;
        }

        private String getHair(Hair hair) {
            if (hair.hairColor.length == 0)
            {
                if (hair.invisible)
                    return "Invisible";
                else
                    return "Bald";
            }
            else
            {
                int maxConfidenceIndex = 0;
                double maxConfidence = 0.0;

                for (int i = 0; i < hair.hairColor.length; ++i)
                {
                    if (hair.hairColor[i].confidence > maxConfidence)
                    {
                        maxConfidence = hair.hairColor[i].confidence;
                        maxConfidenceIndex = i;
                    }
                }

                return hair.hairColor[maxConfidenceIndex].color.toString();
            }
        }

        private String getMakeup(Makeup makeup) {
            return  (makeup.eyeMakeup || makeup.lipMakeup) ? "Yes" : "No" ;
        }

        private String getAccessories(Accessory[] accessories) {
            if (accessories.length == 0)
            {
                return "NoAccessories";
            }
            else
            {
                String[] accessoriesList = new String[accessories.length];
                for (int i = 0; i < accessories.length; ++i)
                {
                    accessoriesList[i] = accessories[i].type.toString();
                }

                return TextUtils.join(",", accessoriesList);
            }
        }

        private String getFacialHair(FacialHair facialHair) {
            return (facialHair.moustache + facialHair.beard + facialHair.sideburns > 0) ? "Yes" : "No";
        }

        private String getEmotion(Emotion emotion)
        {
            String emotionType = "";
            double emotionValue = 0.0;
            if (emotion.anger > emotionValue)
            {
                emotionValue = emotion.anger;
                emotionType = "Anger";
            }
            if (emotion.contempt > emotionValue)
            {
                emotionValue = emotion.contempt;
                emotionType = "Contempt";
            }
            if (emotion.disgust > emotionValue)
            {
                emotionValue = emotion.disgust;
                emotionType = "Disgust";
            }
            if (emotion.fear > emotionValue)
            {
                emotionValue = emotion.fear;
                emotionType = "Fear";
            }
            if (emotion.happiness > emotionValue)
            {
                emotionValue = emotion.happiness;
                emotionType = "Happiness";
            }
            if (emotion.neutral > emotionValue)
            {
                emotionValue = emotion.neutral;
                emotionType = "Neutral";
            }
            if (emotion.sadness > emotionValue)
            {
                emotionValue = emotion.sadness;
                emotionType = "Sadness";
            }
            if (emotion.surprise > emotionValue)
            {
                emotionValue = emotion.surprise;
                emotionType = "Surprise";
            }
            return String.format("%s: %f", emotionType, emotionValue);
        }

        private String getHeadPose(HeadPose headPose)
        {
            return String.format("Pitch: %s, Roll: %s, Yaw: %s", headPose.pitch, headPose.roll, headPose.yaw);
        }
    }

    private class DetectionTask extends AsyncTask<InputStream, String, Face[]> {
        private boolean mSucceed = true;

        @Override
        protected Face[] doInBackground(InputStream... params) {
            FaceServiceClient faceServiceClient = new FaceServiceRestClient(Constant.FACE_ENDPOINT,Constant.API_KEY_FACE);
            try {
                publishProgress("Detecting...");

                // Start detection.
                return faceServiceClient.detect(
                        params[0],
                        true,
                        true,
                        new FaceServiceClient.FaceAttributeType[] {
                                FaceServiceClient.FaceAttributeType.Age,
                                FaceServiceClient.FaceAttributeType.Gender,
                                FaceServiceClient.FaceAttributeType.Smile,
                                FaceServiceClient.FaceAttributeType.Glasses,
                                FaceServiceClient.FaceAttributeType.FacialHair,
                                FaceServiceClient.FaceAttributeType.Emotion,
                                FaceServiceClient.FaceAttributeType.HeadPose,
                                FaceServiceClient.FaceAttributeType.Accessories,
                                FaceServiceClient.FaceAttributeType.Blur,
                                FaceServiceClient.FaceAttributeType.Exposure,
                                FaceServiceClient.FaceAttributeType.Hair,
                                FaceServiceClient.FaceAttributeType.Makeup,
                                FaceServiceClient.FaceAttributeType.Noise,
                                FaceServiceClient.FaceAttributeType.Occlusion
                        });
            } catch (Exception e) {
                mSucceed = false;
                publishProgress(e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            mProgressDialog.show();
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            mProgressDialog.setMessage(progress[0]);
            setInfo(progress[0]);
        }

        @Override
        protected void onPostExecute(Face[] result) {
            for (Face face: result) {
                Log.d("DATA", "left: " + face.faceRectangle.left
                        +  ",  top: " + face.faceRectangle.top
                        + ", width: " + face.faceRectangle.width
                        + "  height: " + face.faceRectangle.height + "\n" );
            }

            setUiAfterDetection(result, mSucceed);
        }
    }


}
