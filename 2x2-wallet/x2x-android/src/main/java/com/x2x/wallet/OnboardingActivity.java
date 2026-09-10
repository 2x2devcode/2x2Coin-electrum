package com.x2x.wallet;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.x2x.core.Bip39;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Onboarding: create (with seed confirmation) or import a wallet, then set a PIN.
 */
public class OnboardingActivity extends AppCompatActivity {

    private WalletStorage storage;
    private LinearLayout panelStart, panelShowSeed, panelConfirmSeed, panelImport;
    private TextView tvSeedWords, tvConfirmPrompt;
    private EditText etImport, etConfirmWord, etImportPassphrase;
    private String pendingMnemonic;
    private String[] pendingWords;
    private final List<Integer> confirmIndexes = new ArrayList<>();
    private int confirmStep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthHelper.enableFlagSecure(this);
        storage = new WalletStorage(this);
        if (storage.hasWallet() && storage.hasPin() && storage.isSeedConfirmed()) {
            goToWallet();
            return;
        }
        // Resume incomplete setup (wallet saved but PIN/confirm missing).
        if (storage.hasWallet() && !storage.hasPin()) {
            AuthHelper.promptSetPinThen(this, storage, this::goToWallet);
            return;
        }

        setContentView(R.layout.activity_onboarding);
        panelStart = findViewById(R.id.panel_start);
        panelShowSeed = findViewById(R.id.panel_show_seed);
        panelConfirmSeed = findViewById(R.id.panel_confirm_seed);
        panelImport = findViewById(R.id.panel_import);
        tvSeedWords = findViewById(R.id.tv_seed_words);
        tvConfirmPrompt = findViewById(R.id.tv_confirm_prompt);
        etImport = findViewById(R.id.et_import_seed);
        etImportPassphrase = findViewById(R.id.et_import_passphrase);
        etConfirmWord = findViewById(R.id.et_confirm_word);

        findViewById(R.id.btn_create).setOnClickListener(v -> startCreate());
        findViewById(R.id.btn_import).setOnClickListener(v -> showOnly(panelImport));
        findViewById(R.id.btn_seed_continue).setOnClickListener(v -> startConfirm());
        findViewById(R.id.btn_confirm_next).setOnClickListener(v -> checkConfirmWord());
        findViewById(R.id.btn_import_go).setOnClickListener(v -> doImport());
        findViewById(R.id.btn_import_back).setOnClickListener(v -> showOnly(panelStart));
        findViewById(R.id.btn_seed_back).setOnClickListener(v -> {
            pendingMnemonic = null;
            showOnly(panelStart);
        });
        findViewById(R.id.btn_confirm_back).setOnClickListener(v -> showOnly(panelShowSeed));

        showOnly(panelStart);
    }

    private void startCreate() {
        pendingMnemonic = Bip39.generate(128);
        pendingWords = pendingMnemonic.split(" ");
        tvSeedWords.setText(formatSeed(pendingWords));
        showOnly(panelShowSeed);
    }

    private void startConfirm() {
        confirmIndexes.clear();
        Random rnd = new Random();
        while (confirmIndexes.size() < 3) {
            int i = rnd.nextInt(pendingWords.length);
            if (!confirmIndexes.contains(i)) confirmIndexes.add(i);
        }
        Collections.sort(confirmIndexes);
        confirmStep = 0;
        etConfirmWord.setText("");
        updateConfirmPrompt();
        showOnly(panelConfirmSeed);
    }

    private void updateConfirmPrompt() {
        int wordNum = confirmIndexes.get(confirmStep) + 1;
        tvConfirmPrompt.setText("Enter word #" + wordNum + " of your recovery phrase");
        etConfirmWord.setText("");
        etConfirmWord.requestFocus();
    }

    private void checkConfirmWord() {
        String typed = etConfirmWord.getText().toString().trim().toLowerCase(Locale.US);
        String expect = pendingWords[confirmIndexes.get(confirmStep)];
        if (!expect.equals(typed)) {
            Toast.makeText(this, "Word does not match. Check your backup.", Toast.LENGTH_LONG)
                    .show();
            return;
        }
        confirmStep++;
        if (confirmStep >= confirmIndexes.size()) {
            finishCreate(pendingMnemonic, true);
        } else {
            updateConfirmPrompt();
        }
    }

    private void doImport() {
        String mnemonic = etImport.getText().toString().trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.US);
        if (!Bip39.isValid(mnemonic)) {
            Toast.makeText(this, "Invalid seed phrase", Toast.LENGTH_LONG).show();
            return;
        }
        String passphrase = etImportPassphrase.getText().toString();
        finishCreate(mnemonic, passphrase, true);
    }

    private void finishCreate(String mnemonic, boolean confirmed) {
        finishCreate(mnemonic, "", confirmed);
    }

    private void finishCreate(String mnemonic, String passphrase, boolean confirmed) {
        storage.saveMnemonic(mnemonic);
        storage.savePassphrase(passphrase);
        storage.setSeedConfirmed(confirmed);
        storage.setReceiveIndex(0);
        storage.setChangeIndex(0);
        AuthHelper.promptSetPinThen(this, storage, this::goToWallet);
    }

    private void goToWallet() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void showOnly(View panel) {
        panelStart.setVisibility(panel == panelStart ? View.VISIBLE : View.GONE);
        panelShowSeed.setVisibility(panel == panelShowSeed ? View.VISIBLE : View.GONE);
        panelConfirmSeed.setVisibility(panel == panelConfirmSeed ? View.VISIBLE : View.GONE);
        panelImport.setVisibility(panel == panelImport ? View.VISIBLE : View.GONE);
    }

    private static String formatSeed(String[] words) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            sb.append(String.format(Locale.US, "%2d. %s", i + 1, words[i]));
            if (i < words.length - 1) sb.append(i % 2 == 1 ? "\n" : "    ");
        }
        return sb.toString();
    }
}
