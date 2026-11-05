# Image Sorter & Downloader

A small Java Swing desktop application based on [the app spec](docs/app-spec.txt).
Requires JDK 17 or newer; no third-party dependencies or build-tool installation.

The ImageFlow desktop UI uses a dark theme with teal accents, separate Sorter and
Downloader workspaces, clickable pattern tags, a live sample folder tree, and a
timestamped activity table with progress and completion counts. Settings scroll
on smaller windows. The preview uses illustrative tags and does not scan or move
files. Sorting currently uses one output destination, moves files, and prompts
for duplicate decisions.

From PowerShell in the project folder:

```powershell
.\build.ps1 -Run
```

Build only with `.\build.ps1`, or build and run service smoke tests with
`.\build.ps1 -Test`. Run the packaged application with
`java -jar build/images-sorter-downloader.jar`.

- **Sort images:** choose different source and output folders and a Tags pattern,
  such as `{{artist}}\{{album}}\{{character}} - {{##}}`.
  Tags are determined separately for each image using a local ComfyUI vision workflow.
  Supported tags: `series`, `character`, `content`, `artist`, optional `album`,
  and `filename` (original stem). Multiple recognized characters become `group`.
  Use one to nine `#` characters inside double braces for zero-padded numbering.
  Numbering follows source filename order, including skipped images. Both slash
  styles separate folders; the source extension is added automatically.
  Missing or invalid recognition opens a form on the left and image preview on
  the right. Enter `Unknown` for properties that cannot be identified, or skip.
  Exact duplicates (SHA-256 of file contents) and filename conflicts show both
  images with keep-both, replace-existing, and skip-incoming choices. Keep-both
  adds a numeric suffix when needed. Successful sorting **moves** the source;
  skipping preserves it. Only immediate source files are scanned; supported
  extensions: JPG/JPEG, PNG, GIF, BMP, WebP, TIFF/TIF. Java's built-in preview
  support varies by format; unsupported previews show the filename instead.
  The activity monitor and progress bar update as images are processed.

  **ComfyUI setup:** start your local ComfyUI server (default URL
  `http://127.0.0.1:8188`) and install a vision model and its required nodes.
  Build a workflow that reads an image, identifies the properties above, and
  exposes a text output through an output node. Export the workflow in **API
  format**, replace the image input filename with `{{image}}`, then select this
  JSON file and enter the text output node ID in the sorter. The node must expose
  a `text` string or list of strings in execution history; the first string must
  be a JSON object, without markdown fences, for example:
  `{"series":"Example","character":["Hero","Friend"],"content":"portrait","artist":"Unknown","album":"Example"}`.
  The app uploads each image, submits the workflow, and polls execution history.
  Requests time out after 45 seconds; tagging waits up to five minutes per image.
  Unavailable ComfyUI, workflow errors, or incomplete tags fall back to manual
  entry. Models and custom nodes are supplied by your ComfyUI installation;
  this Java application does not install them.

- **Download images:** select one or more UTF-8 text files and an output folder.
  Each line contains a direct HTTP(S) image URL; blank lines and `#` comments are
  ignored. Downloads use unique names and the response image Content-Type for the
  extension. Supported types: JPEG, PNG, GIF, WebP, BMP, TIFF, AVIF, SVG.
  Requests have a 45-second timeout and a 25 MB response limit. Individual URL
  failures appear in the activity log and do not stop remaining URLs.

Both operations run in a background worker to keep the interface responsive.
Wait for completion before closing the application; resumable jobs are not implemented.

Example URL list (replace with real direct image links):

```text
# My images
https://example.com/images/picture.png
https://example.com/images/another.jpg
```

`App` starts the UI, `ui/MainWindow` contains the two tabs, and `service/`
contains independent sorting and download logic. ComfyUI supplies image recognition; source-site page parsing/authentication
remains an extension point.
