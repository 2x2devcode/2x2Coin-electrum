# Keep Bouncy Castle and core wallet classes.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class com.x2x.core.** { *; }

# ZXing embedded scanner
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }

# Biometric / security-crypto
-keep class androidx.biometric.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn javax.annotation.**
