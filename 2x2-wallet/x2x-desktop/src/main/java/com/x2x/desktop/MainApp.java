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
    private Label syncLabel;
    private Label feeLabel;
    private Label addressLabel;
    private Label activityLabel;
    private ImageView qrView;
    private TextField toField;
    private TextField amountField;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
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
        activityLabel = mutedLabel("Loading…");
        activityLabel.setWrapText(true);

        Button refresh = secondaryButton("Refresh");
        refresh.setOnAction(e -> refresh());

        VBox bal = card(new VBox(6, mutedLabel("BALANCE"), balanceLabel, ticker, syncLabel));
        bal.setAlignment(Pos.CENTER);
        VBox act = card(new VBox(8, sectionTitle("Activity"), activityLabel));

        VBox box = new VBox(16,
                row(titleLabel("2X2 Wallet"), statusLabel),
                bal, act, refresh);
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
        });

        showAddress();
        VBox box = new VBox(16, sectionTitle("Receive"),
                card(new VBox(12, qrView, addressLabel, row(copy, neu))));
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
        Button send = primaryButton("Send 2X2");
        send.setOnAction(e -> confirmSend());
        VBox box = new VBox(16, sectionTitle("Send"),
                card(new VBox(10, toField, amountField, feeLabel, send)));
        box.setPadding(new Insets(20));
        return box;
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
        pool.execute(() -> {
            try {
                ApiClient.Status s = wallet.api().getStatus();
                feePerKb = wallet.api().getFeePerKb();
                ApiClient.Balance b0 = wallet.api().getBalance(wallet.receiveAddress(0));
                long bal = wallet.getBalanceSat();
                StringBuilder act = new StringBuilder();
                java.util.Set<String> seen = new java.util.HashSet<>();
                int scan = Math.max(wallet.lookAhead, storage.getReceiveIndex() + 1);
                for (int i = 0; i < scan; i++) {
                    for (ApiClient.TxInfo t : wallet.api().getTxs(wallet.receiveAddress(i))) {
                        String id = t.txid == null ? "" : t.txid;
                        if (id.isEmpty() || !seen.add(id)) continue;
                        if (act.length() > 0) act.append('\n');
                        String shortId = id.length() > 18
                                ? id.substring(0, 10) + "…" + id.substring(id.length() - 6) : id;
                        act.append(shortId);
                        if (t.amount != null) act.append("   ").append(t.amount).append(" 2X2");
                        if (seen.size() >= 12) break;
                    }
                    if (seen.size() >= 12) break;
                }
                String activity = act.length() == 0 ? "No transactions yet" : act.toString();
                Platform.runLater(() -> {
                    if (s.online) {
                        statusLabel.setText("● Mainnet " + s.blocks);
                        statusLabel.setTextFill(Color.web(GREEN));
                    } else {
                        statusLabel.setText("offline");
                        statusLabel.setTextFill(Color.web("#E5484D"));
                    }
                    balanceLabel.setText(Amounts.satToCoins(bal));
                    feeLabel.setText("Fee rate: " + Amounts.satToCoins(feePerKb) + " 2X2 / kB");
                    syncLabel.setVisible(b0.scanning);
                    syncLabel.setText(b0.scanning
                            ? "Indexer syncing… balance may be incomplete" : "");
                    activityLabel.setText(activity);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("offline");
                    statusLabel.setTextFill(Color.web("#E5484D"));
                    activityLabel.setText("Could not refresh: " + ex.getMessage());
                });
            }
        });
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
        final long amountFinal = amountSat;
        final int useChange = storage.getChangeIndex();
        pool.execute(() -> {
            try {
                var built = wallet.createTransaction(to, amountFinal, useChange);
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
                        return;
                    }
                    if (!requirePin("Authorize payment")) return;
                    doSend(to, amountFinal, useChange);
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                        alert(Alert.AlertType.ERROR, "Cannot build transaction", ex.getMessage()));
            }
        });
    }

    private void doSend(String to, long amountSat, int useChange) {
        pool.execute(() -> {
            try {
                String txid = wallet.send(to, amountSat, useChange);
                storage.setChangeIndex(useChange + 1);
                persistQuiet();
                Platform.runLater(() -> {
                    alert(Alert.AlertType.INFORMATION, "Sent", "Transaction broadcast:\n" + txid);
                    toField.clear();
                    amountField.clear();
                    refresh();
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                        alert(Alert.AlertType.ERROR, "Send failed", ex.getMessage()));
            }
        });
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
