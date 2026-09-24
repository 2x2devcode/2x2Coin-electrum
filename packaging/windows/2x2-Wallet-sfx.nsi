; Self-contained 2X2 Wallet for Windows (64-bit).
; Embeds Liberica JRE Full + app JAR. Extracts to %LOCALAPPDATA%\2x2-Wallet and launches.
; No separate folders needed beside this .exe — one file distribution.
;
; Built by: compile-windows.sh  →  dist/windows/2x2-Wallet.exe

!ifndef APP_VERSION
  !define APP_VERSION "1.3.15"
!endif
!ifndef PAYLOAD_DIR
  !define PAYLOAD_DIR "2x2-Wallet-windows"
!endif
!ifndef OUT_FILE
  !define OUT_FILE "2x2-Wallet.exe"
!endif
!ifndef ICON_FILE
  !define ICON_FILE "2x2-Wallet.ico"
!endif

Unicode true
; NSIS Ubuntu packages ship x86 stubs (PE32). That is normal for installers and
; runs on Windows x64 under WoW64. The embedded JRE is amd64 — x64 Windows required.
; (Do not set "Target amd64-unicode" unless amd64 stubs are installed.)
SilentInstall silent
RequestExecutionLevel user
Name "2X2 Wallet"
OutFile "${OUT_FILE}"
Icon "${ICON_FILE}"
Caption "2X2 Wallet ${APP_VERSION}"
SetCompressor /SOLID lzma
VIProductVersion "${APP_VERSION}.0"
VIAddVersionKey "ProductName" "2X2 Wallet"
VIAddVersionKey "FileDescription" "2X2 Wallet (self-contained)"
VIAddVersionKey "CompanyName" "2x2coin"
VIAddVersionKey "FileVersion" "${APP_VERSION}"
VIAddVersionKey "ProductVersion" "${APP_VERSION}"
VIAddVersionKey "LegalCopyright" "2x2coin"

!include "LogicLib.nsh"
!include "x64.nsh"

Var AppDir
Var ExistingVer

Function .onInit
  ${IfNot} ${RunningX64}
    MessageBox MB_ICONSTOP|MB_OK "2X2 Wallet requires 64-bit Windows (x64)."
    Abort
  ${EndIf}
  SetRegView 64
  StrCpy $AppDir "$LOCALAPPDATA\2x2-Wallet"
FunctionEnd

Section "Run"
  StrCpy $ExistingVer ""
  IfFileExists "$AppDir\runtime\version.txt" 0 needs_extract
  IfFileExists "$AppDir\runtime\jre\bin\javaw.exe" 0 needs_extract
  IfFileExists "$AppDir\runtime\lib\2x2-wallet-desktop.jar" 0 needs_extract
  FileOpen $0 "$AppDir\runtime\version.txt" r
  FileRead $0 $ExistingVer
  FileClose $0
  ; Trim CR/LF
  Push $ExistingVer
  Call Trim
  Pop $ExistingVer
  ; Must match exactly — bump APP_VERSION whenever TLS pins / JAR change,
  ; otherwise a stale runtime under %LOCALAPPDATA% keeps old pins ("certificate pin mismatch").
  StrCmp $ExistingVer "${APP_VERSION}" launch needs_extract

  needs_extract:
    DetailPrint "Updating runtime to ${APP_VERSION}"
    RMDir /r "$AppDir\runtime"
    SetOutPath "$AppDir\runtime"
    File /r "${PAYLOAD_DIR}\*.*"
    FileOpen $0 "$AppDir\runtime\version.txt" w
    FileWrite $0 "${APP_VERSION}"
    FileClose $0

  launch:
    IfFileExists "$AppDir\runtime\jre\bin\javaw.exe" 0 fail_jre
    IfFileExists "$AppDir\runtime\lib\2x2-wallet-desktop.jar" 0 fail_jar
    Exec '"$AppDir\runtime\jre\bin\javaw.exe" --add-modules javafx.controls,javafx.graphics -jar "$AppDir\runtime\lib\2x2-wallet-desktop.jar"'
    Quit

  fail_jre:
    MessageBox MB_ICONSTOP|MB_OK "Failed to extract the bundled Java runtime."
    Quit
  fail_jar:
    MessageBox MB_ICONSTOP|MB_OK "Failed to extract 2x2-wallet-desktop.jar."
    Quit
SectionEnd

Function Trim
  Exch $R1
  Push $R2
  Loop:
    StrCpy $R2 "$R1" 1 -1
    StrCmp "$R2" " " TrimRight
    StrCmp "$R2" "$\r" TrimRight
    StrCmp "$R2" "$\n" TrimRight
    StrCmp "$R2" "$\t" TrimRight
    GoTo Done
  TrimRight:
    StrCpy $R1 "$R1" -1
    GoTo Loop
  Done:
    Pop $R2
    Exch $R1
FunctionEnd
