# Communication Buddy Installation Guide

Follow these steps to complete the implementation and test the new Settings and Custom Word features in your Communication Buddy app.

## Prerequisites

- Android Studio installed
- JDK 23 installed and JAVA_HOME properly configured
- Android device or emulator for testing

## Steps to Complete Implementation

1. **Open project in Android Studio**:
   ```
   File > Open > Select your project directory
   ```

2. **Sync Gradle files**:
   ```
   Tools > Android > Sync Project with Gradle Files
   ```

3. **Resolve any remaining dependencies**:
   If Android Studio detects any missing dependencies, install them through the SDK Manager.

4. **Build the project**:
   ```
   Build > Make Project (or Ctrl+F9)
   ```

5. **Run the app**:
   ```
   Run > Run 'app' (or Shift+F10)
   ```

## Testing the Features

### Settings Screen

1. Launch the app on your device/emulator
2. Tap the gear icon in the top right corner
3. Test the settings functionality:
   - **Speech Settings**:
     - Test voice selection:
       - Tap on "Voice" 
       - Choose a language from the list
       - Select a specific voice from that language
       - Confirm it plays a sample automatically
       - Test pressing the "Test Voice" button
     - Adjust speech rate and pitch sliders
     - Ensure changes take effect when speaking words
   - **Appearance Settings**:
     - Test grid size selection (3×3 to 7×7)
     - Verify that changing grid size updates the main screen layout
     - Test text size options (Small, Medium, Large, Extra Large)
     - Enable high contrast mode and verify visual changes
   - **Behavior Settings**:
     - Toggle auto-speak and verify functionality
   - **Settings Persistence**:
     - Make changes, tap "Save" button in the top-right
     - Exit the app completely and relaunch
     - Verify that all settings are remembered, including voice selection

### Testing Multiple Languages

1. In the Settings screen, test voice selection with at least 3 different languages
2. For each language:
   - Verify multiple voices are available
   - Test that samples play correctly
   - Save and confirm the voice is used on the main screen
3. Test with non-Latin character languages if available (e.g., Chinese, Japanese, Arabic)
4. Check for any language-specific pronunciation issues with standard words

### Custom Word Management

1. From the settings screen, tap "Add New Word"
2. Test creating a new word:
   - Enter a word name
   - Select a category
   - Add an image using:
     - Camera (requires device with camera)
     - Gallery (select an image from your device)
   - Save the word and verify it appears in the correct category

3. Test editing a word:
   - From settings, tap "Edit Words"
   - Select a word to edit
   - Change its text or category
   - Replace its image
   - Save and verify changes

### Testing Category-Specific Images

1. Create the same word in two different categories (e.g., "chicken" in both "Animals" and "Food")
2. Add different images to each instance
3. Switch between categories and verify that the correct image is displayed

### Testing Appearance Settings Impact

1. Change grid size to different values (3×3, 5×5, 7×7)
   - Verify that the number of word cards per row changes accordingly
   - Observe how this affects the overall usability

2. Change text size between options
   - Verify that text labels on word cards update to reflect the new size
   - Check readability at each setting

3. Test high contrast mode
   - Enable high contrast mode and observe visual changes
   - Verify that card backgrounds and text areas have improved contrast
   - Test with different text sizes to ensure readability

## Troubleshooting

### Build Issues

- **Java errors**: Ensure JAVA_HOME points to a valid JDK-23 installation
  ```
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-23"
  ```

- **Dependency issues**: Make sure all dependencies in build.gradle.kts are properly resolved
  ```
  File > Invalidate Caches / Restart
  ```

### Settings Not Saving

- If settings aren't persisting after app restart:
  1. Make sure you're tapping the "Save" button after changing settings
  2. Check that permissions for data storage are granted
  3. Try clearing app data and cache, then set up again

### Voice Issues

- If voices aren't working or no voices are available:
  1. Make sure your device has text-to-speech engines installed
  2. Try installing additional language packs from the Android Settings app
  3. Ensure your device has an internet connection during first setup
  4. Check that the app has permissions to access the internet if needed

### Camera Permissions

- If camera features don't work, ensure your app has camera permissions:
  1. Go to device Settings > Apps > Communication Buddy > Permissions
  2. Enable Camera permission

### Image Loading Issues

- If custom images don't appear:
  1. Check that storage permissions are granted
  2. Verify that images are being saved to the correct location
  3. Try restarting the app

## Production Considerations

Before releasing to production:

1. Update app version in build.gradle.kts
2. Test on multiple device sizes and Android versions
3. Test with a variety of languages and TTS engines
4. Consider implementing a backup/restore feature for user data
5. Add analytics for feature usage tracking
6. Consider adding a settings reset option 