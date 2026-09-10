package com.x2x.wallet;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.x2x.core.Address;
import com.x2x.core.ApiClient;
import com.x2x.core.NetworkParameters;
import com.x2x.core.Wallet;

public class MainActivity extends AppCompatActivity {

    private WalletStorage storage;
    private Wallet wallet;
    private int receiveIndex = 0;
    private int changeIndex = 0;
    private long feePerKb = NetworkParameters.DEFAULT_FEE_PER_KB;

    private TextView tvStatus, tvBalance, tvAddress, tvFee, tvWebsite, tvActivity;
    private ImageView imgQr;
    private EditText etTo, etAmount;
    private SwipeRefreshLayout swipeRefresh;

    private final ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) return;
                String raw = result.getContents().trim();
                String addr = extractAddress(raw);
                if (Address.isValid(addr)) {
                    etTo.setText(addr);
                } else {
                    Toast.makeText(this, "QR did not contain a valid 2X2 address",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        storage = new WalletStorage(this);
        String mnemonic = storage.getMnemonic();
        if (mnemonic == null) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        if (!storage.hasPin()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        wallet = Wallet.fromMnemonic(mnemonic, "", new ApiClient());
        receiveIndex = storage.getReceiveIndex();
        changeIndex = storage.getChangeIndex();

        swipeRefresh = findViewById(R.id.swipe_refresh);
        tvStatus = findViewById(R.id.tv_status);
        tvBalance = findViewById(R.id.tv_balance);
        tvAddress = findViewById(R.id.tv_address);
        tvFee = findViewById(R.id.tv_fee);
        tvWebsite = findViewById(R.id.tv_website);
        tvActivity = findViewById(R.id.tv_activity);
        imgQr = findViewById(R.id.img_qr);
        etTo = findViewById(R.id.et_to);
        etAmount = findViewById(R.id.et_amount);

        swipeRefresh.setOnRefreshListener(this::refresh);
        findViewById(R.id.btn_copy).setOnClickListener(v -> copyAddress());
        findViewById(R.id.btn_new_address).setOnClickListener(v -> {
            receiveIndex++;
            storage.setReceiveIndex(receiveIndex);
            showAddress();
        });
        findViewById(R.id.btn_send).setOnClickListener(v -> confirmSend());
        findViewById(R.id.btn_scan_qr).setOnClickListener(v -> scanQr());
        findViewById(R.id.btn_backup).setOnClickListener(v -> showSeed());
        findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDelete());
        tvWebsite.setOnClickListener(v -> openWebsite());

        showAddress();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wallet != null) refresh();
    }

    private void showAddress() {
        String addr = wallet.receiveAddress(receiveIndex);
        tvAddress.setText(addr);
        Bitmap qr = Qr.encode(addr, 480);
        if (qr != null) imgQr.setImageBitmap(qr);
    }

    private void refresh() {
        swipeRefresh.setRefreshing(true);

        Bg.run(this, () -> {
            ApiClient.Status s = wallet.api().getStatus();
            feePerKb = wallet.api().getFeePerKb();
            return s;
        }, s -> {
            if (s.online) {
                tvStatus.setText("● Mainnet " + s.blocks);
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.x2x_green));
            } else {
                tvStatus.setText("offline");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.red));
            }
            tvFee.setText("Fee rate: " + Amounts.satToCoins(feePerKb) + " 2X2 / kB");
            maybeStopRefresh();
        }, e -> {
            tvStatus.setText("offline");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.red));
            maybeStopRefresh();
        });

        Bg.run(this, () -> wallet.getBalanceSat(),
                bal -> {
                    tvBalance.setText(Amounts.satToCoins(bal));
                    maybeStopRefresh();
                },
                e -> {
                    Toast.makeText(this, "Could not refresh balance", Toast.LENGTH_SHORT).show();
                    maybeStopRefresh();
                });

        loadActivity();
    }

    private void maybeStopRefresh() {
        swipeRefresh.setRefreshing(false);
    }

    private void loadActivity() {
        final int scan = Math.max(wallet.lookAhead, receiveIndex + 1);
        Bg.run(this, () -> {
            java.util.List<ApiClient.TxInfo> all = new java.util.ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < scan; i++) {
                for (ApiClient.TxInfo t : wallet.api().getTxs(wallet.receiveAddress(i))) {
                    String id = t.txid == null ? "" : t.txid;
                    if (!id.isEmpty() && seen.add(id)) all.add(t);
                }
            }
            return all;
        }, list -> {
            if (list.isEmpty()) {
                tvActivity.setText("No transactions yet");
            } else {
                StringBuilder sb = new StringBuilder();
                int n = Math.min(list.size(), 12);
                for (int i = 0; i < n; i++) {
                    ApiClient.TxInfo t = list.get(i);
                    String id = t.txid == null ? "" : t.txid;
                    String shortId = id.length() > 18
                            ? id.substring(0, 10) + "…" + id.substring(id.length() - 6) : id;
                    sb.append(shortId);
                    if (t.amount != null) sb.append("   ").append(t.amount).append(" 2X2");
                    if (i < n - 1) sb.append('\n');
                }
                tvActivity.setText(sb.toString());
            }
            swipeRefresh.setRefreshing(false);
        }, e -> {
            tvActivity.setText("Could not load activity");
            swipeRefresh.setRefreshing(false);
        });
    }

    private void copyAddress() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("2X2 address", tvAddress.getText().toString()));
        Toast.makeText(this, R.string.address_copied, Toast.LENGTH_SHORT).show();
    }

    private void scanQr() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Scan a 2X2 address");
        options.setBeepEnabled(false);
        options.setOrientationLocked(true);
        qrLauncher.launch(options);
    }

    private void confirmSend() {
        String to = etTo.getText().toString().trim();
        String amtStr = etAmount.getText().toString().trim();
        if (!Address.isValid(to)) {
            Toast.makeText(this, "Invalid recipient address", Toast.LENGTH_LONG).show();
            return;
        }
        long amountSat;
        try {
            amountSat = Amounts.coinsToSat(amtStr);
        } catch (Exception e) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_LONG).show();
            return;
        }
        if (amountSat <= 0) {
            Toast.makeText(this, "Amount must be positive", Toast.LENGTH_LONG).show();
            return;
        }

        final long amountFinal = amountSat;
        final int useChange = changeIndex;
        Toast.makeText(this, "Estimating fee…", Toast.LENGTH_SHORT).show();
        Bg.run(this, () -> wallet.createTransaction(to, amountFinal, useChange), built -> {
            String msg = "Send " + Amounts.satToCoins(amountFinal) + " 2X2\n"
                    + "To: " + to + "\n"
                    + "Network fee: " + Amounts.satToCoins(built.feeSat) + " 2X2\n"
                    + "Total debit: "
                    + Amounts.satToCoins(amountFinal + built.feeSat) + " 2X2";
            new AlertDialog.Builder(this)
                    .setTitle("Confirm payment")
                    .setMessage(msg)
                    .setPositiveButton("Send", (d, w) ->
                            AuthHelper.requireAuth(this, storage, "Authorize payment",
                                    () -> doSend(to, amountFinal, useChange)))
                    .setNegativeButton("Cancel", null)
                    .show();
        }, e -> new AlertDialog.Builder(this)
                .setTitle("Cannot build transaction")
                .setMessage(String.valueOf(e.getMessage()))
                .setPositiveButton("OK", null)
                .show());
    }

    private void doSend(String to, long amountSat, int useChange) {
        Toast.makeText(this, "Broadcasting…", Toast.LENGTH_SHORT).show();
        Bg.run(this, () -> wallet.send(to, amountSat, useChange), txid -> {
            // Rotate change address after a successful spend that may have created change.
            changeIndex = useChange + 1;
            storage.setChangeIndex(changeIndex);
            new AlertDialog.Builder(this)
                    .setTitle("Sent")
                    .setMessage("Transaction broadcast:\n" + txid)
                    .setPositiveButton("OK", null)
                    .show();
            etTo.setText("");
            etAmount.setText("");
            refresh();
        }, e -> new AlertDialog.Builder(this)
                .setTitle("Send failed")
                .setMessage(String.valueOf(e.getMessage()))
                .setPositiveButton("OK", null)
                .show());
    }

    private void showSeed() {
        AuthHelper.requireAuth(this, storage, "Reveal recovery phrase", () -> {
            AuthHelper.enableFlagSecure(this);
            new AlertDialog.Builder(this)
                    .setTitle("Recovery phrase")
                    .setMessage(storage.getMnemonic())
                    .setPositiveButton("Close", null)
                    .show();
        });
    }

    private void confirmDelete() {
        AuthHelper.requireAuth(this, storage, "Authorize wallet deletion", () -> {
            EditText confirm = new EditText(this);
            confirm.setHint("Type DELETE");
            confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            new AlertDialog.Builder(this)
                    .setTitle("Delete wallet?")
                    .setMessage("This removes the wallet from this device. Make sure your recovery "
                            + "phrase is backed up. Type DELETE to confirm.")
                    .setView(confirm)
                    .setPositiveButton("Delete", (d, w) -> {
                        if (!"DELETE".equals(confirm.getText().toString().trim())) {
                            Toast.makeText(this, "Confirmation text did not match",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        storage.clear();
                        startActivity(new Intent(this, OnboardingActivity.class));
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void openWebsite() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(NetworkParameters.WEBSITE_URL)));
        } catch (Exception ignored) {
        }
    }

    /** Accept bare addresses or URI-like payloads (coin:address?…). */
    static String extractAddress(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        int scheme = s.indexOf(':');
        if (scheme > 0 && scheme < 20) {
            s = s.substring(scheme + 1);
        }
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        return s.trim();
    }
}
