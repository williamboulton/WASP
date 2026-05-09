#define AppName "WASP"
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "."
#endif
#ifndef OutputDir
  #define OutputDir "."
#endif
#define AppIconPath AddBackslash(SourcePath) + "Native\\src\\assets\\wasp.ico"

[Setup]
AppId={{2F61B3C4-8A7B-4F0E-8D9A-75EEAFD87E7D}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=WASP
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
OutputBaseFilename=wasp-{#AppVersion}-setup
OutputDir={#OutputDir}
Compression=lzma
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
SetupIconFile={#AppIconPath}
UninstallDisplayIcon={app}\wasp.ico

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion
Source: "{#AppIconPath}"; DestDir: "{app}"; DestName: "wasp.ico"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\WASPTray.exe"; IconFilename: "{app}\wasp.ico"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\WASPTray.exe"; IconFilename: "{app}\wasp.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\system_metrics.exe"; Parameters: "install"; Flags: runhidden waituntilterminated
Filename: "sc.exe"; Parameters: "sdset SystemMetricsService ""D:(A;;CCLCSWRPWPDTLOCRRC;;;SY)(A;;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;BA)(A;;CCLCSWRPWPLOCRRC;;;IU)(A;;CCLCSWLOCRRC;;;SU)"""; Flags: runhidden waituntilterminated
Filename: "{app}\WASPTray.exe"; Description: "Launch {#AppName}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "sc.exe"; Parameters: "stop SystemMetricsService"; Flags: runhidden waituntilterminated; RunOnceId: "StopSystemMetricsService"
Filename: "{app}\system_metrics.exe"; Parameters: "remove"; Flags: runhidden waituntilterminated; RunOnceId: "RemoveSystemMetricsService"

[UninstallDelete]
; Backend database and runtime artifacts are written outside {app}, so delete them explicitly.
Type: files; Name: "{%USERPROFILE}\wasp.db"
Type: files; Name: "{commonappdata}\WASP\system_metrics_output.json"
Type: files; Name: "{localappdata}\WASP\system_metrics_output.json"
Type: files; Name: "{localappdata}\WASP\logs\system_metrics.log"
Type: files; Name: "{localappdata}\WASP\logs\send_client.log"
Type: dirifempty; Name: "{commonappdata}\WASP"
Type: dirifempty; Name: "{localappdata}\WASP\logs"
Type: dirifempty; Name: "{localappdata}\WASP"
