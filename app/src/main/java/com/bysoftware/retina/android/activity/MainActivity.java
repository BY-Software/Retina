package com.bysoftware.retina.android.activity;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.bysoftware.retina.android.BuildConfig;
import com.bysoftware.retina.android.R;
import com.bysoftware.retina.android.utility.DoubleClickListener;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;

import java.util.ArrayList;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import jerry.speechapi.auth.Authentication;
import jerry.speechapi.service.GoogleSpeechService;
import jerry.speechapi.service.GoogleSpeechServiceFactory;

public class MainActivity extends AppCompatActivity {
    private static final String API_KEY = BuildConfig.RETINA_API_KEY;

    private String response;

    private final int REQ_CODE_SPEECH_INPUT = 100;

    @BindView(R.id.text_result)
    TextView textViewResult;

    @BindView(R.id.button_record)
    Button buttonRecord;

    @BindView(R.id.text_translate)
    TextView textViewTranslate;

    private String speachText;

    private Unbinder butterKnifeUnbinder;

    private GoogleSpeechService speechService;

    private Authentication authentication;

    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        butterKnifeUnbinder = ButterKnife.bind(this);

        speechService = GoogleSpeechServiceFactory.newService();
        authentication = new Authentication.Builder()
                .setApiKey(API_KEY)
                .build();

        buttonRecord.setOnClickListener(new DoubleClickListener() {
            @Override
            public void onSingleClick(View v) {
                //TODO: Add Camera
                Toast.makeText(getApplicationContext(), "SINGLE CLICK", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDoubleClick(View v) {
                promptSpeechInput();
                Toast.makeText(getApplicationContext(), "DOUBLE CLICK", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        butterKnifeUnbinder.unbind();
        super.onDestroy();
    }

    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(), getString(R.string.speech_not_supported), Toast.LENGTH_SHORT).show();
        }
    }

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

                    if (response != null) {
                        // Translate text
                        new AsyncTask<Void, Void, Void>() {
                            @Override
                            protected Void doInBackground(Void... params) {
                                TranslateOptions options = TranslateOptions.newBuilder()
                                        .setApiKey(API_KEY)
                                        .build();
                                Translate translate = options.getService();
                                final Translation translation =
                                        translate.translate(response,
                                                Translate.TranslateOption.targetLanguage("en"));

                                speachText = translation.getTranslatedText();
                                return null;
                            }
                        }.execute();

                        //Response To Speech
                        final Handler textViewHandler = new Handler();
                        textViewHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                                    @Override
                                    public void onInit(int status) {
                                        if (status != TextToSpeech.ERROR) {
                                            Locale locale = new Locale("en", "EN");
                                            textToSpeech.setLanguage(locale);

                                            textToSpeech.speak(speachText, TextToSpeech.QUEUE_FLUSH, null);

                                            textViewTranslate.setText(speachText);
                                            Toast.makeText(getApplicationContext(), speachText, Toast.LENGTH_SHORT).show();
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
}

