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
import androidx.recyclerview.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import com.CodeBySonu.VoxSherpa.databinding.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;
import com.k2fsa.sherpa.onnx.*;
import com.tom_roush.pdfbox.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.HashMap;
import java.util.regex.*;
import org.json.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import android.database.Cursor;

public class ModelsFragmentActivity extends Fragment {
	
	public final int REQ_CD_FILEPICKER = 101;
	private FirebaseDatabase _firebase = FirebaseDatabase.getInstance();
	private ModelsFragmentBinding binding;
	private static final String TAG = "ModelsFragment";
	private static final Gson GSON = new Gson();
	private List<HashMap<String, Object>> modelList = new ArrayList<>();
	private volatile boolean isLoading = false;
	private String tempOnnxPath = "";
	private String tempTokensPath = "";
	private BottomSheetDialog importDialog;
	private View dialogView;
	private int lastGeneratedSampleRate = 22050;
	private volatile com.CodeBySonu.VoxSherpa.SupertonicModelFiles.DownloadTask supertonicDownloadTask;
	private volatile com.CodeBySonu.VoxSherpa.KokoroModelFiles.DownloadTask kokoroDownloadTask;
	private final java.util.concurrent.ConcurrentHashMap<String, com.CodeBySonu.VoxSherpa.PiperModelCatalog.DownloadTask> piperDownloadTasks = new java.util.concurrent.ConcurrentHashMap<>();
	private Intent FilePicker = new Intent(Intent.ACTION_GET_CONTENT);
	private SharedPreferences sp1, sp2, sp3;
	private DatabaseReference fb = _firebase.getReference("/");
	private ChildEventListener _fb_child_listener;
	
	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater _inflater, @Nullable ViewGroup _container, @Nullable Bundle _savedInstanceState) {
		binding = ModelsFragmentBinding.inflate(_inflater, _container, false);
		initialize(_savedInstanceState, binding.getRoot());
		FirebaseApp.initializeApp(getContext());
		initializeLogic();
		return binding.getRoot();
	}
	
	private void initialize(Bundle _savedInstanceState, View _view) {
		FilePicker.setType("*/*");
		FilePicker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		sp1 = getContext().getSharedPreferences("sp1", Activity.MODE_PRIVATE);
		sp2 = getContext().getSharedPreferences("sp2", Activity.MODE_PRIVATE);
		sp3 = getContext().getSharedPreferences("sp3", Activity.MODE_PRIVATE);
		
		binding.btnFilter.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				binding.imageview8.animate().rotation(-90f).setDuration(300).start();
				com.google.android.material.bottomsheet.BottomSheetDialog sortDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(getContext());
				View dialogView = getActivity().getLayoutInflater().inflate(R.layout.sort_bottom_dialog, null);
				android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
				gd.setColor(android.graphics.Color.parseColor("#131B2D"));
				float radius = 24f * getResources().getDisplayMetrics().density;
				gd.setCornerRadii(new float[]{radius, radius, radius, radius, 0f, 0f, 0f, 0f});
				dialogView.setBackground(gd);
				sortDialog.setContentView(dialogView);
				sortDialog.setOnShowListener(dialog -> {
					com.google.android.material.bottomsheet.BottomSheetDialog d = (com.google.android.material.bottomsheet.BottomSheetDialog) dialog;
					View bottomSheetInternal = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
					if (bottomSheetInternal != null) bottomSheetInternal.setBackgroundColor(android.graphics.Color.TRANSPARENT);
				});
				
				LinearLayout layoutAll = dialogView.findViewById(R.id.layout_all_models);
				LinearLayout layoutDownload = dialogView.findViewById(R.id.layout_download);
				LinearLayout layoutInstalled = dialogView.findViewById(R.id.layout_installed);
				LinearLayout layoutNewest = dialogView.findViewById(R.id.layout_newest);
				LinearLayout layoutOldest = dialogView.findViewById(R.id.layout_oldest);
				RadioButton rbAll = dialogView.findViewById(R.id.rb_all_models);
				RadioButton rbDownload = dialogView.findViewById(R.id.rb_download);
				RadioButton rbInstalled = dialogView.findViewById(R.id.rb_installed);
				RadioButton rbNewest = dialogView.findViewById(R.id.rb_newest);
				RadioButton rbOldest = dialogView.findViewById(R.id.rb_oldest);
				
				String currentSort = sp1.getString("sort_preference", "all_models");
				String currentLanguage = sp1.getString("language_filter", "All Languages");
				if(currentSort.equals("download")) rbDownload.setChecked(true);
				else if(currentSort.equals("installed")) rbInstalled.setChecked(true);
				else if(currentSort.equals("newest")) rbNewest.setChecked(true);
				else if(currentSort.equals("oldest")) rbOldest.setChecked(true);
				else rbAll.setChecked(true);
				
				Runnable applyFilterAndSort = () -> {
					String activeSort = sp1.getString("sort_preference", "all_models");
					String activeLang = sp1.getString("language_filter", "All Languages");
					String savedData = sp1.getString("models_data", "[]");
					java.util.ArrayList<java.util.HashMap<String, Object>> masterList = new com.google.gson.Gson().fromJson(savedData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
					if (masterList == null) masterList = new java.util.ArrayList<>();
					modelList.clear(); 
					for (java.util.HashMap<String, Object> item : masterList) {
						String onnxPath = item.containsKey("onnx_path") && item.get("onnx_path") != null ? item.get("onnx_path").toString() : "";
						boolean isInstalled = !onnxPath.isEmpty();
						String itemLang = item.containsKey("language") && item.get("language") != null ? item.get("language").toString() : "";
						boolean isSupertonicModel = item.containsKey("type") && item.get("type").toString().equalsIgnoreCase(com.CodeBySonu.VoxSherpa.SupertonicModelFiles.MODEL_TYPE);
						boolean languageMatch = activeLang.equals("All Languages") || activeLang.equals(itemLang) || isSupertonicModel;
						if (languageMatch) {
							if (activeSort.equals("download")) { if (!isInstalled) modelList.add(item); } 
							else if (activeSort.equals("installed")) { if (isInstalled) modelList.add(item); } 
							else { modelList.add(item); }
						}
					}
					if (activeSort.equals("newest")) java.util.Collections.reverse(modelList);
					if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyDataSetChanged();
					_updateEmptyState();
					if (!activeLang.equals("All Languages")) binding.sortTv.setText(activeLang);
					else if (activeSort.equals("download")) binding.sortTv.setText("Download");
					else if (activeSort.equals("installed")) binding.sortTv.setText("Installed");
					else if (activeSort.equals("newest")) binding.sortTv.setText("Newest First");
					else if (activeSort.equals("oldest")) binding.sortTv.setText("Oldest First");
					else binding.sortTv.setText("All Models");
				};
				
				String[] languagesFromXml = getResources().getStringArray(R.array.language_list);
				String[] finalLanguages = new String[languagesFromXml.length + 1];
				finalLanguages[0] = "All Languages";
				System.arraycopy(languagesFromXml, 0, finalLanguages, 1, languagesFromXml.length);
				android.widget.ArrayAdapter<String> langAdapter = new android.widget.ArrayAdapter<>(getContext(), R.layout.custom_dropdown_item, R.id.tv_drop_item, finalLanguages);
				android.widget.AutoCompleteTextView dropdownLang = dialogView.findViewById(R.id.dropdown_lang);
				if(dropdownLang != null) {
					dropdownLang.setAdapter(langAdapter);
					dropdownLang.setText(currentLanguage, false);
					dropdownLang.setOnItemClickListener((parentView, view, position, id) -> {
						sp1.edit().putString("language_filter", finalLanguages[position]).apply();
						sortDialog.dismiss(); applyFilterAndSort.run();
					});
				}
				
				View.OnClickListener clickListener = v -> {
					String selected = "all_models";
					if(v.getId() == R.id.layout_download) selected = "download";
					else if(v.getId() == R.id.layout_installed) selected = "installed";
					else if(v.getId() == R.id.layout_newest) selected = "newest";
					else if(v.getId() == R.id.layout_oldest) selected = "oldest";
					sp1.edit().putString("sort_preference", selected).apply();
					sortDialog.dismiss(); applyFilterAndSort.run();
				};
				layoutAll.setOnClickListener(clickListener); layoutDownload.setOnClickListener(clickListener);
				layoutInstalled.setOnClickListener(clickListener); layoutNewest.setOnClickListener(clickListener); layoutOldest.setOnClickListener(clickListener);
				sortDialog.setOnDismissListener(dialogInterface -> binding.imageview8.animate().rotation(0f).setDuration(300).start());
				sortDialog.show();
			}
		});
		
		_fb_child_listener = new ChildEventListener() {
			@Override public void onChildAdded(DataSnapshot _param1, String _param2) {}
			@Override public void onChildChanged(DataSnapshot _param1, String _param2) {}
			@Override public void onChildMoved(DataSnapshot _param1, String _param2) {}
			@Override public void onChildRemoved(DataSnapshot _param1) {}
			@Override public void onCancelled(DatabaseError _param1) {}
		};
		fb.addChildEventListener(_fb_child_listener);
	}
	
	private void initializeLogic() {
		binding.modelsMenu.setOnClickListener(anchor -> {
			android.widget.PopupMenu menu = new android.widget.PopupMenu(requireContext(), anchor);
			menu.getMenu().add(0, 0, 0, "Generate"); menu.getMenu().add(0, 1, 1, "Manage Models");
			menu.getMenu().add(0, 2, 2, "Library"); menu.getMenu().add(0, 3, 3, "Settings");
			menu.setOnMenuItemClickListener(item -> {
				androidx.viewpager2.widget.ViewPager2 viewPager = requireActivity().findViewById(R.id.viewpager);
				viewPager.setCurrentItem(item.getItemId(), false); return true;
			}); menu.show();
		});
		String initialSort = sp1.getString("sort_preference", "all_models");
		if (initialSort.equals("download")) binding.sortTv.setText("Download");
		else if (initialSort.equals("installed")) binding.sortTv.setText("Installed");
		else if (initialSort.equals("newest")) binding.sortTv.setText("Newest First");
		else if (initialSort.equals("oldest")) binding.sortTv.setText("Oldest First");
		else binding.sortTv.setText("All Models");
		_syncMmsCatalog(); _setupDataAndStorage(); _fetchFirebaseModels(); _setupRecyclerViewAdapter(); _setupFabAndImportDialog();
	}
	
	@Override
	public void onActivityResult(int _requestCode, int _resultCode, Intent _data) {
		super.onActivityResult(_requestCode, _resultCode, _data);
		if (_resultCode == android.app.Activity.RESULT_OK && _data != null) {
			android.net.Uri uri = (_data.getClipData() != null) ? _data.getClipData().getItemAt(0).getUri() : _data.getData();
			String path = uri.toString(); String mode = sp1.getString("picking_mode", "");
			String fileName = "Unknown_File"; String fileSizeStr = "0 MB";
			android.database.Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
			if (cursor != null && cursor.moveToFirst()) {
				int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
				int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
				if (nameIndex != -1) fileName = cursor.getString(nameIndex);
				if (sizeIndex != -1) { long size = cursor.getLong(sizeIndex); fileSizeStr = String.format(java.util.Locale.US, "%.1f MB", (size / (1024.0 * 1024.0))); }
				cursor.close();
			}
			if (mode.equals("onnx")) {
				if (fileName.toLowerCase().endsWith(".onnx")) {
					tempOnnxPath = path; sp1.edit().putString("temp_onnx_name", fileName).apply(); sp1.edit().putString("temp_onnx_size", fileSizeStr).apply();
					if (dialogView != null) {
						com.google.android.material.card.MaterialCardView card = dialogView.findViewById(R.id.btn_choose_onnx);
						android.widget.TextView tvName = dialogView.findViewById(R.id.onnx_name_tv);
						card.setStrokeColor(android.graphics.Color.parseColor("#1D61FF")); tvName.setText(fileName); tvName.setTextColor(android.graphics.Color.parseColor("#1D61FF"));
					}
				} else {
					com.google.android.material.snackbar.Snackbar snack = com.google.android.material.snackbar.Snackbar.make(getActivity().findViewById(android.R.id.content), "Invalid File! Please select a .onnx model file.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG);
					snack.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
				}
			} else if (mode.equals("tokens")) {
				if (fileName.toLowerCase().endsWith(".txt")) {
					tempTokensPath = path;
					if (dialogView != null) {
						com.google.android.material.card.MaterialCardView card = dialogView.findViewById(R.id.btn_select_tokens);
						android.widget.TextView tvName = dialogView.findViewById(R.id.tokens_txt_tv);
						card.setStrokeColor(android.graphics.Color.parseColor("#48BB78")); tvName.setText(fileName); tvName.setTextColor(android.graphics.Color.parseColor("#48BB78"));
					}
				} else {
					com.google.android.material.snackbar.Snackbar snack = com.google.android.material.snackbar.Snackbar.make(getActivity().findViewById(android.R.id.content), "Invalid File! Please select the tokens.txt file.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG);
					snack.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show();
				}
			}
		}
	}
	
	@Override public void onResume() { super.onResume(); _syncMmsCatalog(); }
	@Override public void onDestroyView() { super.onDestroyView(); binding = null; }
	
	public void _updateEmptyState() {
		boolean empty = modelList.isEmpty();
		binding.recyclerviewModels.setVisibility(empty ? View.GONE : View.VISIBLE);
		binding.emptyStateView.setVisibility(empty ? View.VISIBLE : View.GONE);
		binding.modelCountTv.setText("MODELS LIST (" + modelList.size() + ")");
	}
	
	public void _setupRecyclerViewAdapter() {
		androidx.recyclerview.widget.RecyclerView.ItemAnimator animator = binding.recyclerviewModels.getItemAnimator();
		if (animator instanceof androidx.recyclerview.widget.SimpleItemAnimator) ((androidx.recyclerview.widget.SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
		
		binding.recyclerviewModels.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
			@NonNull @Override public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
				return new androidx.recyclerview.widget.RecyclerView.ViewHolder(getActivity().getLayoutInflater().inflate(R.layout.item_model, parent, false)) {};
			}
			
			@Override public void onBindViewHolder(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position, @NonNull java.util.List<Object> payloads) {
				if (payloads.isEmpty()) super.onBindViewHolder(holder, position, payloads);
				else {
					for (Object payload : payloads) {
						if (payload.equals("PROGRESS_UPDATE")) {
							int pos = holder.getAdapterPosition(); if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return;
							java.util.HashMap<String, Object> item = modelList.get(pos);
							android.widget.ProgressBar progressBar = holder.itemView.findViewById(R.id.progress_download);
							android.widget.TextView txtModelSub = holder.itemView.findViewById(R.id.txt_model_sub);
							int progress = item.containsKey("download_progress") ? Integer.parseInt(item.get("download_progress").toString()) : 0;
							progressBar.setIndeterminate(false); progressBar.setProgress(progress); txtModelSub.setText(progress + "%");
						}
					}
				}
			}
			
			@Override public void onBindViewHolder(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
				android.view.View v = holder.itemView; android.content.Context context = v.getContext();
				int pos = holder.getAdapterPosition(); if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return;
				java.util.HashMap<String, Object> item = modelList.get(pos);
				
				boolean isKokoro = item.containsKey("type") && item.get("type").toString().equalsIgnoreCase("kokoro");
				boolean isSupertonic = com.CodeBySonu.VoxSherpa.SupertonicModelFiles.isSupertonicModel(item);
				boolean isPiperArchive = com.CodeBySonu.VoxSherpa.PiperModelCatalog.isCatalogModel(item);
				
				String modelName = "Unknown Model";
				if (item.containsKey("name") && item.get("name") != null && !item.get("name").toString().trim().isEmpty()) modelName = item.get("name").toString();
				else if (item.containsKey("language") && item.containsKey("gender")) {
					modelName = item.get("language").toString() + " " + item.get("gender").toString();
					if (item.containsKey("quality")) modelName += " (" + item.get("quality").toString() + ")";
				}
				((android.widget.TextView) v.findViewById(R.id.txt_model_id)).setText(modelName);
				final String finalModelName = modelName;
				
				String lang = item.containsKey("language") ? item.get("language").toString() : "Unknown";
				String gender = item.containsKey("gender") ? item.get("gender").toString() : "Unknown";
				String size = item.containsKey("size") ? item.get("size").toString() : "0 MB";
				String currentOnnx = item.containsKey("onnx_path") && item.get("onnx_path") != null ? item.get("onnx_path").toString() : "";
				String currentModelDir = item.containsKey("model_dir") && item.get("model_dir") != null ? item.get("model_dir").toString() : "";
				
				boolean isDownloaded = isSupertonic ? com.CodeBySonu.VoxSherpa.SupertonicModelFiles.isInstalled(new java.io.File(currentModelDir)) : isKokoro ? com.CodeBySonu.VoxSherpa.KokoroModelFiles.isInstalled(new java.io.File(currentModelDir)) : isPiperArchive ? com.CodeBySonu.VoxSherpa.PiperModelCatalog.isInstalled(new java.io.File(currentModelDir)) : !currentOnnx.isEmpty();
				boolean isDownloading = item.containsKey("is_downloading") && item.get("is_downloading").toString().equals("true");
				int progress = item.containsKey("download_progress") ? Integer.parseInt(item.get("download_progress").toString()) : 0;
				
				String activeOnnx = sp1.getString("active_model", ""); String activeModelType = sp1.getString("active_model_type", ""); String activeSupertonicDir = sp1.getString("active_supertonic_dir", "");
				
				// FIXED LOGIC: Simplified check for Active Model (Green Tick & Remove Button)
				boolean isActiveModel = false;
				if (isSupertonic) {
					isActiveModel = !currentModelDir.isEmpty() && currentModelDir.equals(activeSupertonicDir) && activeModelType.equalsIgnoreCase(com.CodeBySonu.VoxSherpa.SupertonicModelFiles.MODEL_TYPE);
				} else {
					isActiveModel = !currentOnnx.isEmpty() && currentOnnx.equals(activeOnnx);
				}
				
				android.widget.ImageView selectIv = v.findViewById(R.id.select_iv); android.widget.TextView useRemoveTv = v.findViewById(R.id.use_remove_tv);
				com.google.android.material.card.MaterialCardView btnUseVoice = v.findViewById(R.id.btn_use_voice); com.google.android.material.card.MaterialCardView btnDelete = v.findViewById(R.id.btn_delete);
				android.widget.TextView txtModelSub = v.findViewById(R.id.txt_model_sub); android.widget.ProgressBar progressBar = v.findViewById(R.id.progress_download);
				
				if (isDownloaded) {
					progressBar.setVisibility(android.view.View.GONE); btnDelete.setVisibility(android.view.View.VISIBLE);
					txtModelSub.setText(isSupertonic ? "31 languages • 10 speakers • " + size : lang + " • " + gender + " • " + size);
					if (isActiveModel) { selectIv.setVisibility(android.view.View.VISIBLE); useRemoveTv.setText("Remove"); btnUseVoice.setCardBackgroundColor(android.graphics.Color.parseColor("#FF4B4B")); }
					else { selectIv.setVisibility(android.view.View.GONE); useRemoveTv.setText("Use Voice"); btnUseVoice.setCardBackgroundColor(android.graphics.Color.parseColor("#1D61FF")); }
				} else if (isDownloading) {
					progressBar.setVisibility(android.view.View.VISIBLE); btnDelete.setVisibility(android.view.View.GONE);
					progressBar.setIndeterminate(false); progressBar.setProgress(progress); txtModelSub.setText(progress + "%");
					selectIv.setVisibility(android.view.View.GONE); useRemoveTv.setText("Cancel"); btnUseVoice.setCardBackgroundColor(android.graphics.Color.parseColor("#718096"));
				} else {
					progressBar.setVisibility(android.view.View.GONE); btnDelete.setVisibility(android.view.View.GONE);
					txtModelSub.setText(lang + " • " + gender + " • " + size); selectIv.setVisibility(android.view.View.GONE); useRemoveTv.setText("Download"); btnUseVoice.setCardBackgroundColor(android.graphics.Color.parseColor("#1D61FF"));
				}
				
				final String capturedOnnx = currentOnnx; final String capturedTokens = item.containsKey("tokens_path") && item.get("tokens_path") != null ? item.get("tokens_path").toString() : "";
				final String capturedVoicesBin = item.containsKey("voices_bin_path") && item.get("voices_bin_path") != null ? item.get("voices_bin_path").toString() : "";
				final String capturedModelType = isSupertonic ? "supertonic" : (isKokoro ? "kokoro" : (item.containsKey("type") && item.get("type").toString().equals("MMS") ? "mms" : "vits"));
				final String capturedModelDir = currentModelDir;
				
				if (isDownloading && !isSupertonic && !item.containsKey("handler_running")) {
					item.put("handler_running", "true");
					long onnxId = item.containsKey("onnx_download_id") ? Long.parseLong(item.get("onnx_download_id").toString()) : -1;
					if (onnxId != -1) {
						android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
						Runnable progressRunnable = new Runnable() {
							@Override public void run() {
								int targetPos = -1; for (int i = 0; i < modelList.size(); i++) { java.util.HashMap<String, Object> m = modelList.get(i); if (m.containsKey("onnx_download_id") && m.get("onnx_download_id").toString().equals(String.valueOf(onnxId))) { targetPos = i; break; } }
								if (targetPos == -1) return; java.util.HashMap<String, Object> currentItem = modelList.get(targetPos);
								if (!currentItem.containsKey("is_downloading") || currentItem.get("is_downloading").toString().equals("false")) { currentItem.remove("handler_running"); return; }
								android.app.DownloadManager dm = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
								android.app.DownloadManager.Query q = new android.app.DownloadManager.Query(); q.setFilterById(onnxId);
								android.database.Cursor cursor = dm.query(q);
								if (cursor != null && cursor.moveToFirst()) {
									int statusIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS); int downloadedIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR); int totalIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
									if (statusIndex >= 0) {
										int status = cursor.getInt(statusIndex); long bytesDownloaded = downloadedIndex >= 0 ? cursor.getLong(downloadedIndex) : 0; long bytesTotal = totalIndex >= 0 ? cursor.getLong(totalIndex) : -1;
										if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
											String targetOnnxName = currentItem.containsKey("target_onnx_name") ? currentItem.get("target_onnx_name").toString() : "";
											String targetTokensName = currentItem.containsKey("target_tokens_name") ? currentItem.get("target_tokens_name").toString() : "";
											String targetVoicesName = currentItem.containsKey("target_voices_name") ? currentItem.get("target_voices_name").toString() : "";
											boolean isKokoroType = currentItem.containsKey("type") && currentItem.get("type").toString().equalsIgnoreCase("kokoro");
											currentItem.put("is_downloading", "false"); currentItem.put("download_progress", "100"); currentItem.remove("handler_running");
											final int finalTargetPos = targetPos;
											new Thread(() -> {
												try {
													java.io.File internalModelsDir = new java.io.File(context.getFilesDir(), "secure_models"); if (!internalModelsDir.exists()) internalModelsDir.mkdirs();
													java.io.File sourceOnnx = new java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), targetOnnxName); java.io.File destinationOnnx = new java.io.File(internalModelsDir, targetOnnxName);
													if (sourceOnnx.exists()) { try (java.io.InputStream in = new java.io.FileInputStream(sourceOnnx); java.io.OutputStream out = new java.io.FileOutputStream(destinationOnnx)) { byte[] buf = new byte[16384]; int len; while ((len = in.read(buf)) > 0) out.write(buf, 0, len); } sourceOnnx.delete(); }
													java.io.File sourceTokens = new java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), targetTokensName); java.io.File destinationTokens = new java.io.File(internalModelsDir, targetTokensName);
													if (sourceTokens.exists()) { try (java.io.InputStream in = new java.io.FileInputStream(sourceTokens); java.io.OutputStream out = new java.io.FileOutputStream(destinationTokens)) { byte[] buf = new byte[16384]; int len; while ((len = in.read(buf)) > 0) out.write(buf, 0, len); } sourceTokens.delete(); }
													currentItem.put("onnx_path", destinationOnnx.getAbsolutePath()); currentItem.put("tokens_path", destinationTokens.getAbsolutePath());
													if (isKokoroType && !targetVoicesName.isEmpty()) {
														java.io.File sourceVoices = new java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), targetVoicesName); java.io.File destinationVoices = new java.io.File(internalModelsDir, targetVoicesName);
														if (sourceVoices.exists()) { try (java.io.InputStream in = new java.io.FileInputStream(sourceVoices); java.io.OutputStream out = new java.io.FileOutputStream(destinationVoices)) { byte[] buf = new byte[16384]; int len; while ((len = in.read(buf)) > 0) out.write(buf, 0, len); } sourceVoices.delete(); }
														currentItem.put("voices_bin_path", destinationVoices.getAbsolutePath());
													}
													sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply(); com.CodeBySonu.VoxSherpa.system.TtsDefaultHelper.syncDefaultVoices(context);
													if (getActivity() != null) getActivity().runOnUiThread(() -> { if (isAdded() && binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(finalTargetPos); });
												} catch (Exception e) {
													currentItem.put("is_downloading", "false"); currentItem.put("download_progress", "0"); currentItem.remove("download_status"); currentItem.remove("handler_running");
													sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply();
													if (getActivity() != null) getActivity().runOnUiThread(() -> { if (!isAdded()) return; if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(finalTargetPos); android.view.View root = getView(); if (root != null) com.google.android.material.snackbar.Snackbar.make(root, "Model installation failed: " + e.getMessage(), com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show(); });
												}
											}).start();
											cursor.close(); return;
										} else if (status == android.app.DownloadManager.STATUS_FAILED) {
											int reasonIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_REASON); int reason = reasonIndex >= 0 ? cursor.getInt(reasonIndex) : -1;
											currentItem.put("is_downloading", "false"); currentItem.put("download_progress", "0"); currentItem.remove("download_status"); currentItem.remove("handler_running");
											final int finalTargetPos = targetPos;
											if (getActivity() != null) getActivity().runOnUiThread(() -> { if (!isAdded()) return; if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(finalTargetPos); android.view.View root = getView(); if (root != null) com.google.android.material.snackbar.Snackbar.make(root, "Download Failed! Code: " + reason, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show(); });
											cursor.close(); return;
										} else if (status == android.app.DownloadManager.STATUS_RUNNING || status == android.app.DownloadManager.STATUS_PAUSED) {
											if (bytesTotal > 0) { int prog = (int) ((bytesDownloaded * 100) / bytesTotal); if (prog >= 100) prog = 99; currentItem.put("download_progress", String.valueOf(prog)); }
											final int finalTargetPos = targetPos;
											if (getActivity() != null) getActivity().runOnUiThread(() -> { if (isAdded() && binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(finalTargetPos, "PROGRESS_UPDATE"); });
										}
									}
									cursor.close();
								}
								handler.postDelayed(this, 500);
							}
						};
						handler.post(progressRunnable);
					}
				}
				
				btnUseVoice.setOnClickListener(view -> {
					if (isLoading) return;
					int clickedPos = holder.getAdapterPosition(); if (clickedPos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return;
					if (com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().isBusy() || com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().isBusy() || com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().isBusy()) {
						android.widget.Toast.makeText(context, "Please wait, audio generation in progress...", android.widget.Toast.LENGTH_SHORT).show();
						try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().cancel(); } catch (Throwable ignored) {} try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().cancel(); } catch (Throwable ignored) {} try { com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().cancel(); } catch (Throwable ignored) {} return;
					}
					if (isDownloading) {
						if (isSupertonic) { if (supertonicDownloadTask != null) supertonicDownloadTask.cancel(); supertonicDownloadTask = null; } 
						else if (isKokoro) { if (kokoroDownloadTask != null) kokoroDownloadTask.cancel(); kokoroDownloadTask = null; } 
						else if (isPiperArchive) { String url = String.valueOf(item.get("model_url")); if (piperDownloadTasks.remove(url) != null) piperDownloadTasks.remove(url).cancel(); } 
						else try {
							long onnxId = Long.parseLong(item.get("onnx_download_id").toString()); long tokensId = Long.parseLong(item.get("tokens_download_id").toString());
							android.app.DownloadManager dm = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE); dm.remove(onnxId, tokensId);
							if (isKokoro && item.containsKey("voices_bin_download_id")) dm.remove(Long.parseLong(item.get("voices_bin_download_id").toString()));
						} catch (Exception e) {}
						item.put("is_downloading", "false"); item.put("download_progress", "0"); item.remove("download_status");
						if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(clickedPos);
					} else if (!isDownloaded) {
						String onnxUrl = item.containsKey("model_url") ? item.get("model_url").toString() : ""; String tokensUrl = item.containsKey("tokens_url") ? item.get("tokens_url").toString() : ""; String voicesBinUrl = item.containsKey("voices_bin_url") ? item.get("voices_bin_url").toString() : "";
						
						if (isSupertonic) {
							com.CodeBySonu.VoxSherpa.SupertonicModelFiles.DownloadTask task = com.CodeBySonu.VoxSherpa.SupertonicModelFiles.newDownloadTask(); supertonicDownloadTask = task;
							item.put("model_dir", com.CodeBySonu.VoxSherpa.SupertonicModelFiles.getModelDirectory(context).getAbsolutePath()); item.put("is_downloading", "true"); item.put("download_progress", "0");
							sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply(); if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(clickedPos);
							android.os.Handler downloadHandler = new android.os.Handler(android.os.Looper.getMainLooper());
							new Thread(() -> {
								try {
									java.io.File modelDir = task.download(context, percent -> downloadHandler.post(() -> { if (task.isCancelled()) return; item.put("download_progress", String.valueOf(percent)); if (binding != null && binding.recyclerviewModels.getAdapter() != null) { int itemPosition = modelList.indexOf(item); if (itemPosition >= 0) binding.recyclerviewModels.getAdapter().notifyItemChanged(itemPosition, "PROGRESS_UPDATE"); } }));
									downloadHandler.post(() -> {
										if (supertonicDownloadTask == task) supertonicDownloadTask = null;
										if (!task.isCancelled()) {
											com.CodeBySonu.VoxSherpa.SupertonicModelFiles.populateInstalledPaths(item, modelDir); item.put("is_downloading", "false"); item.put("download_progress", "100"); item.remove("download_status");
											sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply(); com.CodeBySonu.VoxSherpa.system.TtsDefaultHelper.syncDefaultVoices(context);
											int itemPosition = modelList.indexOf(item); if (isAdded() && itemPosition >= 0 && binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(itemPosition);
										}
									});
								} catch (Exception error) {
									downloadHandler.post(() -> {
										if (supertonicDownloadTask == task) supertonicDownloadTask = null;
										if (!task.isCancelled()) {
											item.put("is_downloading", "false"); item.put("download_progress", "0"); item.remove("download_status"); sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply();
											int itemPosition = modelList.indexOf(item); if (isAdded() && itemPosition >= 0 && binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(itemPosition);
											android.view.View root = getView(); if (isAdded() && root != null) com.google.android.material.snackbar.Snackbar.make(root, "Supertonic download failed: " + error.getMessage(), com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
										}
									});
								}
							}, "supertonic-model-download").start(); return;
						}

						if (isKokoro) {
							com.CodeBySonu.VoxSherpa.KokoroModelFiles.DownloadTask task = com.CodeBySonu.VoxSherpa.KokoroModelFiles.newDownloadTask(); kokoroDownloadTask = task;
							item.put("model_dir", com.CodeBySonu.VoxSherpa.KokoroModelFiles.getModelDirectory(context).getAbsolutePath()); item.put("is_downloading", "true"); item.put("download_progress", "0");
							sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply(); if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(clickedPos);
							android.os.Handler downloadHandler = new android.os.Handler(android.os.Looper.getMainLooper());
							new Thread(() -> {
								try {
									java.io.File modelDir = task.download(context, percent -> downloadHandler.post(() -> { if (task.isCancelled()) return; item.put("download_progress", String.valueOf(percent)); if (binding != null && binding.recyclerviewModels.getAdapter() != null) { int itemPosition = modelList.indexOf(item); if (itemPosition >= 0) binding.recyclerviewModels.getAdapter().notifyItemChanged(itemPosition, "PROGRESS_UPDATE"); } }));
									downloadHandler.post(() -> {
										if (kokoroDownloadTask == task) kokoroDownloadTask = null;
										if (!task.isCancelled()) {
											com.CodeBySonu.VoxSherpa.KokoroModelFiles.populateInstalledPaths(item, modelDir); item.put("is_downloading", "false"); item.put("download_progress", "100"); item.remove("download_status");
											sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply(); com.CodeBySonu.VoxSherpa.system.TtsDefaultHelper.syncDefaultVoices(context);
											int itemPosition = modelList.indexOf(item); if (isAdded() && itemPosition >= 0 && binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(itemPosition);
										}
									});
								} catch (Exception error) {
									downloadHandler.post(() -> {
										if (kokoroDownloadTask == task) kokoroDownloadTask = null;
										if (!task.isCancelled()) {
											item.put("is_downloading", "false"); item.put("download_progress", "0"); item.remove("download_status"); sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply();
											int itemPosition = modelList.indexOf(item); if (isAdded() && itemPosition >= 0 && binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(itemPosition);
											android.view.View root = getView(); if (isAdded() && root != null) com.google.android.material.snackbar.Snackbar.make(root, "Kokoro download failed: " + error.getMessage(), com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
										}
									});
								}
							}, "kokoro-model-download").start(); return;
						}

						if (isPiperArchive) {
							com.CodeBySonu.VoxSherpa.PiperModelCatalog.DownloadTask task = com.CodeBySonu.VoxSherpa.PiperModelCatalog.newDownloadTask(); String piperUrl = String.valueOf(item.get("model_url")); piperDownloadTasks.put(piperUrl, task);
							item.put("model_dir", com.CodeBySonu.VoxSherpa.PiperModelCatalog.getModelDirectory(context, item).getAbsolutePath()); item.put("is_downloading", "true"); item.put("download_progress", "0");
							sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply(); if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(clickedPos);
							android.os.Handler downloadHandler = new android.os.Handler(android.os.Looper.getMainLooper());
							new Thread(() -> {
								try {
									java.io.File modelDir = task.download(context, item, percent -> downloadHandler.post(() -> { if (task.isCancelled()) return; item.put("download_progress", String.valueOf(percent)); if (binding != null && binding.recyclerviewModels.getAdapter() != null) { int itemPosition = modelList.indexOf(item); if (itemPosition >= 0) binding.recyclerviewModels.getAdapter().notifyItemChanged(itemPosition, "PROGRESS_UPDATE"); } }));
									downloadHandler.post(() -> {
										piperDownloadTasks.remove(piperUrl, task);
										if (!task.isCancelled()) {
											com.CodeBySonu.VoxSherpa.PiperModelCatalog.populateInstalledPaths(item, modelDir); item.put("is_downloading", "false"); item.put("download_progress", "100"); item.remove("download_status");
											sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply(); com.CodeBySonu.VoxSherpa.system.TtsDefaultHelper.syncDefaultVoices(context);
											int itemPosition = modelList.indexOf(item); if (isAdded() && binding != null && itemPosition >= 0 && binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(itemPosition);
										}
									});
								} catch (Exception error) {
									downloadHandler.post(() -> {
										piperDownloadTasks.remove(piperUrl, task);
										if (!task.isCancelled()) {
											item.put("is_downloading", "false"); item.put("download_progress", "0"); item.remove("download_status"); sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply();
											int itemPosition = modelList.indexOf(item); if (isAdded() && binding != null && itemPosition >= 0 && binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(itemPosition);
											android.view.View root = getView(); if (isAdded() && root != null) com.google.android.material.snackbar.Snackbar.make(root, "Piper download failed: " + error.getMessage(), com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
										}
									});
								}
							}, "piper-model-download").start(); return;
						}
						
						if (onnxUrl.isEmpty() || tokensUrl.isEmpty() || (isKokoro && voicesBinUrl.isEmpty())) {
							com.google.android.material.snackbar.Snackbar.make(v, "Unable to download. Required links are missing.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show(); return;
						}
						try {
							android.app.DownloadManager dm = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
							android.app.DownloadManager.Request reqOnnx = new android.app.DownloadManager.Request(android.net.Uri.parse(onnxUrl));
							String onnxFileName = "model_" + System.currentTimeMillis() + ".onnx"; reqOnnx.setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, onnxFileName); reqOnnx.setTitle("Downloading Voice Model"); reqOnnx.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE); long onnxId = dm.enqueue(reqOnnx);
							android.app.DownloadManager.Request reqTokens = new android.app.DownloadManager.Request(android.net.Uri.parse(tokensUrl));
							String tokensFileName = "tokens_" + System.currentTimeMillis() + ".txt"; reqTokens.setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, tokensFileName); reqTokens.setTitle("Downloading Tokens"); long tokensId = dm.enqueue(reqTokens);
							long voicesBinId = -1; String voicesBinFileName = "";
							if (isKokoro) { android.app.DownloadManager.Request reqVoices = new android.app.DownloadManager.Request(android.net.Uri.parse(voicesBinUrl)); voicesBinFileName = "voices_" + System.currentTimeMillis() + ".bin"; reqVoices.setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, voicesBinFileName); reqVoices.setTitle("Downloading Voices Library"); voicesBinId = dm.enqueue(reqVoices); item.put("voices_bin_download_id", String.valueOf(voicesBinId)); }
							item.put("target_onnx_name", onnxFileName); item.put("target_tokens_name", tokensFileName); if (isKokoro) item.put("target_voices_name", voicesBinFileName);
							item.put("is_downloading", "true"); item.put("download_progress", "0"); item.put("onnx_download_id", String.valueOf(onnxId)); item.put("tokens_download_id", String.valueOf(tokensId));
							if (getActivity() != null) getActivity().runOnUiThread(() -> { if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyItemChanged(clickedPos); });
						} catch (IllegalArgumentException e) { com.google.android.material.snackbar.Snackbar.make(v, "Unable to initiate download. Link is invalid.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show(); } 
						catch (Exception e) { com.google.android.material.snackbar.Snackbar.make(v, "An unexpected error occurred.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show(); }
					} else {
						if (capturedOnnx.equals(activeOnnx)) {
							isLoading = true; btnUseVoice.setEnabled(false); btnUseVoice.setAlpha(0.5f); useRemoveTv.setText("Loading...");
							try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().destroy(); try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().destroy(); } catch (Throwable ignoredKokoro) {} try { com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().destroy(); } catch (Throwable ignoredSupertonic) {} } catch (Throwable ignored) {}
							sp1.edit().putString("active_model", "").putString("active_tokens", "").putString("active_model_name", "").putString("active_model_type", "").putString("active_voices_bin", "").putString("active_language", "").putString("active_supertonic_dir", "").apply();
							isLoading = false; btnUseVoice.setEnabled(true); btnUseVoice.setAlpha(1.0f); if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyDataSetChanged(); return;
						}
						if (isKokoro) {
							new com.google.android.material.dialog.MaterialAlertDialogBuilder(context).setTitle("Studio Quality Voice").setMessage("Kokoro is a high-fidelity neural model. Due to its complex architecture, synthesis may take longer than standard voices.").setPositiveButton("I Understand", (dialog, which) -> proceedToLoadModel(holder, btnUseVoice, useRemoveTv, capturedOnnx, capturedTokens, capturedVoicesBin, capturedModelType, capturedModelDir, finalModelName, lang)).setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss()).setCancelable(true).show();
						} else { proceedToLoadModel(holder, btnUseVoice, useRemoveTv, capturedOnnx, capturedTokens, capturedVoicesBin, capturedModelType, capturedModelDir, finalModelName, lang); }
					}
				});
				
				v.findViewById(R.id.btn_delete).setOnClickListener(view -> {
					int safePos = holder.getAdapterPosition(); if (safePos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return;
					java.util.HashMap<String, Object> itemToDelete = modelList.get(safePos);
					String onnxToDelete = itemToDelete.containsKey("onnx_path") && itemToDelete.get("onnx_path") != null ? itemToDelete.get("onnx_path").toString() : ""; String tokensToDelete = itemToDelete.containsKey("tokens_path") && itemToDelete.get("tokens_path") != null ? itemToDelete.get("tokens_path").toString() : ""; String voicesToDelete = itemToDelete.containsKey("voices_bin_path") && itemToDelete.get("voices_bin_path") != null ? itemToDelete.get("voices_bin_path").toString() : "";
					if (onnxToDelete.equals(sp1.getString("active_model", ""))) {
						try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().destroy(); try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().destroy(); } catch (Throwable ignored) {} try { com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().destroy(); } catch (Throwable ignored) {} } catch (Throwable ignored) {}
						sp1.edit().putString("active_model", "").putString("active_tokens", "").putString("active_model_name", "").putString("active_model_type", "").putString("active_voices_bin", "").putString("active_language", "").putString("active_supertonic_dir", "").apply();
					}
					if (isSupertonic) com.CodeBySonu.VoxSherpa.SupertonicModelFiles.deleteInstalledModel(itemToDelete);
					else if (isKokoro) com.CodeBySonu.VoxSherpa.KokoroModelFiles.deleteInstalledModel(context, itemToDelete);
					else if (isPiperArchive) com.CodeBySonu.VoxSherpa.PiperModelCatalog.deleteInstalledModel(itemToDelete);
					else { if (!onnxToDelete.isEmpty()) new java.io.File(onnxToDelete).delete(); if (!tokensToDelete.isEmpty()) new java.io.File(tokensToDelete).delete(); if (!voicesToDelete.isEmpty()) new java.io.File(voicesToDelete).delete(); }
					String allData = sp1.getString("models_data", "[]"); java.util.ArrayList<java.util.HashMap<String, Object>> mList = new com.google.gson.Gson().fromJson(allData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
					if (mList != null) {
						String nameToDelete = itemToDelete.containsKey("name") ? itemToDelete.get("name").toString() : "";
						for (int i = 0; i < mList.size(); i++) {
							String mName = mList.get(i).containsKey("name") ? mList.get(i).get("name").toString() : "";
							if (mName.equals(nameToDelete)) {
								if (itemToDelete.containsKey("model_url")) { mList.get(i).remove("onnx_path"); mList.get(i).remove("tokens_path"); mList.get(i).remove("voices_bin_path"); mList.get(i).remove("model_dir"); mList.get(i).remove("model_config_path"); mList.get(i).remove("espeak_data_path"); mList.get(i).remove("text_encoder_path"); mList.get(i).remove("vector_estimator_path"); mList.get(i).remove("vocoder_path"); mList.get(i).remove("unicode_indexer_path"); } 
								else mList.remove(i); break;
							}
						} sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(mList)).apply();
					}
					_applyFilterAndSort();
				});
			}
			
			private void proceedToLoadModel(androidx.recyclerview.widget.RecyclerView.ViewHolder holder, com.google.android.material.card.MaterialCardView btnUseVoice, android.widget.TextView useRemoveTv, String capturedOnnx, String capturedTokens, String capturedVoicesBin, String capturedModelType, String capturedModelDir, String finalModelName, String capturedLanguage) { 
				boolean isKokoroType = capturedModelType.equalsIgnoreCase("kokoro"); boolean isSupertonicType = capturedModelType.equals("supertonic"); boolean isMmsType = capturedModelType.equals("mms");
				isLoading = true; btnUseVoice.setEnabled(false); btnUseVoice.setAlpha(0.5f); useRemoveTv.setText("Loading...");
				new Thread(() -> {
					try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().cancel(); } catch (Throwable ignored) {} try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().cancel(); } catch (Throwable ignored) {} try { com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().cancel(); } catch (Throwable ignored) {}
					try { Thread.sleep(200); } catch (Exception ignored) {}
					try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().destroy(); } catch (Throwable ignored) {} try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().destroy(); } catch (Throwable ignored) {} try { com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().destroy(); } catch (Throwable ignored) {}
					String result = "Error"; int totalSpeakers = 1;
					try {
						if (isKokoroType) {
							com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().setActiveSpeakerId(sp1.getInt("active_kokoro_speaker", 31));
							result = com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().loadModel(holder.itemView.getContext(), capturedOnnx, capturedTokens, capturedVoicesBin);
							if ("Success".equals(result)) totalSpeakers = com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().getNumSpeakers();
						} else if (isSupertonicType) {
							result = com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().loadModel(holder.itemView.getContext(), capturedModelDir, "en");
							if ("Success".equals(result)) { totalSpeakers = com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().getNumSpeakers(); com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstance().setActiveSpeakerId(sp1.getInt("active_supertonic_speaker", 0)); }
						} else {
							result = com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().loadModel(holder.itemView.getContext(), capturedOnnx, capturedTokens, capturedLanguage, isMmsType);
							if ("Success".equals(result)) totalSpeakers = com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().getNumSpeakers();
						}
					} catch (Throwable t) { result = "Error: Invalid or corrupt model."; }
					final String finalResult = result; final int finalTotalSpeakers = totalSpeakers;
					if (getActivity() != null) {
						getActivity().runOnUiThread(() -> {
							if (!isAdded()) return; android.view.View root = getView();
							try {
								if ("Success".equals(finalResult)) {
									sp1.edit().putString("active_model", capturedOnnx).putString("active_tokens", capturedTokens).putString("active_model_name", finalModelName).putString("active_model_type", capturedModelType).putString("active_voices_bin", capturedVoicesBin).putString("active_language", isSupertonicType ? "en" : capturedLanguage).putString("active_supertonic_dir", isSupertonicType ? capturedModelDir : "").putInt("active_speaker_count", finalTotalSpeakers).apply();
								} else { if (root != null) com.google.android.material.snackbar.Snackbar.make(root, "Failed to load model.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B")).setTextColor(android.graphics.Color.WHITE).show(); }
							} finally { isLoading = false; btnUseVoice.setEnabled(true); btnUseVoice.setAlpha(1.0f); if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyDataSetChanged(); }
						});
					}
				}).start();
			}
			
			@Override public int getItemCount() { return modelList.size(); }
		});
	}
	
	public void _setupFabAndImportDialog() {
		binding.fabAddModel.setOnClickListener(v -> {
			importDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(getContext()); dialogView = getActivity().getLayoutInflater().inflate(R.layout.dialog_import_model, null); importDialog.setContentView(dialogView);
			importDialog.setOnShowListener(dialogInterface -> { View bottomSheet = importDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet); if (bottomSheet != null) bottomSheet.setBackgroundColor(Color.TRANSPARENT); });
			tempOnnxPath = ""; tempTokensPath = ""; sp1.edit().remove("temp_onnx_name").remove("temp_onnx_size").apply();
			ArrayAdapter<String> langAdapter = new ArrayAdapter<>(getContext(), R.layout.custom_dropdown_item, R.id.tv_drop_item, getResources().getStringArray(R.array.language_list));
			AutoCompleteTextView dropdownLang = dialogView.findViewById(R.id.dropdown_lang); if(dropdownLang != null) dropdownLang.setAdapter(langAdapter);
			ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(getContext(), R.layout.custom_dropdown_item, R.id.tv_drop_item, new String[]{"Male", "Female", "Neutral"});
			AutoCompleteTextView dropdownGender = dialogView.findViewById(R.id.dropdown_gender); if(dropdownGender != null) dropdownGender.setAdapter(genderAdapter);
			dialogView.findViewById(R.id.btn_close).setOnClickListener(view -> importDialog.dismiss());
			dialogView.findViewById(R.id.btn_choose_onnx).setOnClickListener(view -> { sp1.edit().putString("picking_mode", "onnx").apply(); FilePicker.setType("*/*"); startActivityForResult(FilePicker, REQ_CD_FILEPICKER); });
			dialogView.findViewById(R.id.btn_select_tokens).setOnClickListener(view -> { sp1.edit().putString("picking_mode", "tokens").apply(); FilePicker.setType("text/plain"); startActivityForResult(FilePicker, REQ_CD_FILEPICKER); });
			dialogView.findViewById(R.id.btn_import_to_library).setOnClickListener(view -> {
				String selectedLang = dropdownLang != null ? dropdownLang.getText().toString().trim() : ""; String selectedGender = dropdownGender != null ? dropdownGender.getText().toString().trim() : ""; String errorMessage = "";
				if (tempOnnxPath.isEmpty()) errorMessage = "Please add an .onnx model file!"; else if (tempTokensPath.isEmpty()) errorMessage = "Please add a tokens.txt file!"; else if (selectedLang.isEmpty()) errorMessage = "Please select a Language!"; else if (selectedGender.isEmpty()) errorMessage = "Please select a Gender!";
				if (!errorMessage.isEmpty()) { Snackbar snackbar = Snackbar.make(dialogView, errorMessage, Snackbar.LENGTH_INDEFINITE).setBackgroundTint(Color.parseColor("#FF4B4B")).setTextColor(Color.WHITE).setActionTextColor(Color.WHITE); snackbar.setAction("✕", v1 -> snackbar.dismiss()); snackbar.show(); return; }
				String newModelName = sp1.getString("temp_onnx_name", "Unknown Model"); boolean isDuplicate = false;
				for (HashMap<String, Object> model : modelList) { if (model.containsKey("name") && newModelName.equals(model.get("name").toString())) { isDuplicate = true; break; } }
				if (isDuplicate) { Snackbar snackbar = Snackbar.make(dialogView, "This model is already in your library", Snackbar.LENGTH_INDEFINITE).setBackgroundTint(Color.parseColor("#FF4B4B")).setTextColor(Color.WHITE).setActionTextColor(Color.WHITE); snackbar.setAction("✕", v1 -> snackbar.dismiss()); snackbar.show(); return; }
				view.setEnabled(false); view.setAlpha(0.5f); final Context ctx = getContext().getApplicationContext();
				final String finalModelName = newModelName; final String finalLang = selectedLang; final String finalGender = selectedGender; final String finalOnnxUri = tempOnnxPath; final String finalTokensUri = tempTokensPath; final String finalSize = sp1.getString("temp_onnx_size", "Unknown Size");
				new Thread(() -> {
					File internalOnnx = null; File internalTokens = null; boolean copySuccess = false;
					try {
						File modelsDir = new File(ctx.getFilesDir(), "PiperModels"); if (!modelsDir.exists()) modelsDir.mkdirs();
						String safeName = finalModelName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis(); internalOnnx = new File(modelsDir, safeName + ".onnx"); internalTokens = new File(modelsDir, safeName + ".txt");
						try (InputStream is1 = ctx.getContentResolver().openInputStream(android.net.Uri.parse(finalOnnxUri)); FileOutputStream os1 = new FileOutputStream(internalOnnx)) { if (is1 == null) throw new Exception("ONNX stream failed"); byte[] buf = new byte[16384]; int len; while ((len = is1.read(buf)) != -1) os1.write(buf, 0, len); }
						try (InputStream is2 = ctx.getContentResolver().openInputStream(android.net.Uri.parse(finalTokensUri)); FileOutputStream os2 = new FileOutputStream(internalTokens)) { if (is2 == null) throw new Exception("Tokens stream failed"); byte[] buf = new byte[16384]; int len; while ((len = is2.read(buf)) != -1) os2.write(buf, 0, len); }
						copySuccess = true;
					} catch (Exception e) {
						if (internalOnnx != null && internalOnnx.exists()) internalOnnx.delete(); if (internalTokens != null && internalTokens.exists()) internalTokens.delete();
						if (getActivity() != null) getActivity().runOnUiThread(() -> { view.setEnabled(true); view.setAlpha(1.0f); Snackbar snackbar = Snackbar.make(dialogView, "Failed to import files.", Snackbar.LENGTH_INDEFINITE).setBackgroundTint(Color.parseColor("#FF4B4B")).setTextColor(Color.WHITE).setActionTextColor(Color.WHITE); snackbar.setAction("✕", v1 -> snackbar.dismiss()); snackbar.show(); });
					}
					if (copySuccess) {
						final String savedOnnxPath = internalOnnx.getAbsolutePath(); final String savedTokensPath = internalTokens.getAbsolutePath();
						if (getActivity() != null) getActivity().runOnUiThread(() -> {
							HashMap<String, Object> newModel = new HashMap<>(); newModel.put("name", finalModelName); newModel.put("onnx_path", savedOnnxPath); newModel.put("tokens_path", savedTokensPath); newModel.put("size", finalSize); newModel.put("language", finalLang); newModel.put("gender", finalGender);
							modelList.add(newModel); sp1.edit().putString("models_data", new Gson().toJson(modelList)).apply(); tempOnnxPath = ""; tempTokensPath = ""; importDialog.dismiss();
							if (binding.recyclerviewModels.getAdapter() != null) { binding.recyclerviewModels.getAdapter().notifyItemInserted(modelList.size() - 1); _updateEmptyState(); } else binding.recyclerviewModels.setAdapter(binding.recyclerviewModels.getAdapter()); 
						});
					}
				}).start();
			});
			importDialog.show();
		});
	}
	
	public void _applyFilterAndSort() {
		String savedData = sp1.getString("models_data", "[]"); java.util.ArrayList<java.util.HashMap<String, Object>> masterList = new com.google.gson.Gson().fromJson(savedData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
		if (masterList == null) masterList = new java.util.ArrayList<>(); String currentSort = sp1.getString("sort_preference", "all_models"); String currentLanguage = sp1.getString("language_filter", "All Languages");
		modelList.clear();
		for (java.util.HashMap<String, Object> item : masterList) {
			String onnxPath = item.containsKey("onnx_path") && item.get("onnx_path") != null ? item.get("onnx_path").toString() : ""; boolean isInstalled = !onnxPath.isEmpty();
			String itemLang = item.containsKey("language") && item.get("language") != null ? item.get("language").toString() : ""; boolean isSupertonicModel = item.containsKey("type") && item.get("type").toString().equalsIgnoreCase(com.CodeBySonu.VoxSherpa.SupertonicModelFiles.MODEL_TYPE);
			boolean languageMatch = currentLanguage.equals("All Languages") || currentLanguage.equals(itemLang) || isSupertonicModel;
			if (languageMatch) {
				if (currentSort.equals("download")) { if (!isInstalled) modelList.add(item); } else if (currentSort.equals("installed")) { if (isInstalled) modelList.add(item); } else modelList.add(item);
			}
		}
		if (currentSort.equals("newest")) java.util.Collections.reverse(modelList); if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyDataSetChanged(); _updateEmptyState();
		if (!currentLanguage.equals("All Languages")) binding.sortTv.setText(currentLanguage); else if (currentSort.equals("download")) binding.sortTv.setText("Download"); else if (currentSort.equals("installed")) binding.sortTv.setText("Installed"); else if (currentSort.equals("newest")) binding.sortTv.setText("Newest First"); else if (currentSort.equals("oldest")) binding.sortTv.setText("Oldest First"); else binding.sortTv.setText("All Models");
	}
	
	public void _setupDataAndStorage() {
		// GRID LAYOUT MANAGER (2 COLUMNS)
		binding.recyclerviewModels.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(getContext(), 2));
		String savedData = sp1.getString("models_data", "[]"); modelList = new Gson().fromJson(savedData, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType()); if (modelList == null) modelList = new ArrayList<>();
		boolean hasSupertonicCard = false; boolean modelsDataChanged = false; java.io.File kokoroModelDir = com.CodeBySonu.VoxSherpa.KokoroModelFiles.getModelDirectory(getContext());
		String expectedKokoroOnnx = new java.io.File(kokoroModelDir, com.CodeBySonu.VoxSherpa.KokoroModelFiles.MODEL_FILE_NAME).getAbsolutePath(); String activeKokoroOnnx = sp1.getString("active_model", ""); boolean hadActiveKokoro = "kokoro".equalsIgnoreCase(sp1.getString("active_model_type", ""));
		for (int i = modelList.size() - 1; i >= 0; i--) {
			HashMap<String, Object> model = modelList.get(i);
			if (com.CodeBySonu.VoxSherpa.KokoroModelFiles.isKokoroModel(model)) { Object modelOnnx = model.get("onnx_path"); if (modelOnnx != null && activeKokoroOnnx.equals(modelOnnx.toString())) hadActiveKokoro = true; modelList.remove(i); modelsDataChanged = true; }
		}
		if (hadActiveKokoro && !expectedKokoroOnnx.equals(activeKokoroOnnx)) {
			try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().destroy(); } catch (Throwable error) {}
			sp1.edit().putString("active_model", "").putString("active_tokens", "").putString("active_model_name", "").putString("active_model_type", "").putString("active_voices_bin", "").putString("active_language", "").apply(); modelsDataChanged = true;
		}
		HashMap<String, Object> kokoroEntry = com.CodeBySonu.VoxSherpa.KokoroModelFiles.createCatalogEntry(); kokoroEntry.put("model_dir", kokoroModelDir.getAbsolutePath());
		if (com.CodeBySonu.VoxSherpa.KokoroModelFiles.isInstalled(kokoroModelDir)) { com.CodeBySonu.VoxSherpa.KokoroModelFiles.populateInstalledPaths(kokoroEntry, kokoroModelDir); kokoroEntry.put("download_progress", "100"); }
		modelList.add(kokoroEntry); modelsDataChanged = true;
		for (int i = modelList.size() - 1; i >= 0; i--) {
			HashMap<String, Object> model = modelList.get(i);
			if (model != null && "piper".equalsIgnoreCase(String.valueOf(model.get("type"))) && model.containsKey("archive_url") && !com.CodeBySonu.VoxSherpa.PiperModelCatalog.isCatalogModel(model)) {
				String modelPath = String.valueOf(model.get("onnx_path")); if (!modelPath.isEmpty() && modelPath.equals(sp1.getString("active_model", ""))) sp1.edit().putString("active_model", "").putString("active_tokens", "").putString("active_model_name", "").putString("active_model_type", "").putString("active_voices_bin", "").putString("active_language", "").putString("active_supertonic_dir", "").apply();
				modelList.remove(i); modelsDataChanged = true;
			}
		}
		for (HashMap<String, Object> model : modelList) {
			boolean isSupertonic = com.CodeBySonu.VoxSherpa.SupertonicModelFiles.isSupertonicModel(model);
			if (isSupertonic) {
				HashMap<String, Object> canonicalEntry = com.CodeBySonu.VoxSherpa.SupertonicModelFiles.createCatalogEntry(); boolean staleCatalog = false;
				for (String key : new String[]{"name", "type", "language", "gender", "size", "quality", "model_url", "model_files"}) { if (!java.util.Objects.equals(model.get(key), canonicalEntry.get(key))) { staleCatalog = true; break; } }
				boolean hasLegacyDownload = staleCatalog || ("true".equals(String.valueOf(model.get("is_downloading"))) && !model.containsKey("onnx_download_id"));
				if (hasLegacyDownload) {
					model.putAll(canonicalEntry);
					model.remove("model_dir"); model.remove("onnx_path"); model.remove("text_encoder_path"); model.remove("vector_estimator_path"); model.remove("vocoder_path"); model.remove("tokens_path"); model.remove("unicode_indexer_path"); model.remove("voice_style_path"); model.remove("voices_bin_path"); model.remove("onnx_download_id"); model.remove("tokens_download_id"); model.remove("voices_bin_download_id"); model.remove("target_onnx_name"); model.remove("target_tokens_name"); model.remove("target_voices_name"); model.remove("download_status"); model.remove("handler_running");
					java.io.File installedDir = com.CodeBySonu.VoxSherpa.SupertonicModelFiles.getModelDirectory(getContext()); if (com.CodeBySonu.VoxSherpa.SupertonicModelFiles.isInstalled(installedDir)) { com.CodeBySonu.VoxSherpa.SupertonicModelFiles.populateInstalledPaths(model, installedDir); model.put("download_progress", "100"); }
					modelsDataChanged = true;
				}
				model.remove("model_type"); model.remove("modelType"); model.remove("model_id"); model.remove("modelId"); model.remove("id");
				if (!"supertonic".equals(model.get("type"))) { model.put("type", com.CodeBySonu.VoxSherpa.SupertonicModelFiles.MODEL_TYPE); modelsDataChanged = true; }
			}
			if (isSupertonic) hasSupertonicCard = true;
		}
		if (!hasSupertonicCard) { modelList.add(com.CodeBySonu.VoxSherpa.SupertonicModelFiles.createCatalogEntry()); modelsDataChanged = true; }
		_rebuildCatalogOrder(); modelsDataChanged = true; if (modelsDataChanged) sp1.edit().putString("models_data", new Gson().toJson(modelList)).apply();
		try {
			StatFs stat = new StatFs(Environment.getDataDirectory().getPath()); long bytesTotal = stat.getBlockSizeLong() * stat.getBlockCountLong(); long bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong(); long bytesUsed = bytesTotal - bytesAvailable;
			double gbTotal = bytesTotal / (1024.0 * 1024.0 * 1024.0); double gbUsed = bytesUsed / (1024.0 * 1024.0 * 1024.0); int progressPercent = (int) ((bytesUsed * 100) / bytesTotal);
			binding.storageUsedTv.setText(String.format(Locale.US, "%.1f GB of %.1f GB used", gbUsed, gbTotal)); binding.storageUsedProgressbar.setProgress(progressPercent);
		} catch (Exception e) { binding.storageUsedTv.setText("Storage info unavailable"); binding.storageUsedProgressbar.setProgress(0); }
		_updateEmptyState();
	}

	private void _rebuildCatalogOrder() {
		java.util.ArrayList<HashMap<String, Object>> existingModels = new java.util.ArrayList<>(modelList); java.util.ArrayList<HashMap<String, Object>> orderedModels = new java.util.ArrayList<>();
		java.util.HashSet<String> builtInUrls = new java.util.HashSet<>(); java.util.List<HashMap<String, Object>> builtInEntries = com.CodeBySonu.VoxSherpa.VoiceModelCatalog.createBuiltInEntries();
		for (int i = 0; i < 2; i++) {
			HashMap<String, Object> canonical = builtInEntries.get(i); String url = String.valueOf(canonical.get("model_url")); builtInUrls.add(url); HashMap<String, Object> existing = null;
			if (i == 1) { for (HashMap<String, Object> candidate : existingModels) { if (com.CodeBySonu.VoxSherpa.SupertonicModelFiles.isSupertonicModel(candidate)) { existing = candidate; break; } } } else existing = _findModelByUrl(existingModels, url);
			orderedModels.add(existing != null ? existing : canonical);
		}
		for (int i = 2; i < builtInEntries.size(); i++) {
			HashMap<String, Object> canonical = builtInEntries.get(i); String url = String.valueOf(canonical.get("model_url")); builtInUrls.add(url);
			HashMap<String, Object> existingEntry = _findModelByUrl(existingModels, url); HashMap<String, Object> entry = _resetCatalogEntry(canonical, existingEntry);
			java.io.File modelDir = com.CodeBySonu.VoxSherpa.PiperModelCatalog.getModelDirectory(getContext(), entry); entry.put("model_dir", modelDir.getAbsolutePath());
			if (com.CodeBySonu.VoxSherpa.PiperModelCatalog.isInstalled(modelDir)) { com.CodeBySonu.VoxSherpa.PiperModelCatalog.populateInstalledPaths(entry, modelDir); entry.put("download_progress", "100"); } else if (!"true".equals(String.valueOf(entry.get("is_downloading")))) { entry.remove("onnx_path"); entry.remove("tokens_path"); }
			orderedModels.add(entry);
		}
		java.util.HashSet<String> retainedUrls = new java.util.HashSet<>(builtInUrls);
		for (HashMap<String, Object> existing : existingModels) {
			if (existing == null) continue;
			String type = String.valueOf(existing.get("type")); String url = existing.containsKey("model_url") ? String.valueOf(existing.get("model_url")) : "";
			boolean isMms = "mms".equalsIgnoreCase(type) || url.contains("mms-tts-multilingual-models-onnx"); boolean isUserModel = url.isEmpty();
			if ((!isMms && !isUserModel) || (isMms && retainedUrls.contains(url))) continue; if (!url.isEmpty() && !retainedUrls.add(url)) continue; orderedModels.add(existing);
		}
		modelList.clear(); modelList.addAll(orderedModels);
	}

	private HashMap<String, Object> _findModelByUrl(java.util.List<HashMap<String, Object>> models, String url) {
		for (HashMap<String, Object> model : models) { if (model != null && url.equals(String.valueOf(model.get("model_url")))) return model; } return null;
	}

	private HashMap<String, Object> _resetCatalogEntry(HashMap<String, Object> canonical, HashMap<String, Object> source) {
		if (source == null) return canonical;
		HashMap<String, Object> target = source; java.util.HashMap<String, Object> downloadState = new java.util.HashMap<>();
		for (String key : new String[] {"is_downloading", "download_progress", "download_status"}) { if (source.containsKey(key)) downloadState.put(key, source.get(key)); }
		target.clear(); target.putAll(canonical); target.putAll(downloadState); return target;
	}
	
	public void _fetchFirebaseModels() {
		fb.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override public void onDataChange(@NonNull DataSnapshot snapshot) {
				if (snapshot.exists()) {
					boolean isListUpdated = false; java.util.ArrayList<String> onlineUrls = new java.util.ArrayList<>();
					onlineUrls.add(com.CodeBySonu.VoxSherpa.KokoroModelFiles.BASE_URL); onlineUrls.add(com.CodeBySonu.VoxSherpa.SupertonicModelFiles.BASE_URL); onlineUrls.addAll(com.CodeBySonu.VoxSherpa.PiperModelCatalog.getCatalogUrls());
					for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
						for (DataSnapshot child : categorySnapshot.getChildren()) {
							try {
								HashMap<String, Object> onlineModel = (HashMap<String, Object>) child.getValue();
								if (onlineModel != null && onlineModel.containsKey("model_url")) {
									String onlineUrl = onlineModel.get("model_url").toString(); onlineUrls.add(onlineUrl);
									if (com.CodeBySonu.VoxSherpa.KokoroModelFiles.isKokoroModel(onlineModel)) { onlineUrls.add(com.CodeBySonu.VoxSherpa.KokoroModelFiles.BASE_URL); continue; }
									if ("piper".equalsIgnoreCase(String.valueOf(onlineModel.get("type"))) && onlineModel.containsKey("archive_url")) continue;
									if (!com.CodeBySonu.VoxSherpa.SupertonicModelFiles.isSupertonicModel(onlineModel) && child.getKey() != null && child.getKey().toLowerCase(Locale.ROOT).contains("supertonic")) onlineModel.put("model_id", child.getKey());
									if (com.CodeBySonu.VoxSherpa.SupertonicModelFiles.isSupertonicModel(onlineModel)) {
										HashMap<String, Object> canonicalEntry = com.CodeBySonu.VoxSherpa.SupertonicModelFiles.createCatalogEntry(); String canonicalUrl = canonicalEntry.get("model_url").toString(); onlineUrls.add(canonicalUrl); boolean hasLocalSupertonic = false;
										for (HashMap<String, Object> localModel : modelList) {
											if (!com.CodeBySonu.VoxSherpa.SupertonicModelFiles.isSupertonicModel(localModel)) continue; hasLocalSupertonic = true;
											for (String key : new String[]{"name", "type", "language", "gender", "size", "quality", "model_url", "model_files"}) { Object canonicalValue = canonicalEntry.get(key); if (!java.util.Objects.equals(localModel.get(key), canonicalValue)) { localModel.put(key, canonicalValue); isListUpdated = true; } }
										}
										if (!hasLocalSupertonic) { modelList.add(canonicalEntry); isListUpdated = true; } continue;
									}
									if (!"mms".equalsIgnoreCase(String.valueOf(onlineModel.get("type")))) continue;
									if (!onlineModel.containsKey("is_downloading")) onlineModel.put("is_downloading", "false"); if (!onlineModel.containsKey("download_progress")) onlineModel.put("download_progress", "0");
									boolean isAlreadyExists = false;
									for (int i = 0; i < modelList.size(); i++) {
										HashMap<String, Object> localModel = modelList.get(i); String localUrl = localModel.containsKey("model_url") ? localModel.get("model_url").toString() : "";
										if (!onlineUrl.isEmpty() && onlineUrl.equals(localUrl)) {
											isAlreadyExists = true;
											for (String key : new String[]{"name", "type", "voices_bin_url", "semple", "gender", "language", "size", "quality", "tokens_url"}) { if (onlineModel.containsKey(key)) { Object onlineVal = onlineModel.get(key); Object localVal = localModel.get(key); if (onlineVal != null && !onlineVal.equals(localVal)) { localModel.put(key, onlineVal); isListUpdated = true; } } }
											break;
										}
									}
									if (!isAlreadyExists) { modelList.add(onlineModel); isListUpdated = true; }
								}
							} catch (Exception e) {}
						}
					}
					for (int i = modelList.size() - 1; i >= 0; i--) {
						HashMap<String, Object> localModel = modelList.get(i);
						if (localModel.containsKey("model_url")) {
							String localUrl = localModel.get("model_url").toString(); boolean isBuiltInSupertonic = com.CodeBySonu.VoxSherpa.SupertonicModelFiles.isSupertonicModel(localModel);
							if (!onlineUrls.contains(localUrl) && !isBuiltInSupertonic && !localUrl.contains("mms-tts-multilingual-models-onnx")) {
								String onnxPath = localModel.containsKey("onnx_path") ? localModel.get("onnx_path").toString() : ""; String tokensPath = localModel.containsKey("tokens_path") ? localModel.get("tokens_path").toString() : ""; String voicesPath = localModel.containsKey("voices_bin_path") ? localModel.get("voices_bin_path").toString() : "";
								if (!onnxPath.isEmpty()) new java.io.File(onnxPath).delete(); if (!tokensPath.isEmpty()) new java.io.File(tokensPath).delete(); if (!voicesPath.isEmpty()) new java.io.File(voicesPath).delete();
								if (onnxPath.equals(sp1.getString("active_model", ""))) { try { com.CodeBySonu.VoxSherpa.VoiceEngine.getInstance().destroy(); } catch (Throwable ignored) {} try { com.CodeBySonu.VoxSherpa.KokoroEngine.getInstance().destroy(); } catch (Throwable ignored) {} sp1.edit().putString("active_model", "").putString("active_tokens", "").putString("active_model_name", "").apply(); }
								modelList.remove(i); isListUpdated = true;
							}
						}
					}
					for (HashMap<String, Object> localModel : modelList) { if (com.CodeBySonu.VoxSherpa.SupertonicModelFiles.isSupertonicModel(localModel) && !com.CodeBySonu.VoxSherpa.SupertonicModelFiles.DISPLAY_SIZE.equals(localModel.get("size"))) { localModel.put("size", com.CodeBySonu.VoxSherpa.SupertonicModelFiles.DISPLAY_SIZE); isListUpdated = true; } }
					if (isListUpdated) { _rebuildCatalogOrder(); sp1.edit().putString("models_data", new com.google.gson.Gson().toJson(modelList)).apply(); _updateEmptyState(); if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyDataSetChanged(); }
				}
			}
			@Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) { com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), "Failed to fetch models from server.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show(); }
		});
	}
	
	public void _syncMmsCatalog() {
		SharedPreferences sp3 = getContext().getSharedPreferences("sp3", android.content.Context.MODE_PRIVATE); boolean mmsEnabled = sp3.getBoolean("mms_models_enabled", false);
		String savedData = sp1.getString("models_data", "[]"); java.util.ArrayList<HashMap<String, Object>> masterList = GSON.fromJson(savedData, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType()); if (masterList == null) masterList = new java.util.ArrayList<>();
		if (mmsEnabled) {
			java.util.List<HashMap<String, Object>> catalog = com.CodeBySonu.VoxSherpa.MmsCatalog.loadModelEntries(getContext()); if (catalog == null || catalog.isEmpty()) return;
			java.util.HashSet<String> existingUrls = new java.util.HashSet<>(); for (HashMap<String, Object> m : masterList) { if (m.containsKey("model_url")) existingUrls.add(m.get("model_url").toString()); }
			boolean changed = false; for (HashMap<String, Object> entry : catalog) { String url = entry.containsKey("model_url") ? entry.get("model_url").toString() : ""; if (!url.isEmpty() && !existingUrls.contains(url)) { masterList.add(entry); existingUrls.add(url); changed = true; } }
			if (changed) { sp1.edit().putString("models_data", GSON.toJson(masterList)).apply(); modelList.clear(); modelList.addAll(masterList); if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyDataSetChanged(); }
		} else {
			boolean changed = false; java.util.Iterator<HashMap<String, Object>> it = masterList.iterator();
			while (it.hasNext()) { HashMap<String, Object> m = it.next(); boolean isMms = m.containsKey("type") && m.get("type").toString().contains("MMS"); String onnxPath = m.containsKey("onnx_path") && m.get("onnx_path") != null ? m.get("onnx_path").toString() : ""; if (isMms && onnxPath.isEmpty()) { it.remove(); changed = true; } }
			if (changed) { sp1.edit().putString("models_data", GSON.toJson(masterList)).apply(); modelList.clear(); modelList.addAll(masterList); if (binding.recyclerviewModels.getAdapter() != null) binding.recyclerviewModels.getAdapter().notifyDataSetChanged(); }
		}
	}
}
