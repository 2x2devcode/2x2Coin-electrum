package com.x2x.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import com.x2x.core.Address;
import com.x2x.core.Amounts;
import com.x2x.core.ApiClient;
import com.x2x.core.ApiException;
import com.x2x.core.AppLog;
import com.x2x.core.Bip39;
import com.x2x.core.NetworkParameters;
import com.x2x.core.Wallet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native desktop UI for 2X2 Wallet (JavaFX). Shares {@link com.x2x.core} with the Android app.
 */
public class MainApp extends Application {

    private static final String BG = "#070B14";
    private static final String SURFACE = "#10182A";
    private static final String GREEN = "#39FF14";
    private static final String MUTED = "#8A9BB3";
    private static final String ON = "#E8EEF8";

    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "2x2-wallet-bg");
        t.setDaemon(true);
        return t;
    });

    private Stage stage;
    private DesktopStorage storage;
    private String sessionPin;
    private Wallet wallet;
    private long feePerKb = NetworkParameters.DEFAULT_FEE_PER_KB;

    private Label statusLabel;
    private Label balanceLabel;
    private Label depositBalanceLabel;
    private Label syncLabel;
    private Label feeLabel;
    private Label addressLabel;
    private VBox activityBox;
    private ImageView qrView;
    private TextField toField;
    private TextField amountField;
    private Button sendButton;
    private Button refreshButton;
    private ComboBox<DesktopStorage.Contact> addressBookCombo;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        AppLog.info("desktop app starting log=" + AppLog.logFile());
        this.storage = new DesktopStorage();
        stage.setTitle("2X2 Wallet");
        stage.setMinWidth(720);
        stage.setMinHeight(560);
        if (storage.hasWallet()) {
            showUnlock();
        } else {
            showOnboarding();
        }
        stage.show();
    }

    @Override
    public void stop() {
        pool.shutdownNow();
    }

    // ---- onboarding / unlock ----

    private void showOnboarding() {
        Label title = titleLabel("2X2 Wallet");
        Label sub = mutedLabel("Secure self-custody wallet for 2x2coin (PoW+PoS hybrid)");
        Button create = primaryButton("Create new wallet");
        Button importBtn = secondaryButton("Import from seed phrase");
        create.setOnAction(e -> startCreateFlow());
        importBtn.setOnAction(e -> startImportFlow());

        VBox box = new VBox(16, title, sub, create, importBtn);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        setScene(box);
    }

    private void showUnlock() {
        Label title = titleLabel("Unlock wallet");
        PasswordField pin = new PasswordField();
        pin.setPromptText("6-digit PIN");
        pin.setMaxWidth(220);
        Button unlock = primaryButton("Unlock");
        Label err = mutedLabel("");
        Runnable go = () -> {
            try {
                storage.unlock(pin.getText().trim());
                sessionPin = pin.getText().trim();
                openMain();
            } catch (Exception ex) {
                err.setTextFill(Color.web("#E5484D"));
                err.setText("Unlock failed: " + ex.getMessage());
            }
        };
        unlock.setOnAction(e -> go.run());
        pin.setOnAction(e -> go.run());
        VBox box = new VBox(14, title, pin, unlock, err);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        setScene(box);
    }

    private void startCreateFlow() {
        String mnemonic = Bip39.generate(128);
        String[] words = mnemonic.split(" ");

        TextArea area = new TextArea(formatSeed(words));
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(8);
        area.setStyle(fieldStyle());

        Button next = primaryButton("I wrote it down");
        Button back = secondaryButton("Back");
        back.setOnAction(e -> showOnboarding());
        next.setOnAction(e -> confirmSeed(mnemonic, words));

        VBox box = new VBox(12,
                titleLabel("Write down your recovery phrase"),
                mutedLabel("These 12 words are the only way to restore your funds."),
                area, next, back);
        box.setPadding(new Insets(28));
        setScene(scroll(box));
    }

    private void confirmSeed(String mnemonic, String[] words) {
        List<Integer> indexes = new ArrayList<>();
        Random rnd = new Random();
        while (indexes.size() < 3) {
            int i = rnd.nextInt(words.length);
            if (!indexes.contains(i)) indexes.add(i);
        }
        Collections.sort(indexes);
        confirmStep(mnemonic, words, indexes, 0);
    }

    private void confirmStep(String mnemonic, String[] words, List<Integer> indexes, int step) {
        int wordNum = indexes.get(step) + 1;
        TextField input = new TextField();
        input.setPromptText("word");
        input.setMaxWidth(280);
        input.setStyle(fieldStyle());
        Button next = primaryButton(step + 1 >= indexes.size() ? "Set PIN" : "Next");
        Button back = secondaryButton("Back");
        back.setOnAction(e -> startCreateFlow());
        next.setOnAction(e -> {
            String typed = input.getText().trim().toLowerCase(Locale.US);
            if (!words[indexes.get(step)].equals(typed)) {
                alert(Alert.AlertType.ERROR, "Mismatch", "Word does not match. Check your backup.");
                return;
            }
            if (step + 1 >= indexes.size()) {
                String pin = askNewPin();
                if (pin == null) return;
                try {
                    storage.createNew(mnemonic, "", pin);
                    sessionPin = pin;
                    openMain();
                } catch (Exception ex) {
                    alert(Alert.AlertType.ERROR, "Error", ex.getMessage());
                }
            } else {
                confirmStep(mnemonic, words, indexes, step + 1);
            }
        });
        VBox box = new VBox(12,
                titleLabel("Confirm your backup"),
                mutedLabel("Enter word #" + wordNum + " of your recovery phrase"),
                input, next, back);
        box.setPadding(new Insets(28));
        setScene(box);
    }

    private void startImportFlow() {
        TextArea seed = new TextArea();
        seed.setPromptText("word1 word2 word3 …");
        seed.setPrefRowCount(5);
        seed.setWrapText(true);
        seed.setStyle(fieldStyle());
        PasswordField pass = new PasswordField();
        pass.setPromptText("Optional BIP39 passphrase");
        pass.setStyle(fieldStyle());
        Button go = primaryButton("Import wallet");
        Button back = secondaryButton("Back");
        back.setOnAction(e -> showOnboarding());
        go.setOnAction(e -> {
            String mnemonic = seed.getText().trim().replaceAll("\\s+", " ").toLowerCase(Locale.US);
            if (!Bip39.isValid(mnemonic)) {
                alert(Alert.AlertType.ERROR, "Invalid seed", "The recovery phrase is not valid.");
                return;
            }
            String pin = askNewPin();
            if (pin == null) return;
            try {
                storage.createNew(mnemonic, pass.getText(), pin);
                sessionPin = pin;
                openMain();
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Error", ex.getMessage());
            }
        });
        VBox box = new VBox(12,
                titleLabel("Import from seed phrase"),
                mutedLabel("Enter your 12–24 word BIP39 recovery phrase."),
                seed, pass, go, back);
        box.setPadding(new Insets(28));
        setScene(scroll(box));
    }

    private String askNewPin() {
        TextInputDialog d1 = new TextInputDialog();
        d1.setTitle("Set wallet PIN");
        d1.setHeaderText("Choose a 6-digit PIN");
        d1.setContentText("PIN:");
        Optional<String> p1 = d1.showAndWait();
        if (p1.isEmpty() || !p1.get().trim().matches("\\d{6}")) {
            alert(Alert.AlertType.ERROR, "PIN", "PIN must be exactly 6 digits.");
            return null;
        }
        TextInputDialog d2 = new TextInputDialog();
        d2.setTitle("Confirm PIN");
        d2.setHeaderText("Re-enter your PIN");
        d2.setContentText("PIN:");
        Optional<String> p2 = d2.showAndWait();
        if (p2.isEmpty() || !p1.get().trim().equals(p2.get().trim())) {
            alert(Alert.AlertType.ERROR, "PIN", "PINs do not match.");
            return null;
        }
        return p1.get().trim();
    }

    // ---- main wallet UI ----

    private void openMain() {
        wallet = Wallet.fromMnemonic(storage.getMnemonic(), storage.getPassphrase(), new ApiClient());
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG + ";");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("Home", buildHome()),
                new Tab("Receive", buildReceive()),
                new Tab("Send", buildSend()),
                new Tab("Settings", buildSettings())
        );
        root.setCenter(tabs);
        setScene(root);
        refresh();
    }

    private VBox buildHome() {
        statusLabel = mutedLabel("connecting…");
        balanceLabel = new Label("0.00000000");
        balanceLabel.setFont(Font.font("System", FontWeight.BOLD, 34));
        balanceLabel.setTextFill(Color.web(ON));
        Label ticker = new Label("2X2");
        ticker.setTextFill(Color.web(GREEN));
        syncLabel = mutedLabel("");
        syncLabel.setVisible(false);
        activityBox = new VBox(6);
        activityBox.getChildren().add(mutedLabel("Loading…"));

        refreshButton = compactSecondary("Refresh");
        refreshButton.setOnAction(e -> refresh());
        HBox refreshRow = new HBox(refreshButton);
        refreshRow.setAlignment(Pos.CENTER);

        VBox bal = card(new VBox(6,
                mutedLabel("WALLET BALANCE (all addresses)"),
                balanceLabel, ticker, syncLabel));
        bal.setAlignment(Pos.CENTER);
        VBox act = card(new VBox(8, sectionTitle("Activity"), activityBox));

        VBox box = new VBox(16,
                row(titleLabel("2X2 Wallet"), statusLabel),
                bal, act, refreshRow);
        box.setPadding(new Insets(20));
        return box;
    }

    private VBox buildReceive() {
        addressLabel = new Label("…");
        addressLabel.setTextFill(Color.web(ON));
        addressLabel.setWrapText(true);
        addressLabel.setFont(Font.font("Monospaced", 13));
        qrView = new ImageView();
        qrView.setFitWidth(220);
        qrView.setFitHeight(220);
        qrView.setPreserveRatio(true);

        depositBalanceLabel = mutedLabel("Deposit address balance: …");
        Button copy = primaryButton("Copy");
        Button neu = secondaryButton("New address");
        copy.setOnAction(e -> {
            javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent();
            c.putString(addressLabel.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
            alert(Alert.AlertType.INFORMATION, "Copied",
                    "Address copied. Clear the clipboard when done.");
        });
        neu.setOnAction(e -> {
            storage.setReceiveIndex(storage.getReceiveIndex() + 1);
            persistQuiet();
            showAddress();
            refresh();
        });

        showAddress();
        VBox box = new VBox(16, sectionTitle("Receive"),
                card(new VBox(12, qrView, addressLabel, depositBalanceLabel, row(copy, neu))));
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.TOP_CENTER);
        return box;
    }

    private VBox buildSend() {
        toField = new TextField();
        toField.setPromptText("Recipient 2X2 address");
        toField.setStyle(fieldStyle());
        amountField = new TextField();
        amountField.setPromptText("Amount (2X2)");
        amountField.setStyle(fieldStyle());
        feeLabel = mutedLabel("Network fee applies");

        addressBookCombo = new ComboBox<>();
        addressBookCombo.setPromptText("Select saved address");
        addressBookCombo.setMaxWidth(Double.MAX_VALUE);
        addressBookCombo.setStyle(fieldStyle());
        addressBookCombo.setOnAction(e -> {
            DesktopStorage.Contact c = addressBookCombo.getValue();
            if (c != null && c.address != null) toField.setText(c.address);
        });
        reloadAddressBookCombo();

        TextField labelField = new TextField();
        labelField.setPromptText("Label (optional)");
        labelField.setStyle(fieldStyle());
        Button saveContact = compactSecondary("Save to book");
        saveContact.setOnAction(e -> {
            String addr = toField.getText().trim();
            if (!Address.isValid(addr)) {
                alert(Alert.AlertType.ERROR, "Address book", "Enter a valid recipient address first.");
                return;
            }
            try {
                storage.addContact(labelField.getText(), addr);
                persistQuiet();
                reloadAddressBookCombo();
                labelField.clear();
                alert(Alert.AlertType.INFORMATION, "Address book", "Address saved.");
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Address book", ex.getMessage());
            }
        });
        Button removeContact = compactSecondary("Remove");
        removeContact.setOnAction(e -> {
            DesktopStorage.Contact c = addressBookCombo.getValue();
            if (c == null || c.address == null) {
                alert(Alert.AlertType.WARNING, "Address book", "Select a saved address to remove.");
                return;
            }
            storage.removeContact(c.address);
            persistQuiet();
            reloadAddressBookCombo();
        });
        HBox bookActions = new HBox(10, saveContact, removeContact);
        bookActions.setAlignment(Pos.CENTER_LEFT);

        sendButton = compactPrimary("Send 2X2");
        sendButton.setOnAction(e -> confirmSend());
        HBox sendRow = new HBox(sendButton);
        sendRow.setAlignment(Pos.CENTER);

        VBox form = new VBox(10,
                mutedLabel("Address book"),
                addressBookCombo,
                labelField,
                bookActions,
                mutedLabel("Recipient"),
                toField,
                mutedLabel("Amount"),
                amountField,
                feeLabel,
                sendRow);
        VBox box = new VBox(16, sectionTitle("Send"), card(form));
        box.setPadding(new Insets(20));
        return box;
    }

    private void reloadAddressBookCombo() {
        if (addressBookCombo == null || storage == null) return;
        DesktopStorage.Contact selected = addressBookCombo.getValue();
        addressBookCombo.getItems().setAll(storage.getAddressBook());
        if (selected != null) {
            for (DesktopStorage.Contact c : addressBookCombo.getItems()) {
                if (selected.address != null && selected.address.equalsIgnoreCase(c.address)) {
                    addressBookCombo.setValue(c);
                    break;
                }
            }
        }
    }

    private VBox buildSettings() {
        Button backup = secondaryButton("Backup seed");
        Button delete = secondaryButton("Delete wallet");
        Button website = primaryButton("2x2coin.com");
        backup.setOnAction(e -> {
            if (!requirePin("Reveal recovery phrase")) return;
            alert(Alert.AlertType.INFORMATION, "Recovery phrase", storage.getMnemonic());
        });
        delete.setOnAction(e -> {
            if (!requirePin("Authorize wallet deletion")) return;
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Delete wallet?");
            d.setHeaderText("Type DELETE to confirm");
            d.setContentText("Confirm:");
            Optional<String> ans = d.showAndWait();
            if (ans.isEmpty() || !"DELETE".equals(ans.get().trim())) {
                alert(Alert.AlertType.WARNING, "Cancelled", "Confirmation text did not match.");
                return;
            }
            try {
                storage.delete();
                sessionPin = null;
                wallet = null;
                showOnboarding();
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Error", ex.getMessage());
            }
        });
        website.setOnAction(e -> getHostServices().showDocument(NetworkParameters.WEBSITE_URL));

        VBox box = new VBox(12, sectionTitle("Settings"), backup, delete, website,
                mutedLabel("Data directory: ~/.2x2-wallet"));
        box.setPadding(new Insets(20));
        return box;
    }

    private void showAddress() {
        if (wallet == null) return;
        String addr = wallet.receiveAddress(storage.getReceiveIndex());
        addressLabel.setText(addr);
        qrView.setImage(QrUtil.encode(addr, 440));
    }

    private void refresh() {
        if (wallet == null) return;
        statusLabel.setText("refreshing…");
        final int recvIdx = storage.getReceiveIndex();
        final int changeIdx = storage.getChangeIndex();
        final String depositAddr = wallet.receiveAddress(recvIdx);
        pool.execute(() -> {
            ApiClient.Status s;
            try {
                s = wallet.api().getStatus();
                feePerKb = wallet.api().getFeePerKb();
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("offline");
                    statusLabel.setTextFill(Color.web("#E5484D"));
                    setActivityEmpty();
                });
                return;
            }

            final ApiClient.Status statusFinal = s;
            Platform.runLater(() -> {
                if (statusFinal.online) {
                    statusLabel.setText("● Mainnet " + statusFinal.blocks);
                    statusLabel.setTextFill(Color.web(GREEN));
                } else {
                    statusLabel.setText("offline");
                    statusLabel.setTextFill(Color.web("#E5484D"));
                }
                feeLabel.setText("Fee rate: " + Amounts.satToCoins(feePerKb) + " 2X2 / kB");
            });

            try {
                ApiClient.Balance depositBal = wallet.api().getBalance(depositAddr);
                ApiClient.Balance b0 = recvIdx == 0 ? depositBal
                        : wallet.api().getBalance(wallet.receiveAddress(0));
                final boolean scanningFinal =
                        ApiClient.shouldWarnIndexerSyncing(depositBal)
                                || ApiClient.shouldWarnIndexerSyncing(b0);
                final long depositSat = depositBal.confirmedSat;

                // Show deposit balance immediately so Activity/rate-limit failures cannot hide funds.
                Platform.runLater(() -> {
                    if (depositBalanceLabel != null) {
                        depositBalanceLabel.setText("Deposit address balance: "
                                + Amounts.satToCoins(depositSat) + " 2X2");
                    }
                    syncLabel.setVisible(scanningFinal);
                    syncLabel.setText(scanningFinal
                            ? "Indexer syncing… balance may be incomplete" : "");
                    if (depositSat > 0) {
                        balanceLabel.setText(Amounts.satToCoins(depositSat));
                    }
                });

                // UI refresh uses /balance for known addresses — avoids look-ahead UTXO storms
                // that exhaust the API rate limit before Activity can load.
                long bal = depositSat;
                try {
                    bal = wallet.getKnownBalancesSat(recvIdx, changeIdx);
                    if (bal <= 0 && depositSat > 0) bal = depositSat;
                } catch (Exception balEx) {
                    if (depositSat <= 0) throw balEx;
                }
                final long balFinal = bal;
                Platform.runLater(() -> balanceLabel.setText(Amounts.satToCoins(balFinal)));

                // Activity: merge network (receive+change) with local history so spent txs remain.
                try {
                    java.util.List<ApiClient.TxInfo> net =
                            wallet.listActivity(recvIdx, changeIdx);
                    for (ApiClient.TxInfo t : net) {
                        if (t.txid == null) continue;
                        // Do not force "in" — preserves outbound rows for the same txid (change out).
                        storage.rememberTx(t.txid, t.direction, t.amount, null);
                    }
                    persistQuiet();
                    java.util.List<ApiClient.TxInfo> merged = mergeActivity(storage.getTxHistory(), net);
                    if (merged.size() > 50) merged = merged.subList(0, 50);
                    final java.util.List<ApiClient.TxInfo> show = merged;
                    Platform.runLater(() -> setActivityItems(show));
                } catch (Exception actEx) {
                    java.util.List<ApiClient.TxInfo> localOnly = historyAsTxInfo(storage.getTxHistory());
                    Platform.runLater(() -> {
                        if (localOnly.isEmpty()) setActivityEmpty();
                        else setActivityItems(localOnly);
                    });
                }
            } catch (Exception ex) {
                Platform.runLater(this::setActivityEmpty);
            }
        });
    }

    private void setActivityEmpty() {
        if (activityBox == null) return;
        activityBox.getChildren().setAll(mutedLabel("No transactions yet"));
    }

    private void setActivityItems(java.util.List<ApiClient.TxInfo> items) {
        if (activityBox == null) return;
        if (items == null || items.isEmpty()) {
            setActivityEmpty();
            return;
        }
        java.util.List<javafx.scene.Node> rows = new java.util.ArrayList<>();
        for (ApiClient.TxInfo t : items) {
            String id = t.txid == null ? "" : t.txid;
            String shortId = id.length() > 18
                    ? id.substring(0, 10) + "…" + id.substring(id.length() - 6) : id;
            StringBuilder line = new StringBuilder(shortId);
            if (t.amount != null) {
                boolean out = t.direction != null && t.direction.equalsIgnoreCase("out");
                line.append(out ? "   −" : "   +").append(t.amount).append(" 2X2");
            }
            Label text = mutedLabel(line.toString());
            text.setWrapText(true);
            HBox.setHgrow(text, Priority.ALWAYS);
            Hyperlink link = new Hyperlink("Explorer");
            link.setTextFill(Color.web(GREEN));
            final String txid = id;
            link.setOnAction(e -> getHostServices().showDocument(NetworkParameters.explorerTxUrl(txid)));
            HBox row = new HBox(10, text, link);
            row.setAlignment(Pos.CENTER_LEFT);
            rows.add(row);
        }
        activityBox.getChildren().setAll(rows);
    }

    private static List<ApiClient.TxInfo> historyAsTxInfo(List<DesktopStorage.HistoryEntry> hist) {
        List<ApiClient.TxInfo> out = new ArrayList<>();
        for (DesktopStorage.HistoryEntry e : hist) {
            ApiClient.TxInfo t = new ApiClient.TxInfo();
            t.txid = e.txid;
            t.amount = e.amount;
            t.direction = e.direction;
            out.add(t);
        }
        return out;
    }

    private static List<ApiClient.TxInfo> mergeActivity(List<DesktopStorage.HistoryEntry> hist,
                                                        List<ApiClient.TxInfo> net) {
        java.util.LinkedHashMap<String, ApiClient.TxInfo> map = new java.util.LinkedHashMap<>();
        for (DesktopStorage.HistoryEntry e : hist) {
            if (e.txid == null) continue;
            ApiClient.TxInfo t = new ApiClient.TxInfo();
            t.txid = e.txid;
            t.amount = e.amount;
            t.direction = e.direction == null ? "in" : e.direction;
            map.put(e.txid.toLowerCase(Locale.ROOT), t);
        }
        if (net != null) {
            for (ApiClient.TxInfo t : net) {
                if (t.txid == null) continue;
                String key = t.txid.toLowerCase(Locale.ROOT);
                ApiClient.TxInfo existing = map.get(key);
                if (existing == null) {
                    if (t.direction == null) t.direction = "in";
                    map.put(key, t);
                } else {
                    if (existing.amount == null) existing.amount = t.amount;
                    if (existing.direction == null) existing.direction = "in";
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    private void confirmSend() {
        String to = toField.getText().trim();
        String amtStr = amountField.getText().trim();
        if (!Address.isValid(to)) {
            alert(Alert.AlertType.ERROR, "Invalid address", "Recipient address is not valid.");
            return;
        }
        long amountSat;
        try {
            amountSat = Amounts.coinsToSat(amtStr);
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Invalid amount", "Enter a valid 2X2 amount.");
            return;
        }
        if (amountSat <= 0) {
            alert(Alert.AlertType.ERROR, "Invalid amount", "Amount must be positive.");
            return;
        }
        setSending(true);
        final long amountFinal = amountSat;
        final int useChange = storage.getChangeIndex();
        final int recvIdx = storage.getReceiveIndex();
        pool.execute(() -> {
            try {
                var built = wallet.createTransaction(to, amountFinal, useChange, recvIdx, useChange);
                Platform.runLater(() -> {
                    String msg = "Send " + Amounts.satToCoins(amountFinal) + " 2X2\n"
                            + "To: " + to + "\n"
                            + "Network fee: " + Amounts.satToCoins(built.feeSat) + " 2X2\n"
                            + "Total debit: "
                            + Amounts.satToCoins(amountFinal + built.feeSat) + " 2X2";
                    Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.CANCEL,
                            new ButtonType("Send", ButtonBar.ButtonData.OK_DONE));
                    a.setTitle("Confirm payment");
                    a.setHeaderText("Confirm payment");
                    Optional<ButtonType> res = a.showAndWait();
                    if (res.isEmpty() || res.get().getButtonData() != ButtonBar.ButtonData.OK_DONE) {
                        setSending(false);
                        return;
                    }
                    if (!requirePin("Authorize payment")) {
                        setSending(false);
                        return;
                    }
                    doSend(to, amountFinal, useChange, recvIdx);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setSending(false);
                    String msg = ApiException.isRateLimited(ex)
                            ? "Server is busy. Wait a few seconds, then try Send again."
                            : ApiException.userMessage(ex);
                    alert(Alert.AlertType.ERROR, "Cannot build transaction", msg);
                });
            }
        });
    }

    private void doSend(String to, long amountSat, int useChange, int recvIdx) {
        pool.execute(() -> {
            try {
                String txid = wallet.send(to, amountSat, useChange, recvIdx, useChange);
                storage.setChangeIndex(useChange + 1);
                storage.rememberTx(txid, "out", Amounts.satToCoins(amountSat), to);
                persistQuiet();
                Platform.runLater(() -> {
                    setSending(false);
                    toField.clear();
                    amountField.clear();
                    alert(Alert.AlertType.INFORMATION, "Sent", "Transaction broadcast:\n" + txid);
                    refresh();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setSending(false);
                    toField.clear();
                    amountField.clear();
                    alert(Alert.AlertType.ERROR, "Send failed",
                            ApiException.userMessage(ex)
                                    + "\n\nDetails were saved to:\n"
                                    + AppLog.logFileDisplayPath());
                });
            }
        });
    }

    private void setSending(boolean busy) {
        if (sendButton == null) return;
        sendButton.setDisable(busy);
        if (busy) {
            sendButton.setText("Sending…");
            sendButton.setStyle("-fx-background-color: #4A5568; -fx-text-fill: #CBD5E1; -fx-font-weight: bold;");
        } else {
            sendButton.setText("Send 2X2");
            sendButton.setStyle("-fx-background-color: " + GREEN
                    + "; -fx-text-fill: #041008; -fx-font-weight: bold;");
        }
        if (toField != null) toField.setDisable(busy);
        if (amountField != null) amountField.setDisable(busy);
        if (addressBookCombo != null) addressBookCombo.setDisable(busy);
    }

    private boolean requirePin(String reason) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Enter PIN");
        d.setHeaderText(reason);
        d.setContentText("PIN:");
        Optional<String> pin = d.showAndWait();
        if (pin.isEmpty()) return false;
        if (!storage.verifyPin(pin.get().trim())) {
            alert(Alert.AlertType.ERROR, "PIN", "Incorrect PIN");
            return false;
        }
        return true;
    }

    private void persistQuiet() {
        try {
            if (sessionPin != null) storage.save(sessionPin);
        } catch (Exception ignored) {
        }
    }

    // ---- UI helpers ----

    private void setScene(javafx.scene.Parent root) {
        StackPane wrap = new StackPane(root);
        wrap.setStyle("-fx-background-color: " + BG + ";");
        Scene scene = new Scene(wrap, 820, 640);
        stage.setScene(scene);
    }

    private static ScrollPane scroll(VBox box) {
        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG + ";");
        return sp;
    }

    private static Label titleLabel(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("System", FontWeight.BOLD, 28));
        l.setTextFill(Color.web(GREEN));
        return l;
    }

    private static Label sectionTitle(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("System", FontWeight.BOLD, 18));
        l.setTextFill(Color.web(ON));
        return l;
    }

    private static Label mutedLabel(String t) {
        Label l = new Label(t);
        l.setTextFill(Color.web(MUTED));
        l.setWrapText(true);
        return l;
    }

    private static Button primaryButton(String t) {
        Button b = new Button(t);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle("-fx-background-color: " + GREEN + "; -fx-text-fill: #041008; -fx-font-weight: bold;");
        return b;
    }

    private static Button secondaryButton(String t) {
        Button b = new Button(t);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle("-fx-background-color: " + SURFACE + "; -fx-text-fill: " + ON + ";");
        return b;
    }

    private static Button compactPrimary(String t) {
        Button b = new Button(t);
        b.setPrefWidth(148);
        b.setMinWidth(148);
        b.setMaxWidth(148);
        b.setStyle("-fx-background-color: " + GREEN + "; -fx-text-fill: #041008; -fx-font-weight: bold;");
        return b;
    }

    private static Button compactSecondary(String t) {
        Button b = new Button(t);
        b.setPrefWidth(132);
        b.setMinWidth(120);
        b.setMaxWidth(160);
        b.setStyle("-fx-background-color: " + SURFACE + "; -fx-text-fill: " + ON + ";");
        return b;
    }

    private static VBox card(javafx.scene.Node content) {
        VBox card = new VBox(content);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 10;");
        return card;
    }

    private static HBox row(javafx.scene.Node... nodes) {
        HBox h = new HBox(10, nodes);
        h.setAlignment(Pos.CENTER_LEFT);
        for (javafx.scene.Node n : nodes) {
            HBox.setHgrow(n, Priority.ALWAYS);
            if (n instanceof Button) ((Button) n).setMaxWidth(Double.MAX_VALUE);
        }
        return h;
    }

    private static String fieldStyle() {
        return "-fx-background-color: " + SURFACE + "; -fx-text-fill: " + ON
                + "; -fx-prompt-text-fill: " + MUTED + ";";
    }

    private static void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(title);
        a.showAndWait();
    }

    private static String formatSeed(String[] words) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            sb.append(String.format(Locale.US, "%2d. %s", i + 1, words[i]));
            if (i < words.length - 1) sb.append(i % 2 == 1 ? "\n" : "    ");
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
