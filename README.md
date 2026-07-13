# ROI Editing Toolkit

A small collection of Fiji/ImageJ utilities for manual ROI review and editing.

## Included tools

### Delete ROIs in Region

Draw a freehand area selection and delete ROI Manager entries that fall within it on the current image slice. The required percentage of ROI area inside the selection is configurable.

The tool deliberately skips ROIs without an assigned slice position so that ROIs from other planes are not deleted accidentally.

### Zoom Control

A persistent, resizable vertical zoom slider for the currently active ImageJ image window.

- Uses ImageJ's native discrete zoom levels
- Tracks the active image
- Includes **Fit** and **100%** buttons
- Default height is approximately 720 px
- Does not modify the active ROI or ROI Manager

## Toolbar integration

The toolbar entries now use plain ImageJ macro action tools with default letter icons. No PNG icons are loaded.

The tools are installed through ImageJ's native `Plugins > Tools` mechanism. This appends them as plugin tools and does not replace the existing toolset.

## Installation

1. Download or clone this repository.
2. Copy the repository contents into the root of your `Fiji.app` installation, preserving the folder structure.
3. Remove any older PNG icon files and toolsets listed below.
4. Restart Fiji.
5. Open the `>>` menu at the right side of the ImageJ toolbar.
6. Select each tool once:
   - `ROI Delete Action Tool`
   - `ROI Zoom Control Action Tool`

ImageJ loads these from `plugins/Tools` and appends them to the toolbar without clearing the tools already present.

The commands are also available directly from:

- `Plugins > ROI Editing Toolkit > Delete ROIs in Region`
- `Plugins > ROI Editing Toolkit > Zoom Control`

## Usage

### Delete ROIs in Region

1. Open an image stack and the ROI Manager.
2. Navigate to the Z-slice to edit.
3. Draw a freehand **area** selection around the ROIs to remove.
4. Open **Delete ROIs in Region**.
5. Set the minimum percentage of each ROI that must lie inside the selection.
6. Click **Delete ROIs on current slice**.

The drawn deletion region remains selected so it can be adjusted and reused.

### Zoom Control

1. Open **Zoom Control** from the Plugins menu or toolbar.
2. Activate the image window you want to control.
3. Move the vertical slider, or use **Fit** or **100%**.

The slider follows whichever image window is currently active.

## Repository structure

```text
scripts/Plugins/ROI_Editing_Toolkit/
  Delete_ROIs_in_Region.groovy
  Zoom_Control.groovy

plugins/Tools/
  ROI_Delete_Action_Tool.ijm
  ROI_Zoom_Control_Action_Tool.ijm
```

## Requirements

- Fiji/ImageJ with Groovy scripting support
- ROI Manager ROIs should have slice positions assigned when using slice-specific deletion

## Cleaning an older installation

Remove these older files if present:

```text
Fiji.app/macros/toolsets/ROI Tools.ijm
Fiji.app/macros/toolsets/Delete ROIs in Region.ijm
Fiji.app/macros/toolsets/Vertical Zoom Slider.ijm
Fiji.app/scripts/Plugins/ROI_Editing_Toolkit/Install_Toolbar_Icons.groovy
Fiji.app/macros/toolsets/icons/ROI_Editing_Toolkit_Delete.png
Fiji.app/macros/toolsets/icons/ROI_Editing_Toolkit_Zoom.png
```

Then replace the two `.ijm` files under `Fiji.app/plugins/Tools` with the current repository versions and restart Fiji.

## Status

Initial public version. Please report Fiji-version-specific errors through GitHub Issues.
