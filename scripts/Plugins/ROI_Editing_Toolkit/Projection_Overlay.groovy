import ij.IJ
import ij.ImagePlus
import ij.CompositeImage
import ij.WindowManager
import ij.gui.GenericDialog
import ij.gui.Overlay
import ij.gui.Roi
import ij.plugin.Duplicator
import ij.plugin.ZProjector
import ij.plugin.frame.RoiManager

final String TITLE = "ROI Editing Toolkit - Projection Overlay"

// Parses "5", "3-10" or "all" into [low, high] clamped to 1..max, or null if invalid.
def parseRange = { String text, int max ->
    String cleaned = text.trim().toLowerCase()

    if (cleaned.isEmpty() || cleaned == "all")
        return [1, max] as int[]

    String[] parts = cleaned.split("-")

    try {
        int low = Integer.parseInt(parts[0].trim())
        int high = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : low

        if (low > high)
            return null

        low = Math.max(1, low)
        high = Math.min(max, high)

        if (low > high)
            return null

        return [low, high] as int[]
    } catch (NumberFormatException error) {
        return null
    }
}

ImagePlus imp = WindowManager.getCurrentImage()

if (imp == null) {
    IJ.error(TITLE, "Open an image stack first.")
    return
}

int[] dims = imp.getDimensions()  // width, height, channels, slices, frames
int nChannels = dims[2]
int nSlices = dims[3]
int nFrames = dims[4]

if (nSlices < 2) {
    IJ.error(TITLE, "The active image has no Z dimension to project.")
    return
}

RoiManager manager = RoiManager.getInstance()

if (manager == null || manager.getCount() == 0) {
    IJ.error(TITLE, "The ROI Manager is empty.")
    return
}

GenericDialog dialog = new GenericDialog(TITLE)
dialog.addChoice("Projection type:", ZProjector.METHODS, ZProjector.METHODS[ZProjector.MAX_METHOD])
dialog.addStringField("Z range (1-" + nSlices + "):", "all", 8)

if (nChannels > 1)
    dialog.addStringField("Channels (1-" + nChannels + "):", "all", 8)

if (nFrames > 1)
    dialog.addStringField("Timepoints (1-" + nFrames + "):", "all", 8)

dialog.showDialog()

if (dialog.wasCanceled())
    return

int method = dialog.getNextChoiceIndex()
String zText = dialog.getNextString()
String cText = nChannels > 1 ? dialog.getNextString() : "all"
String tText = nFrames > 1 ? dialog.getNextString() : "all"

int[] zRange = parseRange(zText, nSlices)
int[] cRange = parseRange(cText, nChannels)
int[] tRange = parseRange(tText, nFrames)

if (zRange == null || cRange == null || tRange == null) {
    IJ.error(TITLE, "Invalid range. Use a number, a range such as 3-10, or 'all'.")
    return
}

if (zRange[1] - zRange[0] < 1) {
    IJ.error(TITLE, "The Z range must cover at least 2 slices.")
    return
}

// ROIs to show: the selected Manager rows, or all of them if none are selected.
Roi[] allRois = manager.getRoisAsArray()
int[] selected = manager.getSelectedIndexes()
List<Roi> sourceRois = []

if (selected != null && selected.length > 0) {
    for (int index : selected)
        sourceRois.add(allRois[index])
} else {
    sourceRois.addAll(allRois)
}

try {
    IJ.showStatus("Projecting...")

    ImagePlus subset = new Duplicator().run(
        imp,
        cRange[0], cRange[1],
        zRange[0], zRange[1],
        tRange[0], tRange[1]
    )

    ZProjector projector = new ZProjector(subset)
    projector.setMethod(method)
    projector.setStartSlice(1)
    projector.setStopSlice(subset.getNSlices())

    if (subset.isHyperStack() || subset.getNDimensions() > 3)
        projector.doHyperStackProjection(true)
    else
        projector.doProjection()

    ImagePlus projection = projector.getProjection()

    if (projection == null) {
        IJ.error(TITLE, "The projection could not be created.")
        return
    }

    boolean allChannelsKept =
        cRange[0] == 1 && cRange[1] == nChannels

    if (imp.isComposite() && projection.isComposite() && allChannelsKept) {
        try {
            ((CompositeImage) projection).copyLuts(imp)
        } catch (Throwable ignored) {
            // Keep the projection's own LUTs.
        }
    }

    int projChannels = projection.getNChannels()
    int projFrames = projection.getNFrames()

    Overlay overlay = new Overlay()
    int added = 0
    int skipped = 0

    for (Roi source : sourceRois) {
        int c = 0
        int z = 0
        int t = 0

        if (source.hasHyperStackPosition()) {
            c = source.getCPosition()
            z = source.getZPosition()
            t = source.getTPosition()
        } else if (source.getPosition() > 0) {
            if (imp.isHyperStack() || imp.getNDimensions() > 3) {
                int[] position = imp.convertIndexToPosition(source.getPosition())
                c = position[0]
                z = position[1]
                t = position[2]
            } else {
                z = source.getPosition()
            }
        }

        // Skip ROIs that belong to slices, channels or frames not projected.
        if ((z > 0 && (z < zRange[0] || z > zRange[1])) ||
            (c > 0 && (c < cRange[0] || c > cRange[1])) ||
            (t > 0 && (t < tRange[0] || t > tRange[1]))) {
            skipped++
            continue
        }

        int projC = (c > 0 && projChannels > 1) ? c - cRange[0] + 1 : 0
        int projT = (t > 0 && projFrames > 1) ? t - tRange[0] + 1 : 0

        Roi copy = (Roi) source.clone()

        if (projC == 0 && projT == 0)
            copy.setPosition(0)
        else
            copy.setPosition(projC, 0, projT)

        overlay.add(copy)
        added++
    }

    projection.setOverlay(overlay)
    projection.show()

    IJ.showStatus(
        "Projection Overlay: " + added + " ROIs shown" +
        (skipped > 0 ? ", " + skipped + " outside the projected range" : "")
    )
} catch (Throwable error) {
    IJ.log(error.toString())
    IJ.error(TITLE, "Could not create the projection:\n" + error.getMessage())
}
