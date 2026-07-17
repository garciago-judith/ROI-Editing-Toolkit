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

final String WINDOW_TITLE = "ROI Editing Toolkit - Region Actions"

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
    thresholdPanel.add(new JLabel("ROI area inside selection:"))

    JSpinner thresholdSpinner =
        new JSpinner(new SpinnerNumberModel(95, 1, 100, 1))
    thresholdSpinner.setPreferredSize(new Dimension(65, 26))
    thresholdPanel.add(thresholdSpinner)
    thresholdPanel.add(new JLabel("%"))

    JPanel groupPanel =
        new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0))
    groupPanel.add(new JLabel("Target ROI group:"))

    JSpinner groupSpinner =
        new JSpinner(new SpinnerNumberModel(1, 1, 255, 1))
    groupSpinner.setPreferredSize(new Dimension(65, 26))
    groupPanel.add(groupSpinner)

    JButton deleteButton =
        new JButton("Delete ROIs on current slice")
    deleteButton.setAlignmentX(Component.CENTER_ALIGNMENT)

    JButton groupButton =
        new JButton("Set group for ROIs in region")
    groupButton.setAlignmentX(Component.CENTER_ALIGNMENT)

    JLabel statusLabel = new JLabel(
        "<html>Draw a freehand area selection.<br>" +
        "Both actions use the current slice and threshold.</html>"
    )
    statusLabel.setBorder(new EmptyBorder(8, 2, 0, 2))
    statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT)

    panel.add(thresholdPanel)
    panel.add(groupPanel)
    panel.add(Box.createVerticalStrut(8))
    panel.add(deleteButton)
    panel.add(Box.createVerticalStrut(6))
    panel.add(groupButton)
    panel.add(statusLabel)

    def getContext = {
        ImagePlus imp = WindowManager.getCurrentImage()
        if (imp == null) {
            JOptionPane.showMessageDialog(
                frame,
                "No image is currently open.",
                WINDOW_TITLE,
                JOptionPane.WARNING_MESSAGE
            )
            return null
        }

        Roi selectionRegion = imp.getRoi()
        if (selectionRegion == null || !selectionRegion.isArea()) {
            JOptionPane.showMessageDialog(
                frame,
                "Draw a freehand area selection on the image first.",
                WINDOW_TITLE,
                JOptionPane.WARNING_MESSAGE
            )
            return null
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
            return null
        }

        return [
            imp: imp,
            roiManager: roiManager,
            region: selectionRegion.clone() as Roi,
            channel: imp.getC(),
            z: imp.getZ(),
            frame: imp.getT(),
            slice: imp.getCurrentSlice(),
            fraction:
                ((Number) thresholdSpinner.getValue()).doubleValue() / 100.0
        ]
    }

    def findMatches = { context ->
        ImagePlus imp = context.imp
        RoiManager roiManager = context.roiManager
        Roi region = context.region
        Roi[] rois = roiManager.getRoisAsArray()

        ArrayList<Integer> indexes = new ArrayList<Integer>()
        int unpositionedCount = 0

        for (int i = 0; i < rois.length; i++) {
            Roi candidate = rois[i]
            if (candidate == null || !candidate.isArea())
                continue

            boolean onCurrentPlane = false

            if (imp.isHyperStack()) {
                int roiC = candidate.getCPosition()
                int roiZ = candidate.getZPosition()
                int roiT = candidate.getTPosition()

                if (roiZ == 0) {
                    unpositionedCount++
                    continue
                }

                onCurrentPlane =
                    roiZ == context.z &&
                    (roiC == 0 || roiC == context.channel) &&
                    (roiT == 0 || roiT == context.frame)
            } else {
                int roiSlice = candidate.getPosition()

                if (roiSlice == 0) {
                    unpositionedCount++
                    continue
                }

                onCurrentPlane = roiSlice == context.slice
            }

            if (!onCurrentPlane)
                continue

            def points = candidate.getContainedPoints()
            if (points == null || points.length == 0)
                continue

            int pointsInside = 0
            for (def point : points) {
                int x = ((Number) point.x).intValue()
                int y = ((Number) point.y).intValue()
                if (region.contains(x, y))
                    pointsInside++
            }

            double fractionInside =
                pointsInside / (double) points.length

            if (fractionInside >= context.fraction)
                indexes.add(i)
        }

        return [
            indexes: indexes,
            unpositionedCount: unpositionedCount
        ]
    }

    def restoreImage = { context ->
        ImagePlus imp = context.imp

        if (imp.isHyperStack())
            imp.setPosition(context.channel, context.z, context.frame)
        else
            imp.setSlice(context.slice)

        imp.setRoi(context.region)
        imp.updateAndDraw()
    }

    deleteButton.addActionListener {
        try {
            def context = getContext()
            if (context == null)
                return

            def result = findMatches(context)
            ArrayList<Integer> indexes = result.indexes

            Collections.sort(indexes, Collections.reverseOrder())

            for (Integer index : indexes)
                context.roiManager.delete(index)

            restoreImage(context)

            int displayedSlice =
                context.imp.isHyperStack() ? context.z : context.slice

            statusLabel.setText(
                "<html>Deleted <b>" + indexes.size() +
                "</b> ROI(s) from slice " + displayedSlice +
                ".<br>Skipped unpositioned ROIs: " +
                result.unpositionedCount + "</html>"
            )

            IJ.showStatus(
                "Deleted " + indexes.size() +
                " ROI(s) from the current slice."
            )
        } catch (Throwable error) {
            IJ.log(error.toString())
            JOptionPane.showMessageDialog(
                frame,
                "The delete operation failed:\n" +
                error.getMessage() +
                "\n\nSee the Fiji Log window for details.",
                WINDOW_TITLE,
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    groupButton.addActionListener {
        try {
            def context = getContext()
            if (context == null)
                return

            def result = findMatches(context)
            ArrayList<Integer> indexes = result.indexes

            if (indexes.isEmpty()) {
                statusLabel.setText(
                    "<html>No ROIs matched the current slice, " +
                    "region, and threshold.</html>"
                )
                IJ.showStatus("No ROIs matched the region.")
                return
            }

            int[] selectedIndexes = new int[indexes.size()]
            for (int i = 0; i < indexes.size(); i++)
                selectedIndexes[i] = indexes.get(i)

            int targetGroup =
                ((Number) groupSpinner.getValue()).intValue()

            RoiManager roiManager = context.roiManager
            roiManager.setSelectedIndexes(selectedIndexes)

            // Assign only the requested group. ImageJ applies
            // the corresponding group color automatically.
            roiManager.setGroup(targetGroup)

            // Keep the affected cells selected in ROI Manager.
            roiManager.setSelectedIndexes(selectedIndexes)
            restoreImage(context)

            statusLabel.setText(
                "<html>Assigned <b>" + selectedIndexes.length +
                "</b> ROI(s) to group " + targetGroup +
                ". ImageJ applied the group color automatically.</html>"
            )

            IJ.showStatus(
                "Assigned " + selectedIndexes.length +
                " ROI(s) to group " + targetGroup + "."
            )
        } catch (Throwable error) {
            IJ.log(error.toString())
            JOptionPane.showMessageDialog(
                frame,
                "The group operation failed:\n" +
                error.getMessage() +
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
