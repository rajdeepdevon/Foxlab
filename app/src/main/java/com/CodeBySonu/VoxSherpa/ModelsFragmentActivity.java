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
	private volatile android.media.AudioTrack supertonicPreviewTrack;
	private volatile int supertonicPreviewPosition = -1;
	private volatile int supertonicPreviewGeneration;
	private volatile com.CodeBySonu.VoxSherpa.SupertonicModelFiles.DownloadTask supertonicDownloadTask;
	private volatile com.CodeBySonu.VoxSherpa.KokoroModelFiles.DownloadTask kokoroDownloadTask;
	private final java.util.concurrent.ConcurrentHashMap<String,
			com.CodeBySonu.VoxSherpa.PiperModelCatalog.DownloadTask> piperDownloadTasks =
			new java.util.concurrent.ConcurrentHashMap<>();
	
	private Intent FilePicker = new Intent(Intent.ACTION_GET_CONTENT);
	private SharedPreferences sp1;
	private SharedPreferences sp2;
	private SharedPreferences sp3;
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
				// 1. Icon ko rotate karna (Top ki taraf) jab dialog khule
				binding.imageview8.animate().rotation(-90f).setDuration(300).start();
				
				// 2. Dialog Setup
				com.google.android.material.bottomsheet.BottomSheetDialog sortDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(getContext());
				View dialogView = getActivity().getLayoutInflater().inflate(R.layout.sort_bottom_dialog, null);
				
				android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
				gd.setColor(android.graphics.Color.parseColor("#131B2D"));
				float radius = 24f * getResources().getDisplayMetrics().density;
				gd.setCornerRadii(new float[]{
					radius, radius, 
					radius, radius, 
					0f, 0f, 
					0f, 0f
				});
				dialogView.setBackground(gd);
				sortDialog.setContentView(dialogView);
				
				// Fix for black corners in BottomSheet
				sortDialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
					@Override
					public void onShow(android.content.DialogInterface dialog) {
						com.google.android.material.bottomsheet.BottomSheetDialog d = (com.google.android.material.bottomsheet.BottomSheetDialog) dialog;
						View bottomSheetInternal = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
						if (bottomSheetInternal != null) {
							bottomSheetInternal.setBackgroundColor(android.graphics.Color.TRANSPARENT);
						}
					}
				});
				
				// Views ko find karna
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
				
				// Get current selections from SP1
				String currentSort = sp1.getString("sort_preference", "all_models");
				String currentLanguage = sp1.getString("language_filter", "All Languages");
				
				if(currentSort.equals("download")) rbDownload.setChecked(true);
				else if(currentSort.equals("installed")) rbInstalled.setChecked(true);
				else if(currentSort.equals("newest")) rbNewest.setChecked(true);
				else if(currentSort.equals("oldest")) rbOldest.setChecked(true);
				else rbAll.setChecked(true);
				
				// 3. COMBINED FILTER & SORT LOGIC (Runnable block to avoid duplicate code)
				Runnable applyFilterAndSort = () -> {
					String activeSort = sp1.getString("sort_preference", "all_models");
					String activeLang = sp1.getString("language_filter", "All Languages");
					
					String savedData = sp1.getString("models_data", "[]");
					java.util.ArrayList<java.util.HashMap<String, Object>> masterList = new com.google.gson.Gson().fromJson(
					savedData, 
					new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType()
					);
					
					if (masterList == null) masterList = new java.util.ArrayList<>();
					modelList.clear(); 
					
					for (java.util.HashMap<String, Object> item : masterList) {
						String onnxPath = item.containsKey("onnx_path") && item.get("onnx_path") != null ? item.get("onnx_path").toString() : "";
						boolean isInstalled = !onnxPath.isEmpty();
						
						// Language filter check
						String itemLang = item.containsKey("language") && item.get("language") != null ? item.get("language").toString() : "";
						boolean isSupertonicModel = item.containsKey("type")
								&& item.get("type").toString().equalsIgnoreCase(com.CodeBySonu.VoxSherpa.SupertonicModelFiles.MODEL_TYPE);
						boolean languageMatch = activeLang.equals("All Languages") || activeLang.equals(itemLang) || isSupertonicModel;
						
						if (languageMatch) {
							if (activeSort.equals("download")) {
								if (!isInstalled) modelList.add(item);
							} else if (activeSort.equals("installed")) {
								if (isInstalled) modelList.add(item);
							} else {
								modelList.add(item);
							}
						}
					}
					
					if (activeSort.equals("newest")) {
						java.util.Collections.reverse(modelList);
					}
					
					if (binding.recyclerviewModels.getAdapter() != null) {
						binding.recyclerviewModels.getAdapter().notifyDataSetChanged();
					}
					_updateEmptyState();
					
					// UI Text Update Logic
					if (!activeLang.equals("All Languages")) {
						binding.sortTv.setText(activeLang);
					} else if (activeSort.equals("download")) {
						binding.sortTv.setText("Download");
					} else if (activeSort.equals("installed")) {
						binding.sortTv.setText("Installed");
					} else if (activeSort.equals("newest")) {
						binding.sortTv.setText("Newest First");
					} else if (activeSort.equals("oldest")) {
						binding.sortTv.setText("Oldest First");
					} else {
						binding.sortTv.setText("All Models");
					}
				};
				
				// 4. SETUP LANGUAGE DROPDOWN
				String[] languagesFromXml = getResources().getStringArray(R.array.language_list);
				String[] finalLanguages = new String[languagesFromXml.length + 1];
				finalLanguages[0] = "All Languages";
				System.arraycopy(languagesFromXml, 0, finalLanguages, 1, languagesFromXml.length);
				
				android.widget.ArrayAdapter<String> langAdapter = new android.widget.ArrayAdapter<>(getContext(), R.layout.custom_dropdown_item, R.id.tv_drop_item, finalLanguages);
				android.widget.AutoCompleteTextView dropdownLang = dialogView.findViewById(R.id.dropdown_lang);
				
				if(dropdownLang != null) {
					dropdownLang.setAdapter(langAdapter);
					
					// Show the currently selected language in the dropdown UI
					dropdownLang.setText(currentLanguage, false);
					
					dropdownLang.setOnItemClickListener((parentView, view, position, id) -> {
						String selectedLang = finalLanguages[position];
						
						// Save the new language filter
						sp1.edit().putString("language_filter", selectedLang).apply();
						sortDialog.dismiss();
						
						// Trigger the combined filter logic
						applyFilterAndSort.run();
					});
				}
				
				// 5. STANDARD SORT BUTTONS CLICK LISTENER
				View.OnClickListener clickListener = v -> {
					String selected = "all_models";
					if(v.getId() == R.id.layout_download) selected = "download";
					else if(v.getId() == R.id.layout_installed) selected = "installed";
					else if(v.getId() == R.id.layout_newest) selected = "newest";
					else if(v.getId() == R.id.layout_oldest) selected = "oldest";
					
					// Save the new sort preference
					sp1.edit().putString("sort_preference", selected).apply();
					sortDialog.dismiss();
					
					// Trigger the combined filter logic
					applyFilterAndSort.run();
				};
				
				layoutAll.setOnClickListener(clickListener);
				layoutDownload.setOnClickListener(clickListener);
				layoutInstalled.setOnClickListener(clickListener);
				layoutNewest.setOnClickListener(clickListener);
				layoutOldest.setOnClickListener(clickListener);
				
				// 6. Dialog Band Hone Par Icon Reset
				sortDialog.setOnDismissListener(dialogInterface -> {
					binding.imageview8.animate().rotation(0f).setDuration(300).start();
				});
				
				sortDialog.show();
				
			}
		});
		
		_fb_child_listener = new ChildEventListener() {
			@Override
			public void onChildAdded(DataSnapshot _param1, String _param2) {
				GenericTypeIndicator<HashMap<String, Object>> _ind = new GenericTypeIndicator<HashMap<String, Object>>() {};
				final String _childKey = _param1.getKey();
				final HashMap<String, Object> _childValue = _param1.getValue(_ind);
				
			}
			
			@Override
			public void onChildChanged(DataSnapshot _param1, String _param2) {
				GenericTypeIndicator<HashMap<String, Object>> _ind = new GenericTypeIndicator<HashMap<String, Object>>() {};
				final String _childKey = _param1.getKey();
				final HashMap<String, Object> _childValue = _param1.getValue(_ind);
				
			}
			
			@Override
			public void onChildMoved(DataSnapshot _param1, String _param2) {
				
			}
			
			@Override
			public void onChildRemoved(DataSnapshot _param1) {
				GenericTypeIndicator<HashMap<String, Object>> _ind = new GenericTypeIndicator<HashMap<String, Object>>() {};
				final String _childKey = _param1.getKey();
				final HashMap<String, Object> _childValue = _param1.getValue(_ind);
				
			}
			
			@Override
			public void onCancelled(DatabaseError _param1) {
				final int _errorCode = _param1.getCode();
				final String _errorMessage = _param1.getMessage();
				
			}
		};
		fb.addChildEventListener(_fb_child_listener);
	}
	
	private void initializeLogic() {
		binding.modelsMenu.setOnClickListener(anchor -> {
			android.widget.PopupMenu menu = new android.widget.PopupMenu(requireContext(), anchor);
			menu.getMenu().add(0, 0, 0, "Generate");
			menu.getMenu().add(0, 1, 1, "Manage Models");
			menu.getMenu().add(0, 2, 2, "Library");
			menu.getMenu().add(0, 3, 3, "Settings");
			menu.setOnMenuItemClickListener(item -> {
				androidx.viewpager2.widget.ViewPager2 viewPager = requireActivity().findViewById(R.id.viewpager);
				viewPager.setCurrentItem(item.getItemId(), false);
				return true;
			});
			menu.show();
		});
		String initialSort = sp1.getString("sort_preference", "all_models");
		if (initialSort.equals("download")) binding.sortTv.setText("Download");
		else if (initialSort.equals("installed")) binding.sortTv.setText("Installed");
		else if (initialSort.equals("newest")) binding.sortTv.setText("Newest First");
		else if (initialSort.equals("oldest")) binding.sortTv.setText("Oldest First");
		else binding.sortTv.setText("All Models");
		
		
		_syncMmsCatalog();
		_setupDataAndStorage();
		_fetchFirebaseModels();
		_setupRecyclerViewAdapter();
		_setupFabAndImportDialog();
	}
	
	@Override
	public void onActivityResult(int _requestCode, int _resultCode, Intent _data) {
		super.onActivityResult(_requestCode, _resultCode, _data);
		if (_resultCode == android.app.Activity.RESULT_OK && _data != null) {
			android.net.Uri uri = (_data.getClipData() != null) ? _data.getClipData().getItemAt(0).getUri() : _data.getData();
			String path = uri.toString();
			String mode = sp1.getString("picking_mode", "");
			
			// --- SMART LOGIC: Get File Name and Size from URI ---
			String fileName = "Unknown_File";
			String fileSizeStr = "0 MB";
			android.database.Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
			if (cursor != null && cursor.moveToFirst()) {
				int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
				int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
				if (nameIndex != -1) fileName = cursor.getString(nameIndex);
				if (sizeIndex != -1) {
					long size = cursor.getLong(sizeIndex);
					fileSizeStr = String.format(java.util.Locale.US, "%.1f MB", (size / (1024.0 * 1024.0)));
				}
				cursor.close();
			}
			
			// --- MODE: ONNX ---
			if (mode.equals("onnx")) {
				if (fileName.toLowerCase().endsWith(".onnx")) {
					tempOnnxPath = path;
					// Save details to SP
					sp1.edit().putString("temp_onnx_name", fileName).apply();
					sp1.edit().putString("temp_onnx_size", fileSizeStr).apply();
					
					// VISUAL FEEDBACK ON DIALOG
					if (dialogView != null) {
						com.google.android.material.card.MaterialCardView card = dialogView.findViewById(R.id.btn_choose_onnx);
						android.widget.TextView tvName = dialogView.findViewById(R.id.onnx_name_tv);
						card.setStrokeColor(android.graphics.Color.parseColor("#1D61FF")); // Blue Success Stroke
						tvName.setText(fileName);
						tvName.setTextColor(android.graphics.Color.parseColor("#1D61FF"));
					}
				} else {
					// PROFESSIONAL SNACKBAR FOR WRONG EXTENSION
					com.google.android.material.snackbar.Snackbar snack = com.google.android.material.snackbar.Snackbar.make(getActivity().findViewById(android.R.id.content), "Invalid File! Please select a .onnx model file.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG);
					snack.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"));
					snack.setTextColor(android.graphics.Color.WHITE);
					snack.show();
				}
			} 
			// --- MODE: TOKENS ---
			else if (mode.equals("tokens")) {
				if (fileName.toLowerCase().endsWith(".txt")) {
					tempTokensPath = path;
					if (dialogView != null) {
						com.google.android.material.card.MaterialCardView card = dialogView.findViewById(R.id.btn_select_tokens);
						android.widget.TextView tvName = dialogView.findViewById(R.id.tokens_txt_tv);
						card.setStrokeColor(android.graphics.Color.parseColor("#48BB78")); // Green Success Stroke
						tvName.setText(fileName);
						tvName.setTextColor(android.graphics.Color.parseColor("#48BB78"));
					}
				} else {
					com.google.android.material.snackbar.Snackbar snack = com.google.android.material.snackbar.Snackbar.make(getActivity().findViewById(android.R.id.content), "Invalid File! Please select the tokens.txt file.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG);
					snack.setBackgroundTint(android.graphics.Color.parseColor("#FF4B4B"));
					snack.setTextColor(android.graphics.Color.WHITE);
					snack.show();
				}
			}
			
		}
		switch (_requestCode) {
			
			default:
			break;
		}
	}
	
	
	@Override
	public void onResume() {
		super.onResume();
		_syncMmsCatalog();
	}

	@Override
	public void onDestroyView() {
		stopSupertonicPreview();
		super.onDestroyView();
		binding = null;
	}
	public void _updateEmptyState() {
		boolean empty = modelList.isEmpty();
		binding.recyclerviewModels.setVisibility(empty ? View.GONE : View.VISIBLE);
		binding.emptyStateView.setVisibility(empty ? View.VISIBLE : View.GONE);
		binding.modelCountTv.setText("MODELS LIST (" + modelList.size() + ")");
	}

	private void playSupertonicPreview(HashMap<String, Object> item, int position, View row, String modelDir) {
		stopSupertonicPreview();
		if (modelDir == null || modelDir.trim().isEmpty()
				|| !com.CodeBySonu.VoxSherpa.SupertonicModelFiles.isInstalled(new File(modelDir))) {
			Snackbar.make(row, "Download Supertonic V3 before previewing it.", Snackbar.LENGTH_SHORT).show();
			return;
		}

		com.CodeBySonu.VoxSherpa.AudioSampleHelper.getInstance().stopAudio();
		for (int i = 0; i < modelList.size(); i++) {
			HashMap<String, Object> model = modelList.get(i);
			if ("true".equals(model.get("is_playing")) || "true".equals(model.get("is_buffering"))) {
				model.put("is_playing", "false");
				model.put("is_buffering", "false");
				if (binding.recyclerviewModels.getAdapter() != null) {
					binding.recyclerviewModels.getAdapter().notifyItemChanged(i);
				}
			}
		}

		final int previewGeneration = supertonicPreviewGeneration;
		item.put("is_buffering", "true");
		item.put("is_playing", "false");
		if (binding.recyclerviewModels.getAdapter() != null) {
			binding.recyclerviewModels.getAdapter().notifyItemChanged(position);
		}

		android.content.Context appContext = requireContext().getApplicationContext();
		new Thread(() -> {
			android.media.AudioTrack track = null;
			try {
				com.CodeBySonu.VoxSherpa.SupertonicEngine engine =
						com.CodeBySonu.VoxSherpa.SupertonicEngine.getInstan
