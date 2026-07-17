# ROI Editing Toolkit

A small collection of Fiji/ImageJ utilities for manual ROI review and editing.

## Included tools

### ROI Region Actions

Draw a freehand area selection and identify ROI Manager entries that fall within it on the current image slice. The required percentage of ROI area inside the selection is configurable.

The same detected ROIs can then be processed in either of two ways:

- **Delete ROIs on current slice**
- **Set group for ROIs in region**

The group action:

- assigns the selected ROIs to the group entered in the dialog;
- sets their ROI position to `0`;
- sets their outline color to magenta;
- sets their line width to `0`;
- leaves the affected ROI Manager rows selected.

The tool deliberately skips ROIs without an assigned slice position when finding candidates, so ROIs from unrelated planes are not processed accidentally.

> **Position note:** after the group action sets an ROI position to `0`, that ROI is no longer associated with a particular slice. It will therefore be skipped by later slice-specific region searches.

### Zoom Control

A persistent, resizable vertical zoom slider for the currently active ImageJ image window.

- Uses ImageJ's native discrete zoom levels
- Tracks the active image
- Includes **Fit** and **100%** buttons
- Default height is approximately 720 px
- Does not modify the active ROI or ROI Manager

## Toolbar integration

Both toolbar buttons are provided in one combined ImageJ toolset:

```text
macros/toolsets/ROI Editing Toolkit.ijm
```

Load the toolset once from the `>>` menu and both buttons appear together.

The icons are encoded directly in the macro-tool names. No PNG files are loaded, and no separate plugin tools or runtime toolbar installer are used.

Selecting a toolset replaces the currently active custom toolset. This is standard ImageJ behavior. The normal built-in ImageJ toolbar tools remain available.

## Installation

1. Download or clone this repository.
2. Copy the repository contents into the root of your `Fiji.app` installation, preserving the folder structure.
3. Remove the obsolete files listed under **Cleaning an older installation**.
4. Restart Fiji.
5. Click `>>` at the right side of the ImageJ toolbar.
6. Select **ROI Editing Toolkit**.

Both buttons will be loaded together:

- **ROI Region Actions**
- **Zoom Control**

The commands are also available directly from:

- `Plugins > ROI Editing Toolkit > Delete ROIs in Region`
- `Plugins > ROI Editing Toolkit > Zoom Control`

## Usage

### Delete ROIs in a region

1. Open an image stack and the ROI Manager.
2. Navigate to the Z-slice to edit.
3. Draw a freehand **area** selection around the ROIs to remove.
4. Open **ROI Region Actions**.
5. Set the minimum percentage of each ROI that must lie inside the selection.
6. Click **Delete ROIs on current slice**.

The drawn region remains selected so it can be adjusted and reused.

### Assign a group to ROIs in a region

1. Navigate to the source Z-slice.
2. Draw a freehand **area** selection around the cells to classify.
3. Open **ROI Region Actions**.
4. Set the overlap threshold.
5. Enter the target group number (`0-255`).
6. Click **Set group for ROIs in region**.

The matching ROI Manager rows remain selected. Their group and visual properties are updated as follows:

```text
Group: selected value
Position: 0
Outline color: magenta
Line width: 0
```

Group `0` removes formal group membership while still applying the requested position and display properties.

### Zoom Control

1. Open **Zoom Control** from the toolbar or Plugins menu.
2. Activate the image window you want to control.
3. Move the vertical slider, or use **Fit** or **100%**.

The slider follows whichever image window is currently active.

## Repository structure

```text
scripts/Plugins/ROI_Editing_Toolkit/
  Delete_ROIs_in_Region.groovy
  Zoom_Control.groovy

macros/toolsets/
  ROI Editing Toolkit.ijm
```

## Requirements

- Fiji/ImageJ with Groovy scripting support
- ROI Manager ROIs should have slice positions assigned before running a slice-specific region action

## Cleaning an older installation

Remove these obsolete files if present:

```text
Fiji.app/macros/toolsets/ROI Tools.ijm
Fiji.app/macros/toolsets/Delete ROIs in Region.ijm
Fiji.app/macros/toolsets/Vertical Zoom Slider.ijm
Fiji.app/plugins/Tools/ROI_Delete_Action_Tool.ijm
Fiji.app/plugins/Tools/ROI_Zoom_Control_Action_Tool.ijm
Fiji.app/scripts/Plugins/ROI_Editing_Toolkit/Install_Toolbar_Icons.groovy
Fiji.app/macros/toolsets/icons/ROI_Editing_Toolkit_Delete.png
Fiji.app/macros/toolsets/icons/ROI_Editing_Toolkit_Zoom.png
```

Then copy the current repository files into `Fiji.app`, restart Fiji, and select **ROI Editing Toolkit** from the toolbar `>>` menu.

## Status

Initial public version. Please report Fiji-version-specific errors through GitHub Issues.
