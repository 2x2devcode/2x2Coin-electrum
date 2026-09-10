; Portable launcher only — no files installed.
; Placed next to jre\ and lib\ inside the unzipped portable folder.
; Built by compile-windows.sh as 2x2-Wallet.exe (avoids custom mingw PE, which Kaspersky often quarantines).

!ifndef OUT_FILE
  !define OUT_FILE "2x2-Wallet.exe"
!endif
!ifndef ICON_FILE
  !define ICON_FILE "2x2-Wallet.ico"
!endif
!ifndef APP_VERSION
  !define APP_VERSION "1.3.2"
!endif

Unicode true
SilentInstall silent
RequestExecutionLevel user
Name "2X2 Wallet"
OutFile "${OUT_FILE}"
Icon "${ICON_FILE}"
Caption "2X2 Wallet"
VIProductVersion "${APP_VERSION}.0"
VIAddVersionKey "ProductName" "2X2 Wallet"
VIAddVersionKey "FileDescription" "2X2 Wallet portable launcher"
VIAddVersionKey "CompanyName" "2x2coin"
VIAddVersionKey "FileVersion" "${APP_VERSION}"
VIAddVersionKey "ProductVersion" "${APP_VERSION}"

Section
  ; $EXEDIR = folder containing this exe (portable root)
  IfFileExists "$EXEDIR\jre\bin\javaw.exe" 0 missing_jre
  IfFileExists "$EXEDIR\lib\2x2-wallet-desktop.jar" 0 missing_jar

  ; Liberica Full JRE already includes JavaFX — no separate javafx\ folder.
  ; Detached start so the launcher exits immediately.
  Exec '"$EXEDIR\jre\bin\javaw.exe" --add-modules javafx.controls,javafx.graphics -jar "$EXEDIR\lib\2x2-wallet-desktop.jar"'
  Quit

  missing_jre:
    MessageBox MB_ICONSTOP|MB_OK "Missing jre\bin\javaw.exe.$\r$\nUnzip the full 2x2-Wallet-windows folder (do not run this exe alone)."
    Quit

  missing_jar:
    MessageBox MB_ICONSTOP|MB_OK "Missing lib\2x2-wallet-desktop.jar.$\r$\nUnzip the full portable package."
    Quit
SectionEnd
