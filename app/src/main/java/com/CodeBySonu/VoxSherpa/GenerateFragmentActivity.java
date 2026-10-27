package com.CodeBySonu.VoxSherpa;

// ... (imports same rahenge) ...

// (Sirf wo lines dikha raha hu jo change hui hain, baaki aapka pura code same hai)
// Aap apne current code mein Ctrl+H (Find & Replace) dabayein aur:
// 1. "#00E5FF" ko dhundein aur "#4A9EFF" se replace karein (Saare replace karein).
// 2. "#3F4B61" ko dhundein aur "#94A3B8" se replace karein.
// 3. "#1D61FF" ko dhundein aur "#4A9EFF" se replace karein.

// Khaas taur par in jagah par changes dhyan se karein:

// Line ~124 (jab generate button dabate hain):
binding.textview69.setTextColor(android.graphics.Color.parseColor("#4A9EFF")); // Pehle #00E5FF tha

// Line ~330 (jab text change karte hain):
binding.textview69.setTextColor(android.graphics.Color.parseColor("#94A3B8")); // Pehle #3F4B61 tha

// _saveAudioAction() ke andar:
com.google.android.material.snackbar.Snackbar.make(getView(), "Audio saved to Library!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
.setBackgroundTint(android.graphics.Color.parseColor("#4A9EFF")) // Pehle #00E5FF tha
.setTextColor(android.graphics.Color.WHITE)
.show();

// showSupertonicVoiceSheet() ke andar:
row.setStrokeColor(android.graphics.Color.parseColor(i == selectedSpeaker ? "#4A9EFF" : "#1A2234")); // Pehle #00E5FF tha
