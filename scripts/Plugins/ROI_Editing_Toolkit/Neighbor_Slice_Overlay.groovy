import ij.IJ
import ij.ImagePlus
import ij.WindowManager
import ij.gui.Overlay
import ij.gui.Roi
import ij.plugin.frame.RoiManager

import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants
import javax.swing.border.EmptyBorder
import javax.swing.event.ChangeListener

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

final String WINDOW_TITLE = "ROI Editing Toolkit - Neighbor Slice Overlay"

final Color ABOVE_COLOR = new Color(0, 255, 255)   // lower Z numbers
final Color BELOW_COLOR = new Color(255, 160, 0)   // higher Z numbers

SwingUtilities.invokeLater {

    for (def openFrame : JFrame.getFrames()) {
        if (openFrame instanceof JFrame &&
            openFrame.isDisplayable() &&
            openFrame.getTitle() == WINDOW_TITLE) {

            openFrame.toFront()
            openFrame.requestFocus()
            return
        }
    }

    ImagePlus decoratedImage = null
    // Overlay ROIs created by this tool, tracked by identity so other overlay
    // content on the image is never touched.
    Set<Roi> ghosts = Collections.newSetFromMap(new IdentityHashMap<Roi, Boolean>())
    String lastKey = null

    JFrame frame = new JFrame(WINDOW_TITLE)
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)

    JPanel mainPanel = new JPanel()
    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS))
    mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10))

    JCheckBox enabledBox = new JCheckBox("Show neighbor ROIs", true)
    enabledBox.setAlignmentX(Component.CENTER_ALIGNMENT)

    JPanel rangePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0))
    JSpinner rangeSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 9999, 1))
    rangePanel.add(new JLabel("Slices above/below:"))
    rangePanel.add(rangeSpinner)

    JCheckBox fadeBox = new JCheckBox("Fade with distance", true)
    fadeBox.setAlignmentX(Component.CENTER_ALIGNMENT)

    JLabel legendLabel = new JLabel(
        "<html><font color='#00b0b0'>&#9632;</font> above &nbsp; " +
        "<font color='#e08c00'>&#9632;</font> below</html>",
        SwingConstants.CENTER
    )
    legendLabel.setAlignmentX(Component.CENTER_ALIGNMENT)
    legendLabel.setBorder(new EmptyBorder(4, 0, 4, 0))

    JButton refreshButton = new JButton("Refresh")
    refreshButton.setAlignmentX(Component.CENTER_ALIGNMENT)

    JLabel statusLabel = new JLabel("No active image", SwingConstants.CENTER)
    statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT)
    statusLabel.setBorder(new EmptyBorder(6, 0, 0, 0))

    mainPanel.add(enabledBox)
    mainPanel.add(rangePanel)
    mainPanel.add(fadeBox)
    mainPanel.add(legendLabel)
    mainPanel.add(refreshButton)
    mainPanel.add(statusLabel)

    // Returns [c, z, t] for an ROI (0 = not assigned), as in Projection Overlay.
    def roiPosition = { Roi roi, ImagePlus imp ->
        if (roi.hasHyperStackPosition())
            return [roi.getCPosition(), roi.getZPosition(), roi.getTPosition()] as int[]

        if (roi.getPosition() > 0) {
            if (imp.isHyperStack() || imp.getNDimensions() > 3)
                return imp.convertIndexToPosition(roi.getPosition())

            return [0, roi.getPosition(), 0] as int[]
        }

        return [0, 0, 0] as int[]
    }

    def currentSlice = { ImagePlus imp ->
        (imp.isHyperStack() || imp.getNDimensions() > 3) ?
            imp.getSlice() : imp.getCurrentSlice()
    }

    def removeGhosts = { ImagePlus imp ->
        Overlay overlay = imp?.getOverlay()

        if (overlay == null) {
            ghosts.clear()
            return
        }

        boolean removed = false

        for (Roi roi : overlay.toArray()) {
            if (ghosts.contains(roi)) {
                overlay.remove(roi)
                removed = true
            }
        }

        ghosts.clear()

        if (!removed)
            return

        imp.setOverlay(overlay.size() > 0 ? overlay : null)
    }

    def rebuild = { ImagePlus imp ->
        removeGhosts(imp)

        RoiManager manager = RoiManager.getInstance()

        if (manager == null || manager.getCount() == 0) {
            statusLabel.setText("ROI Manager is empty")
            return
        }

        int range = (rangeSpinner.getValue() as Number).intValue()
        boolean fade = fadeBox.isSelected()
        int currentZ = currentSlice(imp)
        int currentT = imp.getFrame()

        Overlay overlay = imp.getOverlay() ?: new Overlay()
        int above = 0
        int below = 0

        for (Roi source : manager.getRoisAsArray()) {
            int[] position = roiPosition(source, imp)
            int z = position[1]
            int t = position[2]

            // ROIs without a Z position are already visible on every slice.
            if (z == 0 || z == currentZ)
                continue

            if (t > 0 && t != currentT)
                continue

            int distance = Math.abs(z - currentZ)

            if (distance > range)
                continue

            Color base = z < currentZ ? ABOVE_COLOR : BELOW_COLOR
            int alpha = 255

            if (fade && range > 1)
                alpha = (int) Math.round(255 - 165.0 * (distance - 1) / (range - 1))

            float width = (float) Math.max(1.0, source.getStrokeWidth())

            Roi ghost = (Roi) source.clone()
            ghost.setPosition(0)
            ghost.setFillColor(null)
            ghost.setStrokeColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha))
            ghost.setStroke(new BasicStroke(
                width,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                10.0f,
                [4.0f, 3.0f] as float[],
                0.0f
            ))
            ghosts.add(ghost)
            overlay.add(ghost)

            if (z < currentZ)
                above++
            else
                below++
        }

        imp.setOverlay(overlay.size() > 0 ? overlay : null)

        statusLabel.setText(
            "Z " + currentZ + ": " + above + " above, " + below + " below"
        )
    }

    // Polls cheaply and only rebuilds when something relevant changed.
    def sync = { boolean force ->
        ImagePlus imp = WindowManager.getCurrentImage()

        if (imp != decoratedImage) {
            removeGhosts(decoratedImage)
            decoratedImage = null
            lastKey = null
        }

        if (imp == null || imp.getWindow() == null) {
            statusLabel.setText("No active image")
            return
        }

        if (imp.getNSlices() < 2) {
            statusLabel.setText("Image has no Z dimension")
            return
        }

        if (!enabledBox.isSelected()) {
            removeGhosts(imp)
            lastKey = null
            statusLabel.setText("Overlay off")
            return
        }

        RoiManager manager = RoiManager.getInstance()
        int roiCount = manager == null ? 0 : manager.getCount()

        String key = [
            imp.getID(), currentSlice(imp), imp.getFrame(), roiCount,
            rangeSpinner.getValue(), fadeBox.isSelected()
        ].join(":")

        if (!force && key == lastKey)
            return

        try {
            rebuild(imp)
            decoratedImage = imp
            lastKey = key
        } catch (Throwable error) {
            IJ.log(WINDOW_TITLE + ": " + error.toString())
            statusLabel.setText("Error, see Log window")
            lastKey = key
        }
    }

    enabledBox.addActionListener({ event -> sync(true) } as ActionListener)
    fadeBox.addActionListener({ event -> sync(true) } as ActionListener)
    rangeSpinner.addChangeListener({ event -> sync(true) } as ChangeListener)
    refreshButton.addActionListener({ event -> sync(true) } as ActionListener)

    Timer syncTimer = new Timer(250, { event -> sync(false) } as ActionListener)

    frame.addWindowListener(new WindowAdapter() {
        @Override
        void windowClosed(WindowEvent event) {
            syncTimer.stop()
            removeGhosts(decoratedImage)
        }
    })

    frame.setContentPane(mainPanel)
    frame.pack()
    frame.setMinimumSize(new Dimension(260, frame.getHeight()))
    frame.setResizable(true)
    frame.setLocationByPlatform(true)
    frame.setAlwaysOnTop(true)
    frame.setVisible(true)

    sync(true)
    syncTimer.start()
}
