# CST2335 Lab 6 - WearTV and AsyncTask

This Android TV slideshow downloads random cat pictures from CATAAS, caches each image in the app's private file directory, and reuses cached images whenever the same cat ID is returned. A bottom progress bar counts down until the next image.

## Run

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Select an Android TV AVD and run the `app` configuration.

The app requires internet access. The first display of a cat is downloaded; later displays of the same ID are loaded from local storage.

## Key implementation

- `MainActivity.CatImages` extends `AsyncTask<String, Integer, String>`.
- `doInBackground()` continuously requests CATAAS metadata, checks the cache, downloads only when needed, and publishes timer updates.
- `onProgressUpdate()` is the only place that updates the `ImageView` and `ProgressBar`.
- The task is cancelled in `onDestroy()` to avoid leaking the activity.
