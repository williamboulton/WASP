#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shellapi.h>

#include <cstdlib>
#include <string>

namespace {
constexpr UINT WMAPP_TRAY_ICON = WM_APP + 1;
constexpr UINT ID_TRAY_ICON = 1001;
constexpr UINT ID_MENU_OPEN = 2001;
constexpr UINT ID_MENU_EXIT = 2002;
constexpr UINT_PTR ID_TIMER_OPEN_BROWSER = 3001;
const char* kWindowClass = "WASPTrayWindowClass";
const char* kMutexName = "Global\\WASPTraySingleton";
const char* kMetricsServiceName = "SystemMetricsService";

struct AppState {
  HINSTANCE instance = nullptr;
  HWND window = nullptr;
  NOTIFYICONDATAA trayIcon = {};
  HICON customTrayIcon = nullptr;
  HANDLE backendProcess = nullptr;
  HANDLE senderProcess = nullptr;
};

std::string Quote(const std::string& value) {
  return "\"" + value + "\"";
}

std::string GetExeDirectory() {
  char exePath[MAX_PATH] = {};
  if (GetModuleFileNameA(nullptr, exePath, MAX_PATH) == 0) {
    return ".\\";
  }

  std::string path(exePath);
  const std::size_t slash = path.find_last_of("\\/");
  if (slash == std::string::npos) {
    return ".\\";
  }
  return path.substr(0, slash + 1);
}

HICON LoadCustomTrayIcon() {
  const std::string exeDir = GetExeDirectory();
  const std::string iconCandidates[] = {
    exeDir + "assets\\wasp.ico",       // packaged alongside exe
    exeDir + "wasp.ico",               // packaged flat next to exe
    exeDir + "..\\src\\assets\\wasp.ico", // local dev from Native/build
  };

  for (const auto& iconPath : iconCandidates) {
    HICON icon = reinterpret_cast<HICON>(
      LoadImageA(nullptr, iconPath.c_str(), IMAGE_ICON, 0, 0, LR_LOADFROMFILE | LR_DEFAULTSIZE)
    );
    if (icon) {
      return icon;
    }
  }
  return nullptr;
}

std::string GetDataDirectory() {
  const char* localAppData = std::getenv("LOCALAPPDATA");
  if (!localAppData || localAppData[0] == '\0') {
    return ".\\";
  }
  return std::string(localAppData) + "\\WASP";
}

std::string GetProgramDataMetricsPath() {
  const char* programData = std::getenv("ProgramData");
  if (!programData || programData[0] == '\0') {
    return GetExeDirectory() + "system_metrics_output.json";
  }

  const std::string dir = std::string(programData) + "\\WASP";
  CreateDirectoryA(dir.c_str(), nullptr);
  return dir + "\\system_metrics_output.json";
}

bool StartHiddenProcess(const std::string& command, HANDLE* outHandle) {
  STARTUPINFOA startupInfo = {};
  startupInfo.cb = sizeof(startupInfo);
  startupInfo.dwFlags = STARTF_USESHOWWINDOW;
  startupInfo.wShowWindow = SW_HIDE;

  PROCESS_INFORMATION processInfo = {};
  std::string mutableCommand = command;

  const BOOL ok = CreateProcessA(
    nullptr,
    mutableCommand.data(),
    nullptr,
    nullptr,
    FALSE,
    CREATE_NO_WINDOW,
    nullptr,
    nullptr,
    &startupInfo,
    &processInfo
  );

  if (!ok) {
    return false;
  }

  CloseHandle(processInfo.hThread);
  if (outHandle) {
    *outHandle = processInfo.hProcess;
  } else {
    CloseHandle(processInfo.hProcess);
  }
  return true;
}

void RunHiddenAndWait(const std::string& command) {
  HANDLE process = nullptr;
  if (!StartHiddenProcess(command, &process)) {
    return;
  }
  WaitForSingleObject(process, 8000);
  CloseHandle(process);
}

void KillImage(const char* imageName) {
  std::string command = std::string("cmd.exe /c taskkill /IM ") + imageName + " /F >nul 2>nul";
  RunHiddenAndWait(command);
}

void StopProcess(HANDLE* handle) {
  if (!handle || !*handle) {
    return;
  }
  const DWORD waitResult = WaitForSingleObject(*handle, 100);
  if (waitResult == WAIT_TIMEOUT) {
    TerminateProcess(*handle, 0);
    WaitForSingleObject(*handle, 1500);
  }
  CloseHandle(*handle);
  *handle = nullptr;
}

void OpenFrontend() {
  ShellExecuteA(nullptr, "open", "http://localhost:8080/", nullptr, nullptr, SW_SHOWNORMAL);
}

bool IsMetricsServiceInstalled() {
  SC_HANDLE scm = OpenSCManagerA(nullptr, nullptr, SC_MANAGER_CONNECT);
  if (!scm) {
    return false;
  }

  SC_HANDLE service = OpenServiceA(scm, kMetricsServiceName, SERVICE_QUERY_STATUS);
  const bool installed = (service != nullptr);
  if (service) {
    CloseServiceHandle(service);
  }
  CloseServiceHandle(scm);
  return installed;
}

bool EnsureMetricsServiceInstalled(const std::string& appDir) {
  if (IsMetricsServiceInstalled()) {
    return true;
  }
  RunHiddenAndWait(Quote(appDir + "system_metrics.exe") + " install");
  return IsMetricsServiceInstalled();
}

bool StartMetricsService() {
  SC_HANDLE scm = OpenSCManagerA(nullptr, nullptr, SC_MANAGER_CONNECT);
  if (!scm) {
    return false;
  }

  SC_HANDLE queryHandle = OpenServiceA(
    scm,
    kMetricsServiceName,
    SERVICE_QUERY_STATUS
  );
  if (!queryHandle) {
    CloseServiceHandle(scm);
    return false;
  }

  SERVICE_STATUS_PROCESS status = {};
  DWORD bytesNeeded = 0;
  bool ok = QueryServiceStatusEx(
    queryHandle,
    SC_STATUS_PROCESS_INFO,
    reinterpret_cast<LPBYTE>(&status),
    sizeof(status),
    &bytesNeeded
  ) != FALSE;

  if (ok && (status.dwCurrentState == SERVICE_RUNNING || status.dwCurrentState == SERVICE_START_PENDING)) {
    CloseServiceHandle(queryHandle);
    CloseServiceHandle(scm);
    return true;
  }

  CloseServiceHandle(queryHandle);

  SC_HANDLE startHandle = OpenServiceA(
    scm,
    kMetricsServiceName,
    SERVICE_START
  );
  if (!startHandle) {
    CloseServiceHandle(scm);
    return false;
  }

  if (!StartServiceA(startHandle, 0, nullptr)) {
    const DWORD err = GetLastError();
    if (err != ERROR_SERVICE_ALREADY_RUNNING) {
      CloseServiceHandle(startHandle);
      CloseServiceHandle(scm);
      return false;
    }
  }

  CloseServiceHandle(startHandle);
  CloseServiceHandle(scm);
  return true;
}

DWORD GetMetricsServiceState(SC_HANDLE service) {
  SERVICE_STATUS_PROCESS status = {};
  DWORD bytesNeeded = 0;
  if (!QueryServiceStatusEx(
        service,
        SC_STATUS_PROCESS_INFO,
        reinterpret_cast<LPBYTE>(&status),
        sizeof(status),
        &bytesNeeded
      )) {
    return SERVICE_STOPPED;
  }
  return status.dwCurrentState;
}

void StopMetricsService() {
  SC_HANDLE scm = OpenSCManagerA(nullptr, nullptr, SC_MANAGER_CONNECT);
  if (!scm) {
    return;
  }

  SC_HANDLE service = OpenServiceA(
    scm,
    kMetricsServiceName,
    SERVICE_STOP | SERVICE_QUERY_STATUS
  );
  if (!service) {
    CloseServiceHandle(scm);
    return;
  }

  DWORD state = GetMetricsServiceState(service);
  if (state == SERVICE_STOPPED) {
    CloseServiceHandle(service);
    CloseServiceHandle(scm);
    return;
  }

  SERVICE_STATUS status = {};
  if (!ControlService(service, SERVICE_CONTROL_STOP, &status)) {
    const DWORD err = GetLastError();
    // In some user contexts, the direct stop request can fail even though
    // shell-level service control still succeeds.
    if (err != ERROR_SERVICE_NOT_ACTIVE) {
      RunHiddenAndWait("sc.exe stop SystemMetricsService >nul 2>nul");
    }
  }

  // Wait up to ~10s for SCM to report fully stopped.
  for (int i = 0; i < 50; ++i) {
    state = GetMetricsServiceState(service);
    if (state == SERVICE_STOPPED) {
      break;
    }
    Sleep(200);
  }

  CloseServiceHandle(service);
  CloseServiceHandle(scm);
}

bool StartWaspProcesses(AppState* app) {
  if (!app) {
    return false;
  }

  // Ensure relaunching from Start menu behaves predictably.
  KillImage("send_client.exe");
  KillImage("WASPBackend.exe");

  const std::string appDir = GetExeDirectory();
  if (!EnsureMetricsServiceInstalled(appDir)) {
    MessageBoxA(
      nullptr,
      "System metrics service is not installed. Reinstall WASP as Administrator.",
      "WASP",
      MB_ICONERROR | MB_OK
    );
    return false;
  }

  if (!StartMetricsService()) {
    MessageBoxA(
      nullptr,
      "Failed to start SystemMetricsService.",
      "WASP",
      MB_ICONERROR | MB_OK
    );
    return false;
  }

  SetEnvironmentVariableA(
    "WASP_METRICS_JSON",
    GetProgramDataMetricsPath().c_str()
  );

  const std::string backendExe = Quote(appDir + "WASPBackend.exe");
  const std::string senderExe = Quote(appDir + "send_client.exe");

  if (!StartHiddenProcess(backendExe, &app->backendProcess)) {
    return false;
  }

  if (!StartHiddenProcess(senderExe, &app->senderProcess)) {
    StopProcess(&app->backendProcess);
    return false;
  }

  return true;
}

void StopWaspProcesses(AppState* app) {
  if (!app) {
    return;
  }

  StopProcess(&app->senderProcess);
  StopProcess(&app->backendProcess);
  StopMetricsService();

  // Best-effort cleanup for any orphaned instances.
  KillImage("send_client.exe");
  KillImage("WASPBackend.exe");
}

void ShowTrayMenu(AppState* app) {
  if (!app || !app->window) {
    return;
  }

  HMENU menu = CreatePopupMenu();
  if (!menu) {
    return;
  }

  AppendMenuA(menu, MF_STRING, ID_MENU_OPEN, "Open WASP");
  AppendMenuA(menu, MF_SEPARATOR, 0, nullptr);
  AppendMenuA(menu, MF_STRING, ID_MENU_EXIT, "Close WASP");

  POINT cursor;
  GetCursorPos(&cursor);
  SetForegroundWindow(app->window);
  TrackPopupMenu(menu, TPM_RIGHTBUTTON, cursor.x, cursor.y, 0, app->window, nullptr);
  DestroyMenu(menu);
}
} // namespace

LRESULT CALLBACK WindowProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
  auto* app = reinterpret_cast<AppState*>(GetWindowLongPtr(hwnd, GWLP_USERDATA));

  switch (message) {
    case WM_CREATE: {
      auto* created = reinterpret_cast<CREATESTRUCT*>(lParam);
      app = reinterpret_cast<AppState*>(created->lpCreateParams);
      SetWindowLongPtr(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(app));
      if (!app) {
        return -1;
      }

      app->window = hwnd;
      app->trayIcon.cbSize = sizeof(app->trayIcon);
      app->trayIcon.hWnd = hwnd;
      app->trayIcon.uID = ID_TRAY_ICON;
      app->trayIcon.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
      app->trayIcon.uCallbackMessage = WMAPP_TRAY_ICON;
      app->customTrayIcon = LoadCustomTrayIcon();
      app->trayIcon.hIcon = app->customTrayIcon ? app->customTrayIcon : LoadIcon(nullptr, IDI_APPLICATION);
      lstrcpynA(app->trayIcon.szTip, "WASP - Running", sizeof(app->trayIcon.szTip));
      Shell_NotifyIconA(NIM_ADD, &app->trayIcon);

      if (!StartWaspProcesses(app)) {
        MessageBoxA(nullptr, "Failed to start WASP background processes.", "WASP", MB_ICONERROR | MB_OK);
        PostQuitMessage(1);
        return -1;
      }

      SetTimer(hwnd, ID_TIMER_OPEN_BROWSER, 3000, nullptr);
      return 0;
    }
    case WM_TIMER:
      if (wParam == ID_TIMER_OPEN_BROWSER) {
        KillTimer(hwnd, ID_TIMER_OPEN_BROWSER);
        OpenFrontend();
      }
      return 0;
    case WMAPP_TRAY_ICON:
      if (lParam == WM_RBUTTONUP || lParam == WM_CONTEXTMENU) {
        ShowTrayMenu(app);
      } else if (lParam == WM_LBUTTONUP || lParam == WM_LBUTTONDBLCLK) {
        OpenFrontend();
      }
      return 0;
    case WM_COMMAND:
      switch (LOWORD(wParam)) {
        case ID_MENU_OPEN:
          OpenFrontend();
          return 0;
        case ID_MENU_EXIT:
          DestroyWindow(hwnd);
          return 0;
        default:
          break;
      }
      break;
    case WM_DESTROY:
      if (app) {
        Shell_NotifyIconA(NIM_DELETE, &app->trayIcon);
        if (app->customTrayIcon) {
          DestroyIcon(app->customTrayIcon);
          app->customTrayIcon = nullptr;
        }
        StopWaspProcesses(app);
      }
      PostQuitMessage(0);
      return 0;
    default:
      break;
  }

  return DefWindowProc(hwnd, message, wParam, lParam);
}

int WINAPI WinMain(HINSTANCE instance, HINSTANCE, LPSTR, int) {
  HANDLE mutex = CreateMutexA(nullptr, FALSE, kMutexName);
  if (!mutex) {
    return 1;
  }

  if (GetLastError() == ERROR_ALREADY_EXISTS) {
    OpenFrontend();
    CloseHandle(mutex);
    return 0;
  }

  AppState app = {};
  app.instance = instance;

  WNDCLASSA windowClass = {};
  windowClass.lpfnWndProc = WindowProc;
  windowClass.hInstance = instance;
  windowClass.lpszClassName = kWindowClass;

  if (!RegisterClassA(&windowClass)) {
    CloseHandle(mutex);
    return 1;
  }

  HWND window = CreateWindowExA(
    0,
    kWindowClass,
    "WASP Tray",
    WS_OVERLAPPEDWINDOW,
    CW_USEDEFAULT,
    CW_USEDEFAULT,
    CW_USEDEFAULT,
    CW_USEDEFAULT,
    nullptr,
    nullptr,
    instance,
    &app
  );

  if (!window) {
    CloseHandle(mutex);
    return 1;
  }

  ShowWindow(window, SW_HIDE);

  MSG message = {};
  while (GetMessage(&message, nullptr, 0, 0) > 0) {
    TranslateMessage(&message);
    DispatchMessage(&message);
  }

  CloseHandle(mutex);
  return static_cast<int>(message.wParam);
}
