/*
 * 2x2-Wallet.exe — portable Windows GUI launcher (no install required).
 *
 * Cross-compiled on Linux with mingw-w64. Expects this layout next to the exe:
 *   jre\bin\javaw.exe
 *   javafx\*.jar
 *   lib\2x2-wallet-desktop.jar
 */
#define WIN32_LEAN_AND_MEAN

#include <windows.h>
#include <stdio.h>
#include <string.h>

#ifndef MAIN_CLASS
#define MAIN_CLASS L"com.x2x.desktop.MainApp"
#endif

#ifndef ADD_MODULES
#define ADD_MODULES L"javafx.base,javafx.controls,javafx.graphics"
#endif

static void dirname_inplace(wchar_t *path) {
  wchar_t *slash = wcsrchr(path, L'\\');
  if (slash == NULL) {
    slash = wcsrchr(path, L'/');
  }
  if (slash != NULL) {
    *slash = L'\0';
  } else {
    path[0] = L'.';
    path[1] = L'\0';
  }
}

static void show_error(const wchar_t *detail) {
  wchar_t buf[1024];
  _snwprintf(buf, 1024,
             L"Failed to start 2X2 Wallet.\n\n%s\n\n"
             L"Keep jre\\, javafx\\, and lib\\ next to 2x2-Wallet.exe "
             L"(unzip the portable package — no installer needed).\n"
             L"For details run 2x2-Wallet-Console.bat.",
             detail);
  MessageBoxW(NULL, buf, L"2X2 Wallet", MB_OK | MB_ICONERROR);
}

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, PWSTR pCmdLine, int nCmdShow) {
  (void)hInstance;
  (void)hPrevInstance;
  (void)nCmdShow;

  wchar_t dir[MAX_PATH];
  DWORD n = GetModuleFileNameW(NULL, dir, MAX_PATH);
  if (n == 0 || n >= MAX_PATH) {
    show_error(L"Could not resolve the executable path.");
    return 1;
  }
  dirname_inplace(dir);

  wchar_t javaw[MAX_PATH];
  _snwprintf(javaw, MAX_PATH, L"%s\\jre\\bin\\javaw.exe", dir);
  if (GetFileAttributesW(javaw) == INVALID_FILE_ATTRIBUTES) {
    show_error(L"Missing jre\\bin\\javaw.exe (bundled Java runtime).");
    return 1;
  }

  wchar_t jar[MAX_PATH];
  _snwprintf(jar, MAX_PATH, L"%s\\lib\\2x2-wallet-desktop.jar", dir);
  if (GetFileAttributesW(jar) == INVALID_FILE_ATTRIBUTES) {
    show_error(L"Missing lib\\2x2-wallet-desktop.jar.");
    return 1;
  }

  wchar_t fx[MAX_PATH];
  _snwprintf(fx, MAX_PATH, L"%s\\javafx", dir);
  if (GetFileAttributesW(fx) == INVALID_FILE_ATTRIBUTES) {
    show_error(L"Missing javafx\\ folder (JavaFX modules).");
    return 1;
  }

  /* CreateProcessW requires a writable command line buffer. */
  wchar_t cmdline[32768];
  int written = _snwprintf(
      cmdline, 32768,
      L"\"%s\" --module-path \"%s\" --add-modules %s -cp \"%s\" %s",
      javaw, fx, ADD_MODULES, jar, MAIN_CLASS);
  if (written < 0 || written >= 32768) {
    show_error(L"Internal error building the Java command line.");
    return 1;
  }

  if (pCmdLine != NULL && pCmdLine[0] != L'\0') {
    size_t used = wcslen(cmdline);
    _snwprintf(cmdline + used, 32768 - used, L" %s", pCmdLine);
  }

  STARTUPINFOW si;
  PROCESS_INFORMATION pi;
  ZeroMemory(&si, sizeof(si));
  ZeroMemory(&pi, sizeof(pi));
  si.cb = sizeof(si);

  if (!CreateProcessW(
          javaw,
          cmdline,
          NULL,
          NULL,
          FALSE,
          0,
          NULL,
          dir,
          &si,
          &pi)) {
    wchar_t detail[256];
    _snwprintf(detail, 256, L"CreateProcess failed (Win32 error %lu).", GetLastError());
    show_error(detail);
    return 1;
  }

  CloseHandle(pi.hThread);
  CloseHandle(pi.hProcess);
  return 0;
}
