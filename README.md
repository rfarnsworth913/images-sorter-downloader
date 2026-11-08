# Image Sorter & Downloader

A small Java Swing desktop application based on [the app spec](docs/app-spec.txt).
Requires JDK 17 or newer; no third-party dependencies or build-tool installation.

The ImageFlow desktop UI uses a dark theme with teal accents, separate Sorter, Downloader, and
Configuration workspaces, clickable pattern tags, a live sample folder tree, and a
timestamped activity table with progress and completion counts. Settings scroll
on smaller windows. The preview uses illustrative tags and does not scan or move
files. Sorting routes images to multiple output destinations, moves files, and prompts
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
  skipping preserves it. Only immediate source files are scanned by default. Enable **Scan sub-folders**
  to include nested folders; all configured output directories are excluded from the
  source scan and symbolic links are not followed. Supported
  extensions: JPG/JPEG, PNG, GIF, BMP, WebP, TIFF/TIF. Java's built-in preview
  support varies by format; unsupported previews show the filename instead.
  The activity monitor and progress bar update as images are processed.

  **ComfyUI setup:** start your local ComfyUI server (default URL
  `http://127.0.0.1:8188`) and install a vision model and its required nodes.
  Build a workflow that reads an image, identifies the properties above, and
  exposes a text output through an output node. Export the workflow in **API
  format**, replace the image input filename with `{{image}}`, then select this
  JSON file and enter the text output node ID in the Configuration tab. The node must expose
  a `text` string or list of strings in execution history; the first string must
  be a JSON object, without markdown fences, for example:
  `{"series":"Example","character":["Hero","Friend"],"content":"portrait","artist":"Unknown","album":"Example","rating":"sfw","tags":["portrait","fantasy"]}`.
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

`App` starts the UI, `ui/MainWindow` contains the three tabs, and `service/`
contains independent sorting and download logic. ComfyUI supplies image recognition; source-site page parsing/authentication
remains an extension point.

The editable tag library is [data/tags.csv](data/tags.csv). It contains filename
placeholder rows (`pattern`), the starting content categories `sfw` and `nsfw`
(`category`), and ten sample content tags for each (`content`, with its parent
category in column two). These are a starter vocabulary, not an exhaustive or
automatic content rating system. View the content vocabulary in Configuration.
The image recognition workflow continues to determine image properties.

Edit this UTF-8 CSV and restart the app to reload it. The schema is
`type,parent,tag`; tags use letters, digits, or underscores (numeric filename
placeholders can use `#`). Leave parent blank for patterns and categories.
The app reads `data/tags.csv` relative to its working directory, falling back to
the bundled copy if no external file exists. Builds include the CSV in the JAR.

Output destinations always include a **Default folder**, initially
`~/Pictures/ImageFlow`. You can change this path but cannot remove the default
card. Use **+ Add folder** for additional destinations and each card's **Remove**
button to delete it. Every additional folder must have at least one tag selected
from the dropdown, populated from all known CSV tags. Click a selected tag to
remove it. Edits apply to the next sorting run.

Routing checks additional destinations in display order. All selected tags must
match, ignoring case; the first matching folder supplies the base output path.
If none matches, the default folder is used. The shared folder/filename pattern
is applied below that base path. The preview shows sample paths for every folder.
Duplicate checks are limited to the selected destination's subtree, so choosing
Replace cannot redirect an image to an unrelated destination.

Your ComfyUI workflow can return `rating` (for example `sfw` or `nsfw`) and `tags`
(as a string array or comma-separated string). Routing also matches metadata
values such as `content`; metadata names such as `artist` match when that
property is known. Content categories are explicit: selecting `portrait` does
not automatically classify an image as SFW. Manual review includes a routing
tag dropdown. Filename placeholders remain formatting controls; selecting one
as a routing label requires a matching image tag unless it names known metadata.
