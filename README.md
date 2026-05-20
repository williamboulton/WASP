# Windows Analysis Service & Processor (W.A.S.P.)

W.A.S.P. is a desktop monitoring app for Windows 11 that tracks and displays performance metrics in real-time. 

This was originally developed in collaboration with others as the capstone project for my Master's Degree in Computer Science/Software Development. After completing the original project, I wanted to continue working on W.A.S.P. in my spare time. This fork contains my continued development of W.A.S.P.

The original repository can be found at https://github.com/matt9527-marist/WASP.

## Download and Installation

1. Go to [GitHub Releases](../../releases).
2. Download the latest version of the WASP setup.
3. Run the installer.

## Using WASP
- Launch WASP by searching for it in the Windows Start menu.
- When WASP launches, the frontend will automatically open.
- Closing the frontend will not exit WASP. Metrics will still be collected in the background by the backend. To reopen the frontend after closing, left click the tray icon in the bottom right of your taskbar.
    - You can also manually navigate to localhost:8080 in your browser to reopen the frontend.
- To fully close out of WASP, right click the tray icon in the bottom right of your taskbar and click "Close WASP."

## Uninstall

1. Open Settings > Apps > Installed apps (or Control Panel > Uninstall a program).
2. Find WASP and choose Uninstall.
3. Follow the uninstall wizard to remove the app.
