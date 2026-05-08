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
UninstallDisplayIcon={app}\WASPBackend.exe

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\WASPTray.exe"; IconFilename: "{app}\WASPTray.exe"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\WASPTray.exe"; IconFilename: "{app}\WASPTray.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\WASPTray.exe"; Description: "Launch {#AppName}"; Flags: nowait postinstall skipifsilent
