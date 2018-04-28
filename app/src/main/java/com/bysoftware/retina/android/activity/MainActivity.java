package com.bysoftware.retina.android.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bysoftware.retina.android.BuildConfig;
import com.bysoftware.retina.android.R;
import com.bysoftware.retina.android.utility.DoubleClickListener;
import com.crashlytics.android.Crashlytics;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Size;

import io.fabric.sdk.android.Fabric;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    private static final String API_KEY = BuildConfig.RETINA_API_KEY;

    private static final String TAG = "MainActivity";
    private static final int REQ_CODE_SPEECH_INPUT = 100;
    private static final int RECORD_REQUEST_CODE = 101;
    private static final int CAMERA_REQUEST_CODE = 102;

    private String response;

    private Size mCaptureNativeSize;
    private Boolean mCapturingPicture;
    private long mCaptureTime;
    private int maxVolume;

    private Feature feature;
    private Bitmap bitmap;

    private String api = "LABEL_DETECTION";

    private String speechText, stringTempEN;
    private String[] words;
    private Boolean checkObject = false;

    private Unbinder butterKnifeUnbinder;

    private TextToSpeech textToSpeech;
    private TextToSpeech openingMessage;

    SharedPreferences sharedPreferences = null;
    SharedPreferences.Editor editor;

    AudioManager audioManager;

    @BindView(R.id.text_result)
    TextView textViewResult;

    @BindView(R.id.button_record)
    Button buttonRecord;

    @BindView(R.id.text_translate)
    TextView textViewTranslate;

    @BindView(R.id.imageProgress)
    ProgressBar imageUploadProgress;

    @BindView(R.id.imageView)
    ImageView imageView;

    @BindView(R.id.visionAPIData)
    TextView visionAPIData;

    @BindView(R.id.camera)
    CameraView camera;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar().hide();
        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_main);
        butterKnifeUnbinder = ButterKnife.bind(this);

        sharedPreferences = getSharedPreferences("com.bysoftware.retina.android", MODE_PRIVATE);

        //Set max volume
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0);

        if (checkPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            buttonRecord.setVisibility(View.VISIBLE);
        } else {
            buttonRecord.setVisibility(View.INVISIBLE);
            makeRequest(Manifest.permission.CAMERA);
        }

        feature = new Feature();
        feature.setType(api);
        feature.setMaxResults(10);

        camera.addCameraListener(new CameraListener() {
            public void onPictureTaken(byte[] jpeg) {
                onPicture(jpeg);
            }
        });

        buttonRecord.setOnClickListener(new DoubleClickListener() {
            @Override
            public void onSingleClick(View v) {
                camera.captureSnapshot();
                Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                vibe.vibrate(100);
            }

            @Override
            public void onDoubleClick(View v) {
                promptSpeechInput();
                Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                vibe.vibrate(500);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        camera.stop();
    }

    @Override
    protected void onDestroy() {
        camera.destroy();
        butterKnifeUnbinder.unbind();
        super.onDestroy();
    }

    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Log.e("promptSpeechInput: ", getString(R.string.speech_not_supported));
        }
    }

    //Speech
    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    response = result.get(0).toLowerCase();
                    textViewResult.setText(response);

                    Log.e("onActivityResult full: ", response);

                    if (response != null) {
                        words = response.split(" ");
                        Log.e("onActivityResult trim: ", words[0]);

                        if (words[0].equals("saat")) {
                            stringTempEN = "watch";
                        } else {
                            // Translate text
                            new AsyncTask<Void, Void, Void>() {
                                @Override
                                protected Void doInBackground(Void... params) {
                                    TranslateOptions options = TranslateOptions.newBuilder()
                                            .setApiKey(API_KEY)
                                            .build();
                                    Translate translate = options.getService();
                                    final Translation translation =
                                            translate.translate(words[0],
                                                    Translate.TranslateOption.targetLanguage("en"));

                                    stringTempEN = translation.getTranslatedText();
                                    return null;
                                }
                            }.execute();
                        }
                        checkObject = true;
                        speechText = "Aranan nesne: " + words[0] + ", aramak için ekrana bir kez dokununuz.";

                        //Response To Speech
                        final Handler textViewHandler = new Handler();
                        textViewHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                                    @Override
                                    public void onInit(int status) {
                                        if (status != TextToSpeech.ERROR) {
                                            Locale locale = new Locale("tr", "TR");
                                            textToSpeech.setLanguage(locale);

                                            textToSpeech.speak(speechText, TextToSpeech.QUEUE_FLUSH, null);
                                        }
                                    }
                                });
                            }
                        });
                    }
                }
                break;
            }
        }
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            bitmap = (Bitmap) data.getExtras().get("data");
            imageView.setImageBitmap(bitmap);
            callCloudVision(bitmap, feature);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    //Vision
    @Override
    protected void onResume() {
        super.onResume();
        if (sharedPreferences.getBoolean("firstRun", true)) {
            //Opening voice response
            final Handler textViewHandler = new Handler();
            textViewHandler.post(new Runnable() {
                @Override
                public void run() {
                    openingMessage = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int status) {
                            if (status != TextToSpeech.ERROR) {
                                Locale locale = new Locale("tr", "TR");
                                openingMessage.setLanguage(locale);
                                String toSpeak = "Kamerayı açmak için bir kere, mikrofonu açmak için iki kere ekrana dokununuz";
                                openingMessage.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                            }
                        }
                    });
                }
            });
            editor = sharedPreferences.edit();
            editor.putBoolean("firstRun", false);
            editor.commit();
        }
        camera.start();
    }

    private int checkPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission);
    }

    private void makeRequest(String permission) {
        ActivityCompat.requestPermissions(this, new String[]{permission}, RECORD_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == RECORD_REQUEST_CODE) {
            if (grantResults.length == 0 && grantResults[0] == PackageManager.PERMISSION_DENIED
                    && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                finish();
            } else {
                buttonRecord.setVisibility(View.VISIBLE);
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void callCloudVision(final Bitmap bitmap, final Feature feature) {
        imageUploadProgress.setVisibility(View.VISIBLE);
        final List<Feature> featureList = new ArrayList<>();
        featureList.add(feature);

        final List<AnnotateImageRequest> annotateImageRequests = new ArrayList<>();

        AnnotateImageRequest annotateImageReq = new AnnotateImageRequest();
        annotateImageReq.setFeatures(featureList);
        annotateImageReq.setImage(getImageEncodeImage(bitmap));
        annotateImageRequests.add(annotateImageReq);


        new AsyncTask<Object, Void, String>() {
            @Override
            protected String doInBackground(Object... params) {
                try {
                    HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
                    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                    VisionRequestInitializer requestInitializer = new VisionRequestInitializer(API_KEY);

                    Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
                    builder.setVisionRequestInitializer(requestInitializer);

                    Vision vision = builder.build();

                    BatchAnnotateImagesRequest batchAnnotateImagesRequest = new BatchAnnotateImagesRequest();
                    batchAnnotateImagesRequest.setRequests(annotateImageRequests);

                    Vision.Images.Annotate annotateRequest = vision.images().annotate(batchAnnotateImagesRequest);
                    annotateRequest.setDisableGZipContent(true);
                    BatchAnnotateImagesResponse response = annotateRequest.execute();
                    return convertResponseToString(response);
                } catch (GoogleJsonResponseException e) {
                    Log.d(TAG, "failed to make API request because " + e.getContent());
                } catch (IOException e) {
                    Log.d(TAG, "failed to make API request because of other IOException " + e.getMessage());
                }
                return "Cloud Vision API request failed. Check logs for details.";
            }

            protected void onPostExecute(String result) {
                visionAPIData.setText(result);
                final String[] speechTxtArray = result.split("0");
                final String speechTextEnglish = speechTxtArray[0];
                String stringTempCloud;
                stringTempCloud = speechTextEnglish.replaceAll("\\s", "");
                if (checkObject) {

                    buttonRecord.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            checkObject = false;
                            Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                            vibe.vibrate(250);
                            speechText = words[0] + " aramsı sonlandırıldı.";
                            return true;
                        }
                    });

                    Log.e("stringTempCloud: ", stringTempCloud);
                    Log.e("stringTempEN: ", stringTempEN);

                    if(stringTempEN.equals(stringTempCloud)){
                        speechText = words[0] + " karşınızda.";
                        checkObject = false;
                    }else {
                        speechText = words[0] + " bulunamadı tekrar deneyiniz.";
                    }
                } else {
                    if (stringTempCloud.equals("watch")) {
                        speechText = "sâât";
                    } else {
                        // Translate text
                        new AsyncTask<Void, Void, Void>() {
                            @Override
                            protected Void doInBackground(Void... params) {
                                TranslateOptions options = TranslateOptions.newBuilder()
                                        .setApiKey(API_KEY)
                                        .build();
                                Translate translate = options.getService();
                                final Translation translation =
                                        translate.translate(speechTextEnglish,
                                                Translate.TranslateOption.targetLanguage("tr"));

                                speechText = translation.getTranslatedText();
                                return null;
                            }
                        }.execute();
                    }
                }

                //Response To Speech
                final Handler textViewHandler = new Handler();
                textViewHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                            @Override
                            public void onInit(int status) {
                                if (status != TextToSpeech.ERROR) {
                                    Locale locale = new Locale("tr", "TR");
                                    textToSpeech.setLanguage(locale);

                                    textToSpeech.speak(speechText, TextToSpeech.QUEUE_FLUSH, null);
                                }
                            }
                        });
                    }
                });

                imageUploadProgress.setVisibility(View.INVISIBLE);
            }
        }.execute();
    }

    @NonNull
    private Image getImageEncodeImage(Bitmap bitmap) {
        Image base64EncodedImage = new Image();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        base64EncodedImage.encodeContent(imageBytes);
        return base64EncodedImage;
    }

    private String convertResponseToString(BatchAnnotateImagesResponse response) {

        AnnotateImageResponse imageResponses = response.getResponses().get(0);

        List<EntityAnnotation> entityAnnotations;

        String message = "";

        entityAnnotations = imageResponses.getLabelAnnotations();
        message = formatAnnotation(entityAnnotations);

        return message;
    }

    private String formatAnnotation(List<EntityAnnotation> entityAnnotation) {
        String message = "";

        if (entityAnnotation != null) {
            for (EntityAnnotation entity : entityAnnotation) {
                message = message + "    " + entity.getDescription() + " " + entity.getScore();
                message += "\n";
            }
        } else {
            message = "Nothing Found";
        }
        return message;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        api = (String) adapterView.getItemAtPosition(i);
        feature.setType(api);
        if (bitmap != null)
            callCloudVision(bitmap, feature);
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    private void onPicture(byte[] jpeg) {
        mCapturingPicture = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        long callbackTime = System.currentTimeMillis();

        // This can happen if picture was taken with a gesture.
        if (mCaptureTime == 0) mCaptureTime = callbackTime - 300;
        if (mCaptureNativeSize == null) mCaptureNativeSize = camera.getPictureSize();
        imageView.setImageBitmap(bitmap);

        callCloudVision(bitmap, feature);

        mCaptureTime = 0;
        mCaptureNativeSize = null;
    }
}