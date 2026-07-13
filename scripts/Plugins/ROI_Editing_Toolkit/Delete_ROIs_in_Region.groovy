import ij.IJ
import ij.ImagePlus
import ij.WindowManager
import ij.gui.Roi
import ij.plugin.frame.RoiManager

import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import javax.swing.border.EmptyBorder

import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.ArrayList
import java.util.Collections

final String WINDOW_TITLE = "ROI Editing Toolkit - Delete ROIs"

SwingUtilities.invokeLater {

    JFrame existingWindow = null

    for (def openFrame : JFrame.getFrames()) {
        if (openFrame instanceof JFrame &&
            openFrame.isDisplayable() &&
            openFrame.getTitle() == WINDOW_TITLE) {

            existingWindow = openFrame as JFrame
            break
        }
    }

    if (existingWindow != null) {
        existingWindow.toFront()
        existingWindow.requestFocus()
        return
    }

    JFrame frame = new JFrame(WINDOW_TITLE)
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)

    JPanel panel = new JPanel()
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS))
    panel.setBorder(new EmptyBorder(10, 10, 10, 10))

    JPanel thresholdPanel =
        new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0))

    JLabel thresholdLabel =
        new JLabel("ROI area inside selection:")

    JSpinner thresholdSpinner = new JSpinner(
        new SpinnerNumberModel(95, 1, 100, 1)
    )

    thresholdSpinner.setPreferredSize(new Dimension(65, 26))

    thresholdPanel.add(thresholdLabel)
    thresholdPanel.add(thresholdSpinner)
    thresholdPanel.add(new JLabel("%"))

    JButton deleteButton =
        new JButton("Delete ROIs on current slice")

    deleteButton.setAlignmentX(Component.CENTER_ALIGNMENT)

    JLabel statusLabel = new JLabel(
        "<html>Draw a freehand area selection,<br>" +
        "then press the button.</html>"
    )

    statusLabel.setBorder(new EmptyBorder(8, 2, 0, 2))
    statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT)

    panel.add(thresholdPanel)
    panel.add(Box.createVerticalStrut(8))
    panel.add(deleteButton)
    panel.add(statusLabel)

    deleteButton.addActionListener {
        try {
            ImagePlus imp = WindowManager.getCurrentImage()

            if (imp == null) {
                JOptionPane.showMessageDialog(
                    frame,
                    "No image is currently open.",
                    WINDOW_TITLE,
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }

            Roi deletionRegion = imp.getRoi()

            if (deletionRegion == null || !deletionRegion.isArea()) {
                JOptionPane.showMessageDialog(
                    frame,
                    "Draw a freehand area selection on the image first.",
                    WINDOW_TITLE,
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }

            RoiManager roiManager = RoiManager.getInstance2()
            if (roiManager == null)
                roiManager = RoiManager.getInstance()

            if (roiManager == null || roiManager.getCount() == 0) {
                JOptionPane.showMessageDialog(
                    frame,
                    "The ROI Manager is empty.",
                    WINDOW_TITLE,
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }

            Roi savedDeletionRegion = deletionRegion.clone() as Roi

            int originalChannel = imp.getC()
            int originalZ = imp.getZ()
            int originalFrame = imp.getT()
            int originalStackPosition = imp.getCurrentSlice()

            double requiredFraction =
                ((Number) thresholdSpinner.getValue()).doubleValue() / 100.0

            Roi[] rois = roiManager.getRoisAsArray()

            ArrayList<Integer> indexesToDelete =
                new ArrayList<Integer>()

            int unpositionedCount = 0
            int nonAreaCount = 0

            for (int i = 0; i < rois.length; i++) {

                Roi candidate = rois[i]

                if (candidate == null || !candidate.isArea()) {
                    nonAreaCount++
                    continue
                }

                boolean isOnCurrentPlane = false

                if (imp.isHyperStack()) {

                    int roiChannel = candidate.getCPosition()
                    int roiZ = candidate.getZPosition()
                    int roiFrame = candidate.getTPosition()

                    if (roiZ == 0) {
                        unpositionedCount++
                        continue
                    }

                    isOnCurrentPlane =
                        roiZ == originalZ &&
                        (roiChannel == 0 ||
                         roiChannel == originalChannel) &&
                        (roiFrame == 0 ||
                         roiFrame == originalFrame)

                } else {

                    int roiSlice = candidate.getPosition()

                    if (roiSlice == 0) {
                        unpositionedCount++
                        continue
                    }

                    isOnCurrentPlane =
                        roiSlice == originalStackPosition
                }

                if (!isOnCurrentPlane)
                    continue

                def containedPoints = candidate.getContainedPoints()

                if (containedPoints == null ||
                    containedPoints.length == 0) {
                    continue
                }

                int pointsInside = 0

                for (def point : containedPoints) {
                    int px = ((Number) point.x).intValue()
                    int py = ((Number) point.y).intValue()

                    if (savedDeletionRegion.contains(px, py))
                        pointsInside++
                }

                double fractionInside =
                    pointsInside / (double) containedPoints.length

                if (fractionInside >= requiredFraction)
                    indexesToDelete.add(i)
            }

            Collections.sort(
                indexesToDelete,
                Collections.reverseOrder()
            )

            for (Integer index : indexesToDelete) {
                roiManager.select(index)
                roiManager.runCommand("Delete")
            }

            if (imp.isHyperStack()) {
                imp.setPosition(
                    originalChannel,
                    originalZ,
                    originalFrame
                )
            } else {
                imp.setSlice(originalStackPosition)
            }

            imp.setRoi(savedDeletionRegion)
            imp.updateAndDraw()

            int deletedCount = indexesToDelete.size()
            int displayedSlice =
                imp.isHyperStack() ? originalZ : originalStackPosition

            statusLabel.setText(
                "<html>Deleted <b>${deletedCount}</b> ROI(s) " +
                "from slice ${displayedSlice}.<br>" +
                "Skipped unpositioned ROIs: " +
                "${unpositionedCount}</html>"
            )

            IJ.showStatus(
                "Deleted ${deletedCount} ROI(s) from the current slice."
            )

        } catch (Throwable error) {
            IJ.log(error.toString())

            JOptionPane.showMessageDialog(
                frame,
                "The operation failed:\n" + error.getMessage() +
                "\n\nSee the Fiji Log window for details.",
                WINDOW_TITLE,
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    frame.setContentPane(panel)
    frame.pack()
    frame.setResizable(false)
    frame.setLocationByPlatform(true)
    frame.setAlwaysOnTop(true)
    frame.setVisible(true)
}
