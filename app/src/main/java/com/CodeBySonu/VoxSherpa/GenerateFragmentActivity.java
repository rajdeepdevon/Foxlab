package com.CodeBySonu.VoxSherpa;

import android.Manifest;
import android.animation.*;
import android.app.*;
import android.app.Activity;
import android.content.*;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.view.View;
import android.view.View.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.CodeBySonu.VoxSherpa.databinding.*;
import com.google.firebase.FirebaseApp;
import com.k2fsa.sherpa.onnx.*;
import com.tom_roush.pdfbox.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.android.material.snackbar.Snackbar;
import android.media.AudioManager;
import android.media.AudioFocusRequest;
import android.os.Build;
import android.content.Context;
import com.CodeBySonu.VoxSherpa.system.VoxMediaController;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.concurrent.LinkedBlockingQueue;
import com.CodeBySonu.VoxSherpa.system.VoxMediaService;
import android.os.Process;


public class GenerateFragmentActivity extends Fragment {
	private static final String[] SUPERTONIC_VOICE_PROFILES = {
		"F1 - Calm, Clear",
		"F2 - Bright, Professional",
		"F3 - Soft, Expressive",
		"F4 - Energetic, Versatile",
		"F5 - Airy, Warm",
		"M1 - Lively, Upbeat",
		"M2 - Warm, Narrative",
		"M3 - Deep, Authoritative",
		"M4 - Measured, Resonant",
		"M5 - Dynamic, Dramatic"
	};

	
	public final int REQ_CD_FILEPICKER = 101;
	
	private GenerateFragmentBinding binding;
	private boolean isAudioGeneratedForCurrentText = false;
	private String lastGeneratedText = "";
	private byte[] lastGeneratedPcmData = null;
	private android.media.AudioTrack audioTrack;
	private android.animation.ValueAnimator playheadAnimator;
	androidx.appcompat.widget.ListPopupWindow listPopupWindow;
	android.widget.ArrayAdapter<String> voiceAdapter;
	private int lastGeneratedSampleRate = 22050;
	private volatile boolean isCancelled = false;
	private volatile boolean isGenerating = false;
	com.CodeBySonu.VoxSherpa.GenerationParams lastParams = null;
	private volatile int currentGenerationId = 0;
	private String currentUiModelPath = "";
	private android.media.AudioTrack liveStreamTrack = null;
	private String lastSettingsState = "";
	private java.util.ArrayList<com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.RegionItem> kokoroRegions =
			new java.util.ArrayList<>();
	private java.util.ArrayList<com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem> kokoroFilteredVoices =
			new java.util.ArrayList<>();
	private boolean suppressKokoroFilterCallbacks;
	android.media.AudioManager audioManager;
	android.media.AudioManager.OnAudioFocusChangeListener focusListener;
	android.media.AudioFocusRequest focusRequest;
	
	private SharedPreferences sp1;
	private SharedPreferences sp2;
	private SharedPreferences sp3;
	private Intent FilePicker = new Intent(Intent.ACTION_GET_CONTENT);
	private SharedPreferences spHistory;
	
	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater _inflater, @Nullable ViewGroup _container, @Nullable Bundle _savedInstanceState) {
		binding = GenerateFragmentBinding.inflate(_inflater, _container, false);
		initialize(_savedInstanceState, binding.getRoot());
		FirebaseApp.initializeApp(getContext());
		initializeLogic();
		return binding.getRoot();
	}
	
	private void initialize(Bundle _savedInstanceState, View _view) {
		sp1 = getContext().getSharedPreferences("sp1", Activity.MODE_PRIVATE);
		sp2 = getContext().getSharedPreferences("sp2", Activity.MODE_PRIVATE);
		sp3 = getContext().getSharedPreferences("sp3", Activity.MODE_PRIVATE);
		FilePicker.setType("*/*");
		FilePicker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		spHistory = getContext().getSharedPreferences("spHistory", Activity.MODE_PRIVATE);
		binding.supertonicVoiceButton.setOnClickListener(view -> showSupertonicVoiceSheet());
		binding.supertonicSettingsButton.setOnClickListener(view -> showSupertonicQualityDialog());

		binding.history.setOnClickListener(view -> {
			if (getActivity() != null) {
				androidx.viewpager2.widget.ViewPager2 viewPager =
						getActivity().findViewById(R.id.viewpager);
				if (viewPager != null) viewPager.setCurrentItem(2, false);
			}
		});
		binding.dictionary.setOnClickListener(view -> {
			startActivity(new Intent(requireContext(), EpubReaderActivity.class));
		});
		binding.cardAdd.setOnClickListener(view -> {
			Intent documentIntent = new Intent(Intent.ACTION_GET_CONTENT);
			documentIntent.setType("*/*");
			documentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
			try {
				startActivityForResult(Intent.createChooser(documentIntent, "Import text or document"), REQ_CD_FILEPICKER);
			} catch (android.content.ActivityNotFoundException error) {
				com.google.android.material.snackbar.Snackbar.make(binding.getRoot(),
						"No file picker is available.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
			}
		});
		binding.btnVoiceInput.setOnClickListener(view -> {
			Intent speechIntent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
			speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
					android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
			speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak text to synthesize");
			try {
				startActivityForResult(speechIntent, 100);
			} catch (android.content.ActivityNotFoundException error) {
				com.google.android.material.snackbar.Snackbar.make(binding.getRoot(),
						"Voice typing is not available on this device.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
			}
		});
		binding.btnPaste.setOnClickListener(view -> {
			android.content.ClipboardManager clipboard =
					(android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
			if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null
					|| clipboard.getPrimaryClip().getItemCount() == 0) {
				com.google.android.material.snackbar.Snackbar.make(binding.getRoot(),
						"Clipboard is empty.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
				return;
			}
			CharSequence pastedText = clipboard.getPrimaryClip().getItemAt(0).coerceToText(requireContext());
			if (pastedText == null || pastedText.length() == 0) return;
			int start = Math.max(0, binding.etInput.getSelectionStart());
			int end = Math.max(0, binding.etInput.getSelectionEnd());
			if (start > end) {
				int swap = start;
				start = end;
				end = swap;
			}
			android.text.Editable text = binding.etInput.getText();
			text.replace(start, end, pastedText);
			int cursor = Math.min(start + pastedText.length(), text.length());
			binding.etInput.setSelection(cursor);
		});
		binding.btnClearText.setOnClickListener(view -> binding.etInput.setText(""));
		
		binding.btnGenerate.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				String inputText = binding.etInput.getText().toString().trim();
				
				if (inputText.isEmpty()) {
					if (getView() != null) {
						com.google.android.material.snackbar.Snackbar.make(getView(), "Please enter some text first.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
						.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
					}
					return;
				}
				
				String currentOnnxModel = sp1.getString("active_model", "");
				String currentTokens    = sp1.getString("active_tokens", "");
				String currentModelType = sp1.getString("active_model_type", "vits");
				String currentVoicesBin = sp1.getString("active_voices_bin", "");
				
				boolean isPunctOn   = sp3.getBoolean("smart_punct", false);
				boolean isEmotionOn = sp3.getBoolean("emotion_tags", false);
				boolean isMultiSpeakerOn = sp3.getBoolean("multi_speaker_tags", false);
				float currentSpeed  = sp3.getFloat("voice_speed", 1.0f);
				float currentPitch  = sp3.getFloat("voice_pitch", 1.0f);
				float currentSilence = sp3.getFloat("silence_scale", 0.2f);
				int currentVoiceStyle = sp3.getInt("voice_style", 1); 
				int currentKokoroVoiceId = sp1.getInt("active_kokoro_speaker", 31);
				final int currentPiperVoiceId = sp1.getInt("active_piper_speaker", 0); 
				
				// Standard check for missing models or tokens
				if (currentOnnxModel.isEmpty() || currentTokens.isEmpty()) {
					if (getView() != null) {
						com.google.android.material.snackbar.Snackbar.make(getView(), "Please select a Voice Model from Models tab.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
						.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
					}
					return;
				}
				
				boolean isVoiceParamChanged = false;
				if (lastParams == null ||
				!currentOnnxModel.equals(lastParams.onnxModel) ||
				currentKokoroVoiceId != lastParams.kokoroVoiceId ||
				currentPiperVoiceId != sp3.getInt("last_piper_speaker", -1) || 
				currentSpeed != lastParams.speed ||
				currentPitch != lastParams.pitch ||
				isPunctOn != lastParams.punctOn ||
				isEmotionOn != lastParams.emotionOn ||
				isMultiSpeakerOn != sp3.getBoolean("last_multi_speaker_state", false) ||
				currentSilence != sp3.getFloat("last_silence_scale", -1f) ||
				currentVoiceStyle != sp3.getInt("last_voice_style", -1)) { 
					
					isVoiceParamChanged = true;
					sp3.edit()
					.putBoolean("last_multi_speaker_state", isMultiSpeakerOn)
					.putFloat("last_silence_scale", currentSilence)
					.putInt("last_voice_style", currentVoiceStyle) 
					.putInt("last_piper_speaker", currentPiperVoiceId) 
					.apply();
				}
				
				if (isGenerating) {
					if (!isCancelled) {
						new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
						.setTitle("Cancel Generation")
						.setMessage("Are you sure you want to cancel the voice synthesis?")
						.setPositiveButton("Yes, Cancel", (dialog, which) -> {
							_cancelGeneration();
							dialog.dismiss();
						})
						.setNegativeButton("No", (dialog, which) -> dialog.dismiss())
						.show();
					}
					return;
				}
				
				if (isAudioGeneratedForCurrentText && !isVoiceParamChanged) {
					_toggleGeneratePlayback();
				} else {
					final com.CodeBySonu.VoxSherpa.GenerationParams params = new com.CodeBySonu.VoxSherpa.GenerationParams(
					inputText, currentOnnxModel, currentTokens, currentModelType, currentVoicesBin,
					currentKokoroVoiceId, currentSpeed, currentPitch, isPunctOn, isEmotionOn
					);
					final String selectedSupertonicLanguage = com.CodeBySonu.VoxSherpa.SupertonicEngine.normalizeLanguage(
							sp1.getString("active_language", "en"));
					final int selectedSupertonicSteps = Math.max(
							SupertonicEngine.MIN_GENERATION_STEPS,
							Math.min(SupertonicEngine.MAX_GENERATION_STEPS,
									sp3.getInt("supertonic_num_steps", SupertonicEngine.DEFAULT_GENERATION_STEPS)));
					
					_forceResetToIdle();
					isGenerating = true;
					isCancelled = false;
					final int myGenId = currentGenerationId;
					
					binding.btnGenerate.setEnabled(true);
					binding.btnGenerate.setAlpha(1.0f);
					binding.imageview65.setVisibility(android.view.View.GONE);
					binding.progressGenerating.setVisibility(android.view.View.VISIBLE);
					binding.textview69.setTextColor(android.graphics.Color.parseColor("#00E5FF"));
					binding.textview92.setText("Cancel");
					binding.imageview52.setImageResource(R.drawable.ic_close);
					
					final boolean isKokoro = _isKokoroModel(params.modelType);
					final boolean isSupertonic = params.modelType.equals("supertonic");
					// NEW: Extract explicit MMS flag for loading the model safely
					final boolean isMmsModel = params.modelType.equals("mms");
					
					java.util.List<String> sentences = new java.util.ArrayList<>();
					String[] parts = params.text.split("(?<=[.!?\\n\u0964])\\s+");
					for (String part : parts) {
						if (!part.trim().isEmpty()) sentences.add(part.trim());
					}
					final int totalSentences = sentences.size();
					if (totalSentences == 0) sentences.add(params.text);
					
					binding.textview69.setText("GENERATING VOICE... 0/" + totalSentences);
					
					com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).updatePlaybackState("VoxSherpa Audio", "Generating...", com.CodeBySonu.VoxSherpa.system.VoxMediaController.STATE_GENERATING, false);
					
					new Thread(() -> {
						android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
						
						String loadResult = "";
						byte[] finalGeneratedPcm = null;
						int generatedSampleRate = 24000;
						java.io.ByteArrayOutputStream pcmStream = new java.io.ByteArrayOutputStream();
						
						float noise = 0.667f;
						float noiseW = 0.80f;
						
						if (currentVoiceStyle == 0) { 
							noise = 0.33f;
							noiseW = 0.50f;
						} else if (currentVoiceStyle == 2) { 
							noise = 0.85f;
							noiseW = 0.90f;
						}
						
						if (isKokoro) {
							com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().setActiveSpeakerId(params.kokoroVoiceId);
							loadResult = com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().loadModel(
									getContext(), params.onnxModel, params.tokens, params.voicesBin);
							if ("Success".equals(loadResult)) generatedSampleRate = com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().getSampleRate();
						} else if (isSupertonic) {
							com.CodeBySonu.VoxSherpa.SupertonicEngine engine = com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance();
							engine.setActiveSpeakerId(sp1.getInt("active_supertonic_speaker", 0));
							String modelDir = sp1.getString("active_supertonic_dir", "");
							engine.setActiveLanguage(selectedSupertonicLanguage);
							loadResult = engine.loadModel(getContext(), modelDir, selectedSupertonicLanguage);
							if ("Success".equals(loadResult)) generatedSampleRate = engine.getSampleRate();
						} else {
							com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().setActiveSpeakerId(currentPiperVoiceId); 
							com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().setNoiseScale(noise);
							com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().setNoiseScaleW(noiseW);
							
							String activeLanguage = sp1.getString("active_language", "");
							// FIX: Use the overloaded loadModel that accepts explicitly Boolean explicitIsMms
							loadResult = com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().loadModel(getContext(), params.onnxModel, params.tokens, activeLanguage, isMmsModel);
							if ("Success".equals(loadResult)) generatedSampleRate = com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().getSampleRate();
						}
						
						if (generatedSampleRate <= 0) {
							if (isKokoro) generatedSampleRate = 24000;
							else if (isSupertonic) generatedSampleRate = 44100;
							else generatedSampleRate = 22050;
						}
						
						final boolean[] doHandoff = {false};
						final int[] handoffHeadPos = {0};
						final int[] totalFramesWritten = {0};
						
						if ("Success".equals(loadResult)) {
							int minBufferSize = android.media.AudioTrack.getMinBufferSize(generatedSampleRate, 
							android.media.AudioFormat.CHANNEL_OUT_MONO, 
							android.media.AudioFormat.ENCODING_PCM_16BIT);
							
							if (minBufferSize <= 0) {
								minBufferSize = generatedSampleRate * 2; 
							}
							
							liveStreamTrack = new android.media.AudioTrack.Builder()
							.setAudioAttributes(new android.media.AudioAttributes.Builder()
							.setUsage(android.media.AudioAttributes.USAGE_MEDIA)
							.setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
							.build())
							.setAudioFormat(new android.media.AudioFormat.Builder()
							.setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
							.setSampleRate(generatedSampleRate)
							.setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
							.build())
							.setBufferSizeInBytes(minBufferSize)
							.setTransferMode(android.media.AudioTrack.MODE_STREAM)
							.build();
							
							java.util.concurrent.LinkedBlockingQueue<byte[]> audioQueue = new java.util.concurrent.LinkedBlockingQueue<>();
							
							Thread playerThread = new Thread(() -> {
								android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
								try {
									if (focusRequest != null && audioManager != null) {
										audioManager.requestAudioFocus(focusRequest);
									}
									
									if (liveStreamTrack != null) liveStreamTrack.play();
									
									while (true) {
										if (isCancelled || myGenId != currentGenerationId) break;
										
										if (doHandoff[0]) break; 
										
										byte[] chunk = audioQueue.take();
										if (chunk.length == 0) break; 
										
										while (liveStreamTrack != null && liveStreamTrack.getPlayState() != android.media.AudioTrack.PLAYSTATE_PLAYING && !isCancelled) {
											Thread.sleep(50); 
										}
										
										if (liveStreamTrack != null && !isCancelled) {
											int written = liveStreamTrack.write(chunk, 0, chunk.length);
											if (written > 0) totalFramesWritten[0] += (written / 2); 
										}
									}
								} catch (InterruptedException e) {
									Thread.currentThread().interrupt();
								} catch (Exception ignored) {
								} finally {
									try {
										if (liveStreamTrack != null) {
											handoffHeadPos[0] = liveStreamTrack.getPlaybackHeadPosition();
											liveStreamTrack.stop();
											liveStreamTrack.release();
											liveStreamTrack = null;
										}
									} catch (Exception ignored) {}
									
									try {
										if (audioManager != null && focusRequest != null) {
											audioManager.abandonAudioFocusRequest(focusRequest);
										}
									} catch (Exception ignored) {}
								}
							});
							playerThread.start();
							
							int doneCount = 0;
							for (String sentence : sentences) {
								if (isCancelled || myGenId != currentGenerationId) break;
								
								if (isMultiSpeakerOn) {
									sentence = com.CodeBySonu.VoxSherpa.PiperVoiceHelper.processAndSetSpeaker(sentence, isKokoro, isSupertonic);
								}
								
								if (sentence.isEmpty()) {
									doneCount++;
									continue;
								}
								
								byte[] chunkData = null;
								
								if ((params.punctOn || params.emotionOn) && !isSupertonic) {
									chunkData = com.CodeBySonu.VoxSherpa.AudioEmotionHelper.processAndGenerate(sentence, params.punctOn, params.emotionOn, params.speed, params.pitch, 1.0f, currentSilence);
								} else {
									if (isKokoro) {
										chunkData = com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().generateAudioPCM(sentence, params.speed, params.pitch);
									} else if (isSupertonic) {
										chunkData = com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().generateAudioPCM(
												sentence, params.speed, params.pitch, selectedSupertonicLanguage,
												selectedSupertonicSteps);
									} else {
										chunkData = com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().generateAudioPCM(sentence, params.speed, params.pitch);
									}
								}
								
								if (isCancelled || myGenId != currentGenerationId) break;
								
								if (chunkData != null && chunkData.length > 0) {
									try {
										pcmStream.write(chunkData);
										audioQueue.put(chunkData);
									} catch (Exception ignored) {}
								}
								doneCount++;
								final int current = doneCount;
								
								if (getActivity() != null && isAdded()) {
									getActivity().runOnUiThread(() -> {
										if (!isCancelled && myGenId == currentGenerationId) {
											binding.textview69.setText("GENERATING... " + current + "/" + totalSentences);
										}
									});
								}
							}
							
							doHandoff[0] = true;
							try {
								audioQueue.put(new byte[0]);
								playerThread.join(3000); 
							} catch (Exception ignored) {}
						}
						
						finalGeneratedPcm = pcmStream.toByteArray();
						
						try {
							pcmStream.close(); 
						} catch (Exception ignored) {}
						
						final byte[] finalPcm = finalGeneratedPcm;
						final String finalLoadResult = loadResult;
						final int finalSampleRate = generatedSampleRate;
						final int finalHead = handoffHeadPos[0];
						
						if (getActivity() != null && isAdded()) {
							getActivity().runOnUiThread(() -> {
								if (myGenId != currentGenerationId) return;
								isGenerating = false;
								binding.btnGenerate.setEnabled(true);
								binding.btnGenerate.setAlpha(1.0f);
								binding.progressGenerating.setVisibility(android.view.View.GONE);
								
								if ("Success".equals(finalLoadResult) && finalPcm != null && finalPcm.length > 0) {
									lastGeneratedPcmData = finalPcm;
									lastGeneratedSampleRate = finalSampleRate;
									isAudioGeneratedForCurrentText = true;
									lastParams = params;
									
									binding.layoutIdleState.setVisibility(android.view.View.GONE);
									binding.layoutGeneratedState.setVisibility(android.view.View.VISIBLE);
									binding.textview69.setText("GENERATION COMPLETE");
									
									final int totalFrames = finalPcm.length / 2;
									final double totalSeconds = (double) totalFrames / finalSampleRate;
									final int w = binding.imgWaveform.getWidth() > 0 ? binding.imgWaveform.getWidth() : 800;
									
									android.graphics.Bitmap waveBmp = com.CodeBySonu.VoxSherpa.WaveformHelper.createWaveformBitmap(finalPcm, w, 150);
									if (waveBmp != null) binding.imgWaveform.setImageBitmap(waveBmp);
									
									try {
										if (audioTrack != null) {
											audioTrack.release();
											audioTrack = null;
										}
										
										int staticBufferSize = finalPcm.length > 0 ? finalPcm.length : 1;
										
										audioTrack = new android.media.AudioTrack.Builder()
										.setAudioAttributes(new android.media.AudioAttributes.Builder()
										.setUsage(android.media.AudioAttributes.USAGE_MEDIA)
										.setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
										.build())
										.setAudioFormat(new android.media.AudioFormat.Builder()
										.setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
										.setSampleRate(finalSampleRate)
										.setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
										.build())
										.setBufferSizeInBytes(staticBufferSize)
										.setTransferMode(android.media.AudioTrack.MODE_STATIC)
										.build();
										
										audioTrack.write(finalPcm, 0, finalPcm.length);
										
										binding.playheadLine.setVisibility(android.view.View.VISIBLE);
										
										android.content.Intent durIntent = new android.content.Intent(getContext(), com.CodeBySonu.VoxSherpa.system.VoxMediaService.class);
										durIntent.setAction("ACTION_UPDATE_STATE");
										durIntent.putExtra("title", "VoxSherpa Audio");
										durIntent.putExtra("subtitle", "Playing...");
										durIntent.putExtra("state", com.CodeBySonu.VoxSherpa.system.VoxMediaController.STATE_PLAYING);
										durIntent.putExtra("isLibraryMode", false);
										durIntent.putExtra("duration", (long)(totalSeconds * 1000));
										
										if (finalHead < (totalFrames - 5000) && finalHead > 0) {
											audioTrack.setPlaybackHeadPosition(finalHead);
											audioTrack.play();
											
											binding.imageview52.setImageResource(R.drawable.icon_pause_circle);
											binding.textview92.setText("Pause");
											
											if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
												getContext().startForegroundService(durIntent);
											} else {
												getContext().startService(durIntent);
											}
											
											float progressFrac = (float) finalHead / totalFrames;
											float startX = progressFrac * w;
											binding.playheadLine.setTranslationX(startX);
											
											long remainingMs = (long) ((1.0 - progressFrac) * totalSeconds * 1000);
											
											if (playheadAnimator != null) playheadAnimator.cancel();
											playheadAnimator = android.animation.ValueAnimator.ofFloat(startX, (float) w);
											playheadAnimator.setDuration(remainingMs);
											playheadAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
											playheadAnimator.addUpdateListener(anim -> {
												float tx = (float) anim.getAnimatedValue();
												binding.playheadLine.setTranslationX(tx);
												double elapsed = (tx / w) * totalSeconds;
												binding.tvDuration.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d", (int)(elapsed/60), (int)(elapsed%60), (int)(totalSeconds/60), (int)(totalSeconds%60)));
											});
											
											playheadAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
												@Override
												public void onAnimationEnd(android.animation.Animator animation) {
													if (binding.playheadLine.getTranslationX() >= w - 10) {
														binding.imageview52.setImageResource(R.drawable.icon_play_circle);
														binding.textview92.setText("Play");
														binding.playheadLine.setTranslationX(0f);
														binding.tvDuration.setText(String.format(java.util.Locale.US, "0:00 / %d:%02d", (int)(totalSeconds/60), (int)(totalSeconds%60)));
														
														try {
															if (audioTrack != null) {
																audioTrack.pause();
																audioTrack.flush();
																audioTrack.stop();
																audioTrack.reloadStaticData();
																audioTrack.setPlaybackHeadPosition(0);
															}
														} catch (Exception ignored) {}
														
														com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification();
													}
												}
											});
											
											playheadAnimator.start();
										} else {
											audioTrack.setPlaybackHeadPosition(totalFrames);
											binding.imageview52.setImageResource(R.drawable.icon_play_circle);
											binding.textview92.setText("Play");
											binding.playheadLine.setTranslationX(0f);
											binding.tvDuration.setText(String.format(java.util.Locale.US, "0:00 / %d:%02d", (int)(totalSeconds/60), (int)(totalSeconds%60)));
											com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification();
										}
										
										audioTrack.setNotificationMarkerPosition(totalFrames);
										audioTrack.setPlaybackPositionUpdateListener(new android.media.AudioTrack.OnPlaybackPositionUpdateListener() {
											@Override
											public void onMarkerReached(android.media.AudioTrack track) {
												if (getActivity() != null && isAdded()) {
													getActivity().runOnUiThread(() -> {
														binding.imageview52.setImageResource(R.drawable.icon_play_circle);
														binding.textview92.setText("Play");
														binding.playheadLine.setTranslationX(0f);
														if (playheadAnimator != null) playheadAnimator.cancel();
														
														try {
															track.pause();
															track.flush();
															track.stop();
															track.reloadStaticData();
															track.setPlaybackHeadPosition(0);
														} catch (Exception ignored) {}
														
														com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification();
													});
												}
											}
											@Override public void onPeriodicNotification(android.media.AudioTrack track) {}
										});
										
									} catch (Exception ignored) {}
								} else {
									binding.progressGenerating.setVisibility(android.view.View.GONE);
									binding.imageview65.setVisibility(android.view.View.VISIBLE);
									binding.textview69.setText("SYNTHESIS FAILED");
									binding.textview69.setTextColor(android.graphics.Color.parseColor("#FF4B4B"));
									binding.textview92.setText("Play");
									binding.imageview52.setImageResource(R.drawable.icon_play_circle);
									
									com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification();
									
									if (getView() != null) {
										String userFriendlyMessage = (finalLoadResult != null && (finalLoadResult.contains("missing") || finalLoadResult.contains("empty")))
										? "Please select a valid voice model to continue."
										: "Unable to generate voice at the moment. Please try again.";
										
										com.google.android.material.snackbar.Snackbar.make(getView(), userFriendlyMessage, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
										.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
									}
								}
							});
						}
					}).start();
				}
				
			}
		});
		
		binding.save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				_saveAudioAction();
			}
		});
		
		binding.etInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence _param1, int _param2, int _param3, int _param4) {
				final String _charSeq = _param1.toString();
				
				int length = _charSeq.length();
				
				// NAYA: Hidden 25000 character limit
				if (length > 25000) {
					binding.etInput.setText(_charSeq.substring(0, 25000));
					binding.etInput.setSelection(25000);
				}
				
				// Reset state on text change
				String currentText = binding.etInput.getText().toString().trim();
				
				if (!currentText.equals(lastGeneratedText) && !isGenerating) {
					isAudioGeneratedForCurrentText = false;
					
					// UI STATE RESET
					binding.layoutIdleState.setVisibility(android.view.View.VISIBLE);
					binding.layoutGeneratedState.setVisibility(android.view.View.GONE);
					binding.progressGenerating.setVisibility(android.view.View.GONE);
					binding.imageview65.setVisibility(android.view.View.VISIBLE);
					
					binding.textview92.setText("Play");
					binding.imageview52.setImageResource(R.drawable.icon_play_circle); 
					
					binding.textview69.setText("READY TO SYNTHESIZE");
					binding.textview69.setTextColor(android.graphics.Color.parseColor("#3F4B61"));
					
					if (audioTrack != null && audioTrack.getState() != android.media.AudioTrack.STATE_UNINITIALIZED) {
						try { audioTrack.stop(); } catch (Exception e) {}
					}
					if (playheadAnimator != null) playheadAnimator.cancel();
				}
				
			}
			
			@Override
			public void beforeTextChanged(CharSequence _param1, int _param2, int _param3, int _param4) {
				
			}
			
			@Override
			public void afterTextChanged(Editable _param1) {
				
			}
		});
		
		binding.opneDropdown.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				// Use the exact model type instead of guessing from the model name
				String currentModelType = sp1.getString("active_model_type", "vits");
				boolean kokoroActive = _isKokoroModel(currentModelType);
				if (kokoroActive) return;
				
				String activePath = sp1.getString("active_model", "");
				int activeSpeakerCount = sp1.getInt("active_speaker_count", 1);
				
				// Return silently if no model is selected
				if (activePath.isEmpty()) {
					return;
				}
				
				// Show snackbar only if it is explicitly a Piper model and has 1 or fewer speakers
				if (!kokoroActive && activeSpeakerCount <= 1) {
					if (_view != null) {
						com.google.android.material.snackbar.Snackbar
						.make(_view, "This Voice model has only one voice.",
						com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
						.setBackgroundTint(android.graphics.Color.parseColor("#3F4B61"))
						.setTextColor(android.graphics.Color.WHITE)
						.show();
					}
					return;
				}
				
				if (listPopupWindow != null) {
					if (listPopupWindow.isShowing()) {
						listPopupWindow.dismiss();
					} else {
						if (kokoroActive) {
							int savedSpeakerId = sp1.getInt("active_kokoro_speaker", 31);
							java.util.List<com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem> voices =
							_getKokoroVoices();
							
							for (int i = 0; i < voices.size(); i++) {
								if (voices.get(i).speakerId == savedSpeakerId) {
									listPopupWindow.setSelection(i);
									break;
								}
							}
						} else if (currentModelType.equalsIgnoreCase("supertonic")) {
							listPopupWindow.setSelection(Math.max(0, Math.min(
									activeSpeakerCount - 1, sp1.getInt("active_supertonic_speaker", 0))));
						} else {
							// Piper multi-speaker selection logic
							int savedPiperSpeakerId = sp1.getInt("active_piper_speaker", 0);
							listPopupWindow.setSelection(savedPiperSpeakerId);
						}
						listPopupWindow.show();
					}
				}
				
			}
		});
		
		binding.btnExport.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				_saveAudioAction();
			}
		});
	}
	
	private void initializeLogic() {
		com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).setGenerateListener(new com.CodeBySonu.VoxSherpa.system.VoxMediaController.MediaCommandListener() {
			
			android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
			
			@Override
			public void onPlay() {
				mainHandler.post(() -> { try { _toggleGeneratePlayback(); } catch(Exception e) {} });
			}
			
			@Override
			public void onPause() {
				mainHandler.post(() -> { try { _toggleGeneratePlayback(); } catch(Exception e) {} });
			}
			
			@Override
			public void onStop() {
				mainHandler.post(() -> { try { _cancelGeneration(); } catch(Exception e) {} });
			}
			
			@Override
			public void onNext() {} 
			
			@Override
			public void onPrevious() {} 
		});
		
		// AUDIO FOCUS LOGIC
		audioManager = (android.media.AudioManager) getContext().getSystemService(android.content.Context.AUDIO_SERVICE);
		
		focusListener = focusChange -> {
			if (focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS || 
			focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
				
				if (liveStreamTrack != null && liveStreamTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PLAYING) {
					liveStreamTrack.pause();
				}
				
				if (audioTrack != null && audioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PLAYING) {
					audioTrack.pause();
					if (getActivity() != null) {
						getActivity().runOnUiThread(() -> {
							binding.imageview52.setImageResource(R.drawable.icon_play_circle);
							binding.textview92.setText("Play");
							if (playheadAnimator != null) playheadAnimator.pause();
							com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).updatePlaybackState("VoxSherpa Audio", "Paused", com.CodeBySonu.VoxSherpa.system.VoxMediaController.STATE_PAUSED, false);
						});
					}
				}
			} else if (focusChange == android.media.AudioManager.AUDIOFOCUS_GAIN) {
				if (liveStreamTrack != null) liveStreamTrack.play();
			}
		};
		
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			focusRequest = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
			.setOnAudioFocusChangeListener(focusListener)
			.build();
		}
		
		String activeModel = sp1.getString("active_model", "");
		String activeModelType = sp1.getString("active_model_type", "vits");
		String activeModelName = sp1.getString("active_model_name", "Custom Voice");
		int activeSpeakerCount = sp1.getInt("active_speaker_count", 1);
		boolean isKokoro = _isKokoroModel(activeModelType);
		boolean isSupertonic = activeModelType.equalsIgnoreCase("supertonic");
		if (activeModel.isEmpty()) {
			binding.voiceNameTv.setText("No Model Selected");
			binding.voiceNameTv.setTextColor(android.graphics.Color.parseColor("#FF4B4B"));
			binding.opneDropdown.setVisibility(android.view.View.GONE);
		} else {
			if (isKokoro || isSupertonic || activeSpeakerCount > 1) {
				binding.opneDropdown.setVisibility(android.view.View.VISIBLE);
			} else {
				binding.opneDropdown.setVisibility(android.view.View.GONE);
			}
			if (isSupertonic) {
				int speakerId = sp1.getInt("active_supertonic_speaker", 0);
				com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().setActiveSpeakerId(speakerId);
				binding.voiceNameTv.setText(getSupertonicVoiceProfile(speakerId));
			} else if (isKokoro) {
				try {
					int savedSpeakerId = sp1.getInt("active_kokoro_speaker", 31);
					com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().setActiveSpeakerId(savedSpeakerId);
					String voiceName = _getKokoroVoiceName(savedSpeakerId);
					binding.voiceNameTv.setText((voiceName != null && !voiceName.isEmpty()) ? voiceName : "Kokoro Voice");
				} catch (Throwable t) {
					binding.voiceNameTv.setText("Kokoro Voice");
				}
			} else {
				String piperLang = "Unknown";
				String piperGender = "Unknown";
				String modelLabel = "Piper";
				try {
					String modelsDataRaw = sp1.getString("models_data", "[]");
					if (!modelsDataRaw.equals("[]")) {
						java.util.ArrayList<java.util.HashMap<String, Object>> mList =
						new com.google.gson.Gson().fromJson(modelsDataRaw,
						new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
						if (mList != null) {
							for (java.util.HashMap<String, Object> model : mList) {
								String onnxPath = model.containsKey("onnx_path") && model.get("onnx_path") != null ? model.get("onnx_path").toString() : "";
								if (activeModel.equals(onnxPath)) {
									if (model.containsKey("language") && model.get("language") != null) {
										piperLang = model.get("language").toString().trim();
									}
									if (model.containsKey("gender") && model.get("gender") != null) {
										piperGender = model.get("gender").toString().trim();
									}
									if (model.containsKey("type") && model.get("type").toString().contains("MMS")) {
										modelLabel = "MMS";
									}
									break;
								}
							}
						}
					}
				} catch (Exception e) {}
				if (!piperLang.equals("Unknown") && piperLang.length() > 0) {
					piperLang = piperLang.substring(0, 1).toUpperCase() + piperLang.substring(1).toLowerCase();
				}
				if (!piperGender.equals("Unknown") && piperGender.length() > 0) {
					piperGender = piperGender.substring(0, 1).toUpperCase() + piperGender.substring(1).toLowerCase();
				}
				if (activeSpeakerCount > 1) {
					int currentPiperId = sp1.getInt("active_piper_speaker", 0);
					binding.voiceNameTv.setText(modelLabel + " • Speaker " + currentPiperId + " • " + piperLang);
				} else {
					binding.voiceNameTv.setText(modelLabel + " • " + piperGender + " • " + piperLang);
				}
			}
			binding.voiceNameTv.setTextColor(android.graphics.Color.WHITE);
		}
		listPopupWindow = new androidx.appcompat.widget.ListPopupWindow(getContext());
		listPopupWindow.setAnchorView(binding.voiceNameTv);
		listPopupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#121826")));
		java.util.List<String> voiceNames = new java.util.ArrayList<>();
		if (isKokoro) {
			java.util.List<com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem> allVoices = _getKokoroVoices();
			for (com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem vItem : allVoices) {
				try {
					String label = vItem.getFullLabel();
					voiceNames.add((label != null && !label.isEmpty()) ? label : "Unknown Voice");
				} catch (Throwable t) {
					voiceNames.add("Unknown Voice");
				}
			}
		} else if (isSupertonic || activeSpeakerCount > 1) {
			for (int i = 0; i < activeSpeakerCount; i++) {
				voiceNames.add(isSupertonic ? getSupertonicVoiceProfile(i) : "Speaker " + i);
			}
		}
		voiceAdapter = new android.widget.ArrayAdapter<>(getContext(), R.layout.dropdown_item, R.id.text1, voiceNames);
		listPopupWindow.setAdapter(voiceAdapter);
		listPopupWindow.setOnItemClickListener((parent, view, position, id) -> {
			try {
				String selectedLabel = voiceAdapter.getItem(position);
				if (selectedLabel == null) return;
				String currentType = sp1.getString("active_model_type", "vits");
				boolean currentlyKokoro = _isKokoroModel(currentType);
				boolean currentlySupertonic = currentType.equalsIgnoreCase("supertonic");
				if (currentlyKokoro) {
					binding.voiceNameTv.setText(selectedLabel);
					for (com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem vItem : _getKokoroVoices()) {
						try {
							if (selectedLabel.equals(vItem.getFullLabel())) {
								com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().setActiveSpeakerId(vItem.speakerId);
								sp1.edit().putInt("active_kokoro_speaker", vItem.speakerId).apply();
								break;
							}
						} catch (Throwable t) {}
					}
				} else if (currentlySupertonic) {
					sp1.edit().putInt("active_supertonic_speaker", position).apply();
					com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().setActiveSpeakerId(position);
					binding.voiceNameTv.setText(getSupertonicVoiceProfile(position));
				} else {
					sp1.edit().putInt("active_piper_speaker", position).apply();
					com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().setActiveSpeakerId(position);
					String currentText = binding.voiceNameTv.getText().toString();
					String langPart = "Unknown";
					String modelPart = "Piper";
					String[] parts = currentText.split(" • ");
					if (parts.length >= 1) modelPart = parts[0];
					if (parts.length >= 3) langPart = parts[2];
					binding.voiceNameTv.setText(modelPart + " • Speaker " + position + " • " + langPart);
				}
				try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().cancel(); } catch (Throwable ignored) {}
				try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().cancel(); } catch (Throwable ignored) {}
				try { com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().cancel(); } catch (Throwable ignored) {}
				currentGenerationId++;
				isCancelled = true;
				isGenerating = false;
				isAudioGeneratedForCurrentText = false;
				try {
					if (audioTrack != null) {
						audioTrack.stop();
						audioTrack.flush();
						audioTrack.release();
						audioTrack = null;
					}
				} catch (Exception ignored) {}
				try {
					if (liveStreamTrack != null) {
						liveStreamTrack.stop();
						liveStreamTrack.release();
						liveStreamTrack = null;
					}
				} catch (Exception ignored) {}
				lastGeneratedPcmData = null;
				lastParams = null;
				if (playheadAnimator != null) playheadAnimator.cancel();
				binding.playheadLine.setTranslationX(0f);
				binding.btnGenerate.setAlpha(1.0f);
				binding.btnGenerate.setEnabled(true);
				binding.layoutGeneratedState.setVisibility(android.view.View.GONE);
				binding.layoutIdleState.setVisibility(android.view.View.VISIBLE);
				binding.progressGenerating.setVisibility(android.view.View.GONE);
				binding.imageview65.setVisibility(android.view.View.VISIBLE);
				binding.textview69.setText("READY TO GENERATE");
				binding.textview69.setTextColor(android.graphics.Color.parseColor("#94A3B8"));
				binding.textview92.setText("Play");
				binding.imageview52.setImageResource(R.drawable.icon_play_circle);
			} catch (Throwable t) {}
			listPopupWindow.dismiss();
		});
		
		binding.imgWaveform.setOnTouchListener((v, motionEvent) -> {
			if (lastGeneratedPcmData == null || audioTrack == null) return false;
			
			int w = binding.imgWaveform.getWidth();
			if (w <= 0) return false;
			
			int totalFrames = lastGeneratedPcmData.length / 2;
			double totalSeconds = (double) totalFrames / lastGeneratedSampleRate;
			int totalMin = (int)(totalSeconds / 60);
			int totalSec = (int)(totalSeconds % 60);
			
			float touchX = Math.max(0f, Math.min(motionEvent.getX(), (float) w));
			float fraction = touchX / w;
			int targetFrame = (int)(fraction * totalFrames);
			
			if (motionEvent.getAction() == android.view.MotionEvent.ACTION_DOWN) {
				if (playheadAnimator != null) playheadAnimator.pause();
				try { audioTrack.pause(); } catch (Exception ignored) {}
			}
			
			if (motionEvent.getAction() == android.view.MotionEvent.ACTION_DOWN
			|| motionEvent.getAction() == android.view.MotionEvent.ACTION_MOVE) {
				binding.playheadLine.setTranslationX(touchX);
				double seekSec = fraction * totalSeconds;
				binding.tvDuration.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d",
				(int)(seekSec / 60), (int)(seekSec % 60), totalMin, totalSec));
			}
			
			if (motionEvent.getAction() == android.view.MotionEvent.ACTION_UP
			|| motionEvent.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
				try {
					audioTrack.stop();
					audioTrack.reloadStaticData();
					audioTrack.setPlaybackHeadPosition(targetFrame);
					
					// 🚀 FIX: Naya object nahi banega — fragment-level same object reuse hoga
					_requestAudioFocus();
					
					audioTrack.play();
				} catch (Exception ignored) {}
				
				float remainingSec = (float)(totalFrames - targetFrame) / lastGeneratedSampleRate;
				if (remainingSec < 0) remainingSec = 0f;
				
				if (playheadAnimator != null) playheadAnimator.cancel();
				playheadAnimator = android.animation.ValueAnimator.ofFloat(touchX, (float) w);
				playheadAnimator.setDuration((long)(remainingSec * 1000));
				playheadAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
				playheadAnimator.addUpdateListener(anim -> {
					float tx = (float) anim.getAnimatedValue();
					binding.playheadLine.setTranslationX(tx);
					// tvDuration live update during playback after seek
					double elapsed = (tx / w) * totalSeconds;
					binding.tvDuration.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d",
					(int)(elapsed / 60), (int)(elapsed % 60), totalMin, totalSec));
				});
				playheadAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(android.animation.Animator animation) {
						if (binding.playheadLine.getTranslationX() >= w - 10) {
							binding.imageview52.setImageResource(R.drawable.icon_play_circle);
							binding.textview92.setText("Play");
							binding.playheadLine.setTranslationX(0f);
							binding.tvDuration.setText(String.format(java.util.Locale.US,
							"0:00 / %d:%02d", totalMin, totalSec));
							try {
								if (audioTrack != null) {
									audioTrack.stop();
									audioTrack.reloadStaticData();
								}
							} catch (Exception ignored) {}
						}
					}
				});
				playheadAnimator.start();
				
				binding.imageview52.setImageResource(R.drawable.icon_pause_circle);
				binding.textview92.setText("Pause");
			}
			
			return true;
		});
		
		_updateSupertonicLanguageSelector();
	}

	private String _getSupertonicLanguageName(String languageCode) {
		String normalizedLanguage = com.CodeBySonu.VoxSherpa.SupertonicEngine.normalizeLanguage(languageCode);
		String languageName = java.util.Locale.forLanguageTag(normalizedLanguage)
				.getDisplayLanguage(java.util.Locale.ENGLISH);
		return languageName.isEmpty() ? normalizedLanguage.toUpperCase(java.util.Locale.ROOT) : languageName;
	}

	private void _updateSupertonicLanguageSelector() {
		if (binding == null || sp1 == null) return;
		boolean isSupertonic = !sp1.getString("active_model", "").isEmpty()
				&& sp1.getString("active_model_type", "vits").equalsIgnoreCase("supertonic");
		binding.supertonicControlsLayout.setVisibility(isSupertonic ? android.view.View.VISIBLE : android.view.View.GONE);
		binding.voiceInfoCard.setVisibility(isSupertonic ? android.view.View.GONE : android.view.View.VISIBLE);
		binding.kokoroVoiceFilters.setVisibility(
				isSupertonic || !sp1.getString("active_model_type", "vits").equalsIgnoreCase("kokoro")
						? android.view.View.GONE : android.view.View.VISIBLE);
		if (!isSupertonic) return;
		updateSupertonicControlLabels();
	}

	private void updateSupertonicControlLabels() {
		if (binding == null || sp1 == null || sp3 == null) return;
		String language = SupertonicEngine.normalizeLanguage(sp1.getString("active_language", "en"));
		int speakerId = sp1.getInt("active_supertonic_speaker", 0);
		String profile = getSupertonicVoiceProfile(speakerId);
		int separator = profile.indexOf(" - ");
		String styleCode = separator >= 0 ? profile.substring(0, separator) : profile;
		binding.supertonicVoiceValue.setText(
				"Supertonic • " + styleCode + " " + _getSupertonicLanguageName(language));
	}

	private void showSupertonicQualityDialog() {
		if (getContext() == null) return;
		int savedSteps = Math.max(SupertonicEngine.MIN_GENERATION_STEPS,
				Math.min(SupertonicEngine.MAX_GENERATION_STEPS,
						sp3.getInt("supertonic_num_steps", SupertonicEngine.DEFAULT_GENERATION_STEPS)));
		android.view.View content = getLayoutInflater().inflate(R.layout.dialog_supertonic_quality, null);
		android.widget.SeekBar seekBar = content.findViewById(R.id.supertonic_quality_seekbar);
		android.widget.TextView value = content.findViewById(R.id.supertonic_quality_value);
		seekBar.setMax(SupertonicEngine.MAX_GENERATION_STEPS - SupertonicEngine.MIN_GENERATION_STEPS);
		seekBar.setProgress(savedSteps - SupertonicEngine.MIN_GENERATION_STEPS);
		value.setText(savedSteps + " steps");
		seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
				value.setText((progress + SupertonicEngine.MIN_GENERATION_STEPS) + " steps");
			}

			@Override
			public void onStartTrackingTouch(android.widget.SeekBar bar) {}

			@Override
			public void onStopTrackingTouch(android.widget.SeekBar bar) {}
		});
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle("Generation Quality")
				.setView(content)
				.setNegativeButton("Cancel", null)
				.setPositiveButton("Done", (dialog, which) -> {
					int selectedSteps = seekBar.getProgress() + SupertonicEngine.MIN_GENERATION_STEPS;
					sp3.edit().putInt("supertonic_num_steps", selectedSteps).apply();
					updateSupertonicControlLabels();
					_forceResetToIdle();
				})
				.show();
	}

	private void showSupertonicVoiceSheet() {
		if (getContext() == null) return;
		com.google.android.material.bottomsheet.BottomSheetDialog dialog =
				new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
		android.view.View content = getLayoutInflater().inflate(R.layout.bottom_sheet_supertonic, null);
		android.widget.Spinner languageSpinner = content.findViewById(R.id.supertonic_language_spinner);
		android.widget.LinearLayout voiceList = content.findViewById(R.id.supertonic_voice_list);
		String selectedLanguage = SupertonicEngine.normalizeLanguage(sp1.getString("active_language", "en"));
		java.util.List<String> languageCodes = new java.util.ArrayList<>(
				java.util.Arrays.asList(SupertonicEngine.getSupportedLanguages()));
		java.util.Collections.sort(languageCodes,
				java.util.Comparator.comparing(this::_getSupertonicLanguageName));
		java.util.List<String> languageNames = new java.util.ArrayList<>();
		int selectedLanguagePosition = 0;
		for (int i = 0; i < languageCodes.size(); i++) {
			String code = languageCodes.get(i);
			languageNames.add(_getSupertonicLanguageName(code));
			if (selectedLanguage.equals(code)) selectedLanguagePosition = i;
		}
		android.widget.ArrayAdapter<String> languageAdapter = new android.widget.ArrayAdapter<>(
				requireContext(), android.R.layout.simple_spinner_item, languageNames);
		languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		languageSpinner.setAdapter(languageAdapter);
		languageSpinner.setSelection(selectedLanguagePosition, false);
		languageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
					int position, long id) {
				String newLanguage = languageCodes.get(position);
				String oldLanguage = SupertonicEngine.normalizeLanguage(sp1.getString("active_language", "en"));
				if (!newLanguage.equals(oldLanguage)) {
					sp1.edit().putString("active_language", newLanguage).apply();
					SupertonicEngine.getInstance().setActiveLanguage(newLanguage);
					updateSupertonicControlLabels();
					_forceResetToIdle();
				}
			}

			@Override
			public void onNothingSelected(android.widget.AdapterView<?> parent) {}
		});

		int selectedSpeaker = sp1.getInt("active_supertonic_speaker", 0);
		for (int i = 0; i < SUPERTONIC_VOICE_PROFILES.length; i++) {
			final int speakerId = i;
			com.google.android.material.card.MaterialCardView row =
					new com.google.android.material.card.MaterialCardView(requireContext());
			android.widget.LinearLayout.LayoutParams rowParams = new android.widget.LinearLayout.LayoutParams(
					android.view.ViewGroup.LayoutParams.MATCH_PARENT,
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
			rowParams.bottomMargin = Math.round(6 * getResources().getDisplayMetrics().density);
			row.setLayoutParams(rowParams);
			row.setClickable(true);
			row.setFocusable(true);
			row.setForeground(androidx.core.content.ContextCompat.getDrawable(
					requireContext(), android.R.drawable.list_selector_background));
			row.setCardBackgroundColor(android.graphics.Color.parseColor(
					i == selectedSpeaker ? "#173044" : "#121826"));
			row.setStrokeColor(android.graphics.Color.parseColor(
					i == selectedSpeaker ? "#00E5FF" : "#1A2234"));
			row.setStrokeWidth(Math.round(getResources().getDisplayMetrics().density));
			row.setRadius(Math.round(12 * getResources().getDisplayMetrics().density));

			android.widget.TextView label = new android.widget.TextView(requireContext());
			label.setText(SUPERTONIC_VOICE_PROFILES[i]);
			label.setTextColor(android.graphics.Color.WHITE);
			label.setTextSize(15);
			label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
			int horizontalPadding = Math.round(16 * getResources().getDisplayMetrics().density);
			int verticalPadding = Math.round(16 * getResources().getDisplayMetrics().density);
			label.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
			row.addView(label);
			row.setOnClickListener(view -> {
				sp1.edit().putInt("active_supertonic_speaker", speakerId).apply();
				SupertonicEngine.getInstance().setActiveSpeakerId(speakerId);
				updateSupertonicControlLabels();
				_forceResetToIdle();
				dialog.dismiss();
			});
			voiceList.addView(row);
		}

		dialog.setContentView(content);
		dialog.show();
	}

	private static String getSupertonicVoiceProfile(int speakerId) {
		return speakerId >= 0 && speakerId < SUPERTONIC_VOICE_PROFILES.length
				? SUPERTONIC_VOICE_PROFILES[speakerId] : "Speaker " + speakerId;
	}
	
	@Override
	public void onActivityResult(int _requestCode, int _resultCode, Intent _data) {
		super.onActivityResult(_requestCode, _resultCode, _data);
		if (_requestCode == 100) {
			if (_resultCode == android.app.Activity.RESULT_OK && _data != null) {
				java.util.ArrayList<String> result = _data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
				if (result != null && !result.isEmpty()) {
					String spokenText = result.get(0);
					String currentText = binding.etInput.getText().toString();
					String finalText;
					if (!currentText.trim().isEmpty()) {
						finalText = currentText + " " + spokenText;
					} else {
						finalText = spokenText;
					}
					if (finalText.length() > 25000) {
						finalText = finalText.substring(0, 25000);
					}
					binding.etInput.setText(finalText);
					binding.etInput.setSelection(finalText.length());
				}
			}
		}
		if (_requestCode == 101) {
			if (_resultCode == android.app.Activity.RESULT_OK && _data != null && _data.getData() != null) {
				android.net.Uri fileUri = _data.getData();
				boolean isPdf = false;
				String mimeType = getContext().getContentResolver().getType(fileUri);
				if ("application/pdf".equalsIgnoreCase(mimeType)) {
					isPdf = true;
				} else {
					String path = fileUri.getPath();
					if (path != null && path.toLowerCase().endsWith(".pdf")) {
						isPdf = true;
					}
				}
				com.CodeBySonu.VoxSherpa.TextImportHelper._readDocument(getContext(), fileUri, isPdf, new com.CodeBySonu.VoxSherpa.TextImportHelper.TextImportCallback() {
					@Override
					public void onSuccess(String text) {
						if (text != null) {
							String formattedText = text.trim();
							if (formattedText.length() > 25000) {
								formattedText = formattedText.substring(0, 25000);
							}
							binding.etInput.setText(formattedText);
							binding.etInput.setSelection(formattedText.length());
						}
					}
					@Override
					public void onError(String errorMessage) {
						if (getView() != null) {
							com.google.android.material.snackbar.Snackbar.make(getView(), errorMessage, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
							.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"))
							.setTextColor(android.graphics.Color.WHITE)
							.show();
						}
					}
				});
			}
		}
		
		switch (_requestCode) {
			
			default:
			break;
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		// Fragment me sirf AudioTrack aur UI Animator ko release karna hai
		if (audioTrack != null) {
			try {
				if (audioTrack.getState() != android.media.AudioTrack.STATE_UNINITIALIZED) {
					audioTrack.stop();
				}
				audioTrack.release();
				audioTrack = null;
			} catch (Exception ignored) {}
		}
		
		if (playheadAnimator != null) {
			playheadAnimator.cancel();
			playheadAnimator = null;
		}
		
	}
	
	private boolean _isKokoroModel(String modelType) {
		return "kokoro".equalsIgnoreCase(modelType);
	}

	private java.util.List<com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem> _getKokoroVoices() {
		return com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.getAllVoices();
	}

	private String _getKokoroVoiceName(int speakerId) {
		for (com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem voice : _getKokoroVoices()) {
			if (voice.speakerId == speakerId) return voice.getFullLabel();
		}
		return "Kokoro Voice";
	}

	private void updateKokoroVoiceFilters(boolean isKokoro, int selectedSpeakerId) {
		binding.kokoroVoiceFilters.setVisibility(isKokoro ? android.view.View.VISIBLE : android.view.View.GONE);
		boolean isSupertonic = sp1.getString("active_model_type", "vits").equalsIgnoreCase("supertonic")
				&& !sp1.getString("active_model", "").isEmpty();
		binding.voiceInfoCard.setVisibility(isKokoro || isSupertonic
				? android.view.View.GONE : android.view.View.VISIBLE);
		if (!isKokoro) return;

		binding.opneDropdown.setVisibility(android.view.View.GONE);
		kokoroRegions = new java.util.ArrayList<>(
				com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.getAvailableRegions());
		java.util.ArrayList<String> regionLabels = new java.util.ArrayList<>();
		for (com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.RegionItem region : kokoroRegions) {
			regionLabels.add(region.getLabel());
		}

		suppressKokoroFilterCallbacks = true;
		binding.kokoroRegionSpinner.setOnItemSelectedListener(null);
		binding.kokoroVoiceSpinner.setOnItemSelectedListener(null);
		binding.kokoroRegionSpinner.setAdapter(createKokoroSpinnerAdapter(regionLabels));

		com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem activeVoice =
				com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.getById(selectedSpeakerId);
		if (activeVoice == null) {
			activeVoice = com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.getDefaultVoice();
			selectedSpeakerId = activeVoice.speakerId;
			sp1.edit().putInt("active_kokoro_speaker", selectedSpeakerId).apply();
		}
		int selectedRegionPosition = 0;
		for (int i = 0; i < kokoroRegions.size(); i++) {
			if (kokoroRegions.get(i).regionCode.equals(activeVoice.regionCode)) {
				selectedRegionPosition = i;
				break;
			}
		}
		binding.kokoroRegionSpinner.setSelection(selectedRegionPosition, false);
		bindKokoroVoices(kokoroRegions.get(selectedRegionPosition).regionCode, selectedSpeakerId);
		binding.voiceNameTv.setText(activeVoice.getFullLabel());
		com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().setActiveSpeakerId(selectedSpeakerId);
		suppressKokoroFilterCallbacks = false;

		binding.kokoroRegionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
					int position, long id) {
				if (suppressKokoroFilterCallbacks || position < 0 || position >= kokoroRegions.size()) return;
				bindKokoroVoices(kokoroRegions.get(position).regionCode,
						sp1.getInt("active_kokoro_speaker", 31));
			}

			@Override
			public void onNothingSelected(android.widget.AdapterView<?> parent) {}
		});
		binding.kokoroVoiceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
					int position, long id) {
				if (suppressKokoroFilterCallbacks || position < 0 || position >= kokoroFilteredVoices.size()) return;
				selectKokoroVoice(kokoroFilteredVoices.get(position));
			}

			@Override
			public void onNothingSelected(android.widget.AdapterView<?> parent) {}
		});
	}

	private android.widget.ArrayAdapter<String> createKokoroSpinnerAdapter(java.util.List<String> labels) {
		android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
				requireContext(), R.layout.kokoro_spinner_item, R.id.text1, labels);
		adapter.setDropDownViewResource(R.layout.dropdown_item);
		return adapter;
	}

	private void bindKokoroVoices(String regionCode, int preferredSpeakerId) {
		kokoroFilteredVoices = new java.util.ArrayList<>(
				com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.getVoicesByRegionCode(regionCode));
		if (kokoroFilteredVoices.isEmpty()) return;

		java.util.ArrayList<String> voiceLabels = new java.util.ArrayList<>();
		int selectedPosition = 0;
		for (int i = 0; i < kokoroFilteredVoices.size(); i++) {
			com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem voice = kokoroFilteredVoices.get(i);
			voiceLabels.add(voice.displayName + " • " + voice.gender);
			if (voice.speakerId == preferredSpeakerId) selectedPosition = i;
		}
		suppressKokoroFilterCallbacks = true;
		binding.kokoroVoiceSpinner.setAdapter(createKokoroSpinnerAdapter(voiceLabels));
		binding.kokoroVoiceSpinner.setSelection(selectedPosition, false);
		suppressKokoroFilterCallbacks = false;
		selectKokoroVoice(kokoroFilteredVoices.get(selectedPosition));
	}

	private void selectKokoroVoice(com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem voice) {
		int previousSpeakerId = sp1.getInt("active_kokoro_speaker", 31);
		sp1.edit().putInt("active_kokoro_speaker", voice.speakerId).apply();
		com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().setActiveSpeakerId(voice.speakerId);
		binding.voiceNameTv.setText(voice.getFullLabel());
		if (previousSpeakerId != voice.speakerId) _forceResetToIdle();
	}

	@Override
	public void onResume() {
		super.onResume();
		String activeModel = sp1.getString("active_model", "");
		String activeModelType = sp1.getString("active_model_type", "vits");
		int activeSpeakerCount = sp1.getInt("active_speaker_count", 1);
		boolean isKokoro = _isKokoroModel(activeModelType);
		boolean isSupertonic = activeModelType.equalsIgnoreCase("supertonic");
		if (activeModel.isEmpty()) {
			binding.voiceNameTv.setText("No Model Selected");
			binding.voiceNameTv.setTextColor(android.graphics.Color.parseColor("#FF4B4B"));
			binding.opneDropdown.setVisibility(android.view.View.GONE);
		} else {
			if (isKokoro || isSupertonic || activeSpeakerCount > 1) {
				binding.opneDropdown.setVisibility(android.view.View.VISIBLE);
			} else {
				binding.opneDropdown.setVisibility(android.view.View.GONE);
			}
			if (isSupertonic) {
				int speakerId = sp1.getInt("active_supertonic_speaker", 0);
				com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().setActiveSpeakerId(speakerId);
				binding.voiceNameTv.setText(getSupertonicVoiceProfile(speakerId));
			} else if (isKokoro) {
				try {
					int savedSpeakerId = sp1.getInt("active_kokoro_speaker", 31);
					com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().setActiveSpeakerId(savedSpeakerId);
					String voiceName = _getKokoroVoiceName(savedSpeakerId);
					binding.voiceNameTv.setText((voiceName != null && !voiceName.isEmpty()) ? voiceName : "Kokoro Voice");
				} catch (Throwable t) {
					binding.voiceNameTv.setText("Kokoro Voice");
				}
			} else {
				String piperLang = "Unknown";
				String piperGender = "Unknown";
				String modelLabel = "Piper";
				try {
					String modelsDataRaw = sp1.getString("models_data", "[]");
					if (!modelsDataRaw.equals("[]")) {
						java.util.ArrayList<java.util.HashMap<String, Object>> mList =
						new com.google.gson.Gson().fromJson(modelsDataRaw,
						new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
						if (mList != null) {
							for (java.util.HashMap<String, Object> model : mList) {
								String onnxPath = model.containsKey("onnx_path") && model.get("onnx_path") != null ? model.get("onnx_path").toString() : "";
								if (activeModel.equals(onnxPath)) {
									if (model.containsKey("language") && model.get("language") != null) {
										piperLang = model.get("language").toString().trim();
									}
									if (model.containsKey("gender") && model.get("gender") != null) {
										piperGender = model.get("gender").toString().trim();
									}
									if (model.containsKey("type") && model.get("type").toString().contains("MMS")) {
										modelLabel = "MMS";
									}
									break;
								}
							}
						}
					}
				} catch (Exception e) {}
				if (!piperLang.equals("Unknown") && piperLang.length() > 0) {
					piperLang = piperLang.substring(0, 1).toUpperCase() + piperLang.substring(1).toLowerCase();
				}
				if (!piperGender.equals("Unknown") && piperGender.length() > 0) {
					piperGender = piperGender.substring(0, 1).toUpperCase() + piperGender.substring(1).toLowerCase();
				}
				if (activeSpeakerCount > 1) {
					int currentPiperId = sp1.getInt("active_piper_speaker", 0);
					binding.voiceNameTv.setText(modelLabel + " • Speaker " + currentPiperId + " • " + piperLang);
				} else {
					binding.voiceNameTv.setText(modelLabel + " • " + piperGender + " • " + piperLang);
				}
			}
			binding.voiceNameTv.setTextColor(android.graphics.Color.WHITE);
		}
		if (listPopupWindow != null) {
			java.util.List<String> newVoiceNames = new java.util.ArrayList<>();
			if (isKokoro) {
				java.util.List<com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem> allVoices = _getKokoroVoices();
				for (com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem vItem : allVoices) {
					try {
						String label = vItem.getFullLabel();
						newVoiceNames.add((label != null && !label.isEmpty()) ? label : "Unknown Voice");
					} catch (Throwable t) {
						newVoiceNames.add("Unknown Voice");
					}
				}
			} else if (isSupertonic || activeSpeakerCount > 1) {
				for (int i = 0; i < activeSpeakerCount; i++) {
					newVoiceNames.add(isSupertonic ? getSupertonicVoiceProfile(i) : "Speaker " + i);
				}
			}
			if (voiceAdapter != null) {
				voiceAdapter.clear();
				voiceAdapter.addAll(newVoiceNames);
				voiceAdapter.notifyDataSetChanged();
			}
		}
		_updateSupertonicLanguageSelector();
		updateKokoroVoiceFilters(isKokoro, sp1.getInt("active_kokoro_speaker", 31));
		String currentSettingsState = sp3.getFloat("voice_speed", 1.0f) + "_" +
		sp3.getFloat("voice_pitch", 1.0f) + "_" +
		sp3.getFloat("silence_scale", 0.2f) + "_" +
		sp3.getBoolean("smart_punct", false) + "_" +
		sp3.getBoolean("emotion_tags", false) + "_" +
		sp3.getBoolean("multi_speaker_tags", false) + "_" +
		sp3.getInt("voice_style", 1);
		boolean isSettingChanged = !lastSettingsState.isEmpty() && !currentSettingsState.equals(lastSettingsState);
		boolean isModelChanged = !currentUiModelPath.isEmpty() && !activeModel.equals(currentUiModelPath);
		if (isModelChanged || isSettingChanged) {
			_forceResetToIdle();
		}
		currentUiModelPath = activeModel;
		lastSettingsState = currentSettingsState;
		
	}
	
	
	@Override
	public void onStart() {
		super.onStart();
		try {
			// Check if Activity is not null and is an instance of MainActivity
			if (getActivity() != null && getActivity() instanceof MainActivity) {
				MainActivity mainActivity = (MainActivity) getActivity();
				if (mainActivity.sharedProcessText != null && !mainActivity.sharedProcessText.isEmpty()) {
					binding.etInput.setText(mainActivity.sharedProcessText);
					binding.etInput.setSelection(mainActivity.sharedProcessText.length());
					mainActivity.sharedProcessText = ""; 
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	public void _saveAudioAction() {
		// ─── SAVE BUTTON LOGIC ──────────────────────────────────────────────────────
		
		// Pehle check karenge ki audio generate hui hai ya nahi.
		if (!isAudioGeneratedForCurrentText || lastGeneratedPcmData == null) {
			if (getView() != null) {
				com.google.android.material.snackbar.Snackbar.make(getView(), "Please generate audio first!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
				.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"))
				.setTextColor(android.graphics.Color.WHITE)
				.show();
			}
			return;
		}
		
		// Yahan se aapka original save logic shuru hota hai (Bina kisi ads ke)
		int sampleRateToSave = lastGeneratedSampleRate > 0 ? lastGeneratedSampleRate : 22050;
		
		String cleanFileName = "Vox_" + System.currentTimeMillis() + ".wav";
		String savedPath = com.CodeBySonu.VoxSherpa.AudioHelper.saveWavFile(lastGeneratedPcmData, cleanFileName, sampleRateToSave, getContext());
		
		if (!savedPath.isEmpty()) {
			try {
				String libraryData = sp2.getString("library_list", "[]");
				java.util.ArrayList<java.util.HashMap<String, Object>> libList = new com.google.gson.Gson().fromJson(
				libraryData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType()
				);
				
				if (libList == null) {
					libList = new java.util.ArrayList<>();
				}
				
				java.util.HashMap<String, Object> newItem = new java.util.HashMap<>();
				newItem.put("title", cleanFileName);
				
				// Extract the first sentence from the generated text for a clean title
				String rawText = (lastParams != null && lastParams.text != null) ? lastParams.text : lastGeneratedText;
				if (rawText == null || rawText.trim().isEmpty()) {
					rawText = binding.etInput.getText().toString().trim();
				}
				
				String displayTitle = rawText.trim();
				int dotIndex = displayTitle.indexOf('.');
				int newlineIndex = displayTitle.indexOf('\n');
				int endIndex = -1;
				
				if (dotIndex > 0 && newlineIndex > 0) {
					endIndex = Math.min(dotIndex, newlineIndex);
				} else if (dotIndex > 0) {
					endIndex = dotIndex;
				} else if (newlineIndex > 0) {
					endIndex = newlineIndex;
				}
				
				if (endIndex > 0) {
					displayTitle = displayTitle.substring(0, endIndex + 1).trim();
				} else if (displayTitle.length() > 50) {
					displayTitle = displayTitle.substring(0, 50).trim() + "...";
				}
				
				if (displayTitle.isEmpty()) {
					displayTitle = "Saved Audio";
				}
				
				newItem.put("text", displayTitle);
				newItem.put("full_text", rawText); 
				newItem.put("path", savedPath);
				
				// Exact duration calculation to prevent UI formatting bugs
				double totalSeconds = (lastGeneratedPcmData.length / 2.0) / sampleRateToSave;
				int min = (int) (totalSeconds / 60);
				int sec = (int) (totalSeconds % 60);
				String cleanDuration = String.format(java.util.Locale.US, "%d:%02d", min, sec);
				
				newItem.put("duration", cleanDuration);
				newItem.put("timestamp", String.valueOf(System.currentTimeMillis()));
				newItem.put("voice_name", binding.voiceNameTv.getText().toString());
				newItem.put("is_favorite", false);
				
				libList.add(0, newItem);
				sp2.edit().putString("library_list", new com.google.gson.Gson().toJson(libList)).apply();
				
				if (getView() != null) {
					com.google.android.material.snackbar.Snackbar.make(getView(), "Audio saved to Library!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
					.setBackgroundTint(android.graphics.Color.parseColor("#00E5FF"))
					.setTextColor(android.graphics.Color.WHITE)
					.show();
				}
			} catch (Exception e) {
				if (getView() != null) {
					com.google.android.material.snackbar.Snackbar.make(getView(), "Failed to update library data.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
					.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"))
					.setTextColor(android.graphics.Color.WHITE)
					.show();
				}
			}
		} else {
			if (getView() != null) {
				com.google.android.material.snackbar.Snackbar.make(getView(), "Failed to save audio file.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
				.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"))
				.setTextColor(android.graphics.Color.WHITE)
				.show();
			}
		}
		// ─── SAVE BUTTON LOGIC END ──────────────────────────────────────────────────
		
	}
	
	
	public void _forceResetToIdle() {
		// CRITICAL SIGSEGV FIX: Cancel native engines to stop C++ background generation immediately
		try {
			com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().cancel();
			com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().cancel();
			com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().cancel();
		} catch (Throwable ignored) {}
		
		currentGenerationId++; 
		isCancelled = true;
		isGenerating = false;
		isAudioGeneratedForCurrentText = false;
		lastParams = null;
		lastGeneratedPcmData = null;
		
		try {
			if (audioTrack != null) {
				audioTrack.stop();
				audioTrack.release();
				audioTrack = null;
			}
		} catch (Exception ignored) {}
		
		try {
			if (liveStreamTrack != null) {
				liveStreamTrack.stop();
				liveStreamTrack.release();
				liveStreamTrack = null;
			}
		} catch (Exception ignored) {}
		
		if (playheadAnimator != null) playheadAnimator.cancel();
		
		binding.playheadLine.setTranslationX(0f);
		binding.layoutGeneratedState.setVisibility(android.view.View.GONE);
		binding.layoutIdleState.setVisibility(android.view.View.VISIBLE);
		binding.progressGenerating.setVisibility(android.view.View.GONE);
		binding.imageview65.setVisibility(android.view.View.VISIBLE);
		
		binding.textview92.setText("Play");
		binding.imageview52.setImageResource(R.drawable.icon_play_circle);
		binding.textview69.setText("READY TO SYNTHESIZE");
		binding.textview69.setTextColor(android.graphics.Color.parseColor("#94A3B8"));
		
	}
	
	
	public void _requestAudioFocus() {
		
		if (audioManager == null) return;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			if (focusRequest != null) audioManager.requestAudioFocus(focusRequest);
		} else {
			audioManager.requestAudioFocus(focusListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN);
		}
	}
	
	
	public void _toggleGeneratePlayback() {
		
		if (!isAudioGeneratedForCurrentText || audioTrack == null || lastGeneratedPcmData == null) return;
		
		int playState = audioTrack.getPlayState();
		int currentHead = audioTrack.getPlaybackHeadPosition();
		int totalFrames = lastGeneratedPcmData.length / 2;
		
		if (playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
			audioTrack.pause();
			binding.imageview52.setImageResource(R.drawable.icon_play_circle);
			binding.textview92.setText("Play");
			if (playheadAnimator != null) playheadAnimator.pause();
			
			com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).updatePlaybackState("VoxSherpa Audio", "Paused", com.CodeBySonu.VoxSherpa.system.VoxMediaController.STATE_PAUSED, false);
		} else {
			final double totalSeconds = (double) totalFrames / lastGeneratedSampleRate;
			final int _totalMin = (int)(totalSeconds / 60);
			final int _totalSec = (int)(totalSeconds % 60);
			final int w = binding.imgWaveform.getWidth() > 0 ? binding.imgWaveform.getWidth() : 800;
			
			// 🚀 FIX: Removed 'flush()' which was causing Silent Play. Added proper reset loop.
			if (currentHead >= totalFrames - 4000 || playState == android.media.AudioTrack.PLAYSTATE_STOPPED) {
				try {
					audioTrack.stop();
					audioTrack.reloadStaticData();
					audioTrack.setPlaybackHeadPosition(0);
				} catch (Exception ignored) {}
				
				binding.playheadLine.setTranslationX(0f);
				if (playheadAnimator != null) playheadAnimator.cancel();
				
				playheadAnimator = android.animation.ValueAnimator.ofFloat(0f, (float) w);
				playheadAnimator.setDuration((long) (totalSeconds * 1000));
				playheadAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
				playheadAnimator.addUpdateListener(anim -> {
					float tx = (float) anim.getAnimatedValue();
					binding.playheadLine.setTranslationX(tx);
					double elapsed = (tx / w) * totalSeconds;
					binding.tvDuration.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d",
					(int)(elapsed / 60), (int)(elapsed % 60), _totalMin, _totalSec));
				});
				
				playheadAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(android.animation.Animator animation) {
						if (binding.playheadLine.getTranslationX() >= w - 10) {
							binding.imageview52.setImageResource(R.drawable.icon_play_circle);
							binding.textview92.setText("Play");
							binding.playheadLine.setTranslationX(0f);
							binding.tvDuration.setText(String.format(java.util.Locale.US,
							"0:00 / %d:%02d", _totalMin, _totalSec));
							try {
								if (audioTrack != null) {
									audioTrack.stop();
									audioTrack.reloadStaticData();
									audioTrack.setPlaybackHeadPosition(0);
								}
							} catch (Exception ignored) {}
							
							com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification();
						}
					}
				});
			}
			
			_requestAudioFocus();
			audioTrack.play();
			binding.imageview52.setImageResource(R.drawable.icon_pause_circle);
			binding.textview92.setText("Pause");
			
			com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).updatePlaybackState("VoxSherpa Audio", "Playing...", com.CodeBySonu.VoxSherpa.system.VoxMediaController.STATE_PLAYING, false);
			
			if (playheadAnimator != null) {
				if (playheadAnimator.isPaused()) {
					playheadAnimator.resume();
				} else {
					playheadAnimator.start();
				}
			}
		}
	}
	
	
	public void _cancelGeneration() {
		if (isGenerating && !isCancelled) {
			isCancelled = true;
			isGenerating = false;
			new Thread(() -> {
				try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().cancel(); } catch (Exception ignored) {}
				try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().cancel(); } catch (Exception ignored) {}
				try { com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().cancel(); } catch (Exception ignored) {}
			}).start();
			
			// LiveStream track (Instant Silence)
			try {
				if (liveStreamTrack != null) {
					liveStreamTrack.pause();
					liveStreamTrack.flush();
					liveStreamTrack.stop();
				}
			} catch (Exception ignored) {}
			
			if (binding != null) {
				try {
					binding.textview92.setText("Canceling...");
					binding.btnGenerate.setAlpha(0.5f);
					binding.btnGenerate.setEnabled(false);
				} catch (Exception ignored) {}
			}
			
			try {
				com.CodeBySonu.VoxSherpa.system.VoxMediaController.getInstance(getContext()).hideNotification();
			} catch (Exception ignored) {}
		}
		
	}
	
}
