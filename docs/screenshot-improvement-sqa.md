# In-game Screenshot Improvement SQA Evidence

## Improvement Scope

The screenshot improvement adds an in-game keyboard shortcut for capturing the current game screen without opening a modal window or interrupting gameplay.

- Default shortcut: `Alt+S`
- Output directory: `screenshots/`
- Output format: PNG
- Filename format: `screenshot-yyyy-MM-dd_HH-mm-ss.png`
- Duplicate handling: a numeric suffix is added when multiple screenshots are captured in the same second.

## Traceability

| Requirement | Implementation | Verification |
| --- | --- | --- |
| Capture the current game scene from a designated key | global `Alt+S` listener in `ShatteredPixelDungeon` | `ScreenshotTest.altSIsScreenshotShortcut` |
| Avoid accidental capture from normal movement | screenshot shortcut requires Alt to be held while pressing `S` | `ScreenshotTest.plainSIsNotScreenshotShortcut` |
| Avoid accidental capture from other Alt shortcuts | only `Alt+S` is accepted, not Alt alone or other Alt key combinations | `ScreenshotTest.otherAltKeysAreNotScreenshotShortcut`, `ScreenshotTest.altKeyAloneIsNotScreenshotShortcut` |
| Save the captured screen as an image file | `Screenshot.capture()` writes a PNG via libGDX `PixmapIO.writePNG` | Manual system test below |
| Do not interrupt game flow | Listener writes directly to disk and logs the saved path, with no window or scene change | Manual gameplay test below |
| Use a predictable recovery location | `Screenshot.pathFor(...)` stores files under `screenshots/` | `ScreenshotTest.screenshotPathUsesDedicatedDirectory` |
| Avoid overwriting screenshots | `Screenshot.filename(...)` appends a sequence when needed | `ScreenshotTest.filenameAddsSequenceWhenSameSecondAlreadyExists`, `ScreenshotTest.filenameAddsFirstSequenceForFirstDuplicate` |

## Automated Tests

Command:

```powershell
.\gradlew.bat core:test --console=plain
```

TDD red evidence:

- Before implementation, `core:test` failed because `Screenshot` did not exist.

Green evidence:

- After implementation, `core:test` passed.

Automated test cases:

- `filenameUsesTimestampWhenAvailable`
- `filenameAddsSequenceWhenSameSecondAlreadyExists`
- `filenameAddsFirstSequenceForFirstDuplicate`
- `filenameDoesNotAddSequenceForNegativeInput`
- `screenshotPathUsesDedicatedDirectory`
- `altSIsScreenshotShortcut`
- `plainSIsNotScreenshotShortcut`
- `otherAltKeysAreNotScreenshotShortcut`
- `altKeyAloneIsNotScreenshotShortcut`
- `lowercaseAndUppercaseSUseSameKeyCode`

These tests verify the deterministic parts of the feature without requiring an OpenGL context.

## Manual System Test

1. Run the desktop game:

   ```powershell
   .\gradlew.bat desktop:debug
   ```

2. Start or load a game.
3. Press `Alt+S`.
4. Confirm that gameplay remains active and no blocking dialog appears.
5. Confirm that a PNG file appears under the configured game data `screenshots/` directory.
6. Press `Alt+S` twice within the same second and confirm the second file receives a numeric suffix instead of overwriting the first file.

## Quality Attribute

This improvement targets usability and diagnosability. Players and developers can capture the current gameplay state quickly for bug reports, visual verification, or assignment evidence without leaving the game or disrupting the current scene.
