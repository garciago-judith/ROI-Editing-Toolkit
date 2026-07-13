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

### Toolbar integration

The toolkit includes two distinct toolbar icons:

- Red freehand region with an X: **Delete ROIs in Region**
- Blue magnifying glass with a plus: **Zoom Control**

The installer adds each icon as an individual toolbar tool. It does not load a replacement toolset, so existing custom toolbar tools are preserved.

## Installation

1. Download or clone this repository.
2. Copy the repository contents into the root of your `Fiji.app` installation, preserving the folder structure.
3. Restart Fiji.
4. Run:

   `Plugins > ROI Editing Toolkit > Install Toolbar Icons`

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
  Install_Toolbar_Icons.groovy

plugins/Tools/
  ROI_Delete_Action_Tool.ijm
  ROI_Zoom_Control_Action_Tool.ijm

macros/toolsets/icons/
  ROI_Editing_Toolkit_Delete.png
  ROI_Editing_Toolkit_Zoom.png
```

## Requirements

- Fiji/ImageJ with Groovy scripting support
- ROI Manager ROIs should have slice positions assigned when using slice-specific deletion

## Upgrading from the earlier toolset version

Older `macros/toolsets` files could replace existing custom tools when selected from the `>>` toolbar menu. They are not used by this version.

Remove these older files if present:

```text
Fiji.app/macros/toolsets/ROI Tools.ijm
Fiji.app/macros/toolsets/Delete ROIs in Region.ijm
Fiji.app/macros/toolsets/Vertical Zoom Slider.ijm
```

Then use:

`Plugins > ROI Editing Toolkit > Install Toolbar Icons`

## Status

Initial public version. The scripts were developed and tested iteratively against current Fiji/ImageJ behavior; please report version-specific errors through GitHub Issues.
