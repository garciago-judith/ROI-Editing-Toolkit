import ij.IJ
import ij.ImagePlus
import ij.WindowManager
import ij.gui.ImageCanvas

import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants
import javax.swing.border.EmptyBorder
import javax.swing.event.ChangeListener

import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Rectangle
import java.awt.event.ActionListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.Hashtable
import java.util.Locale

final String WINDOW_TITLE = "ROI Editing Toolkit - Zoom Control"

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

    // Native ImageJ zoom levels.
    double[] zoomLevels = [
        1.0 / 72.0,
        1.0 / 48.0,
        1.0 / 32.0,
        1.0 / 24.0,
        1.0 / 16.0,
        1.0 / 12.0,
        1.0 / 8.0,
        1.0 / 6.0,
        1.0 / 4.0,
        1.0 / 3.0,
        1.0 / 2.0,
        0.75,
        1.0,
        1.5,
        2.0,
        3.0,
        4.0,
        6.0,
        8.0,
        12.0,
        16.0,
        24.0,
        32.0
    ] as double[]

    int index100 = 12

    boolean internalSliderUpdate = false
    boolean applyingZoom = false

    def formatPercent = { double magnification ->
        double percent = magnification * 100.0

        if (percent < 10.0)
            return String.format(Locale.US, "%.1f%%", percent)

        if (Math.abs(percent - Math.rint(percent)) < 0.05)
            return String.format(Locale.US, "%.0f%%", percent)

        return String.format(Locale.US, "%.1f%%", percent)
    }

    def nearestZoomIndex = { double magnification ->
        if (magnification <= 0.0)
            return index100

        int nearest = 0
        double smallestDistance = Double.POSITIVE_INFINITY

        for (int i = 0; i < zoomLevels.length; i++) {
            double distance =
                Math.abs(Math.log(magnification / zoomLevels[i]))

            if (distance < smallestDistance) {
                smallestDistance = distance
                nearest = i
            }
        }

        return nearest
    }

    JFrame frame = new JFrame(WINDOW_TITLE)
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)

    JPanel mainPanel = new JPanel()
    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS))
    mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10))

    JLabel zoomLabel = new JLabel("No active image", SwingConstants.CENTER)
    zoomLabel.setAlignmentX(Component.CENTER_ALIGNMENT)
    zoomLabel.setBorder(new EmptyBorder(0, 0, 6, 0))

    JSlider slider = new JSlider(
        SwingConstants.VERTICAL,
        0,
        zoomLevels.length - 1,
        index100
    )

    slider.setPreferredSize(new Dimension(125, 560))
    slider.setSnapToTicks(true)
    slider.setMinorTickSpacing(1)
    slider.setPaintTicks(true)
    slider.setPaintLabels(true)

    Hashtable<Integer, JLabel> labelTable =
        new Hashtable<Integer, JLabel>()

    int[] labelledIndexes = [
        0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22
    ] as int[]

    for (int index : labelledIndexes) {
        labelTable.put(
            index,
            new JLabel(formatPercent(zoomLevels[index]))
        )
    }

    slider.setLabelTable(labelTable)

    JPanel buttonPanel =
        new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0))

    JButton fitButton = new JButton("Fit")
    JButton oneHundredButton = new JButton("100%")

    buttonPanel.add(fitButton)
    buttonPanel.add(oneHundredButton)

    JLabel imageLabel = new JLabel(
        "The slider follows the active image.",
        SwingConstants.CENTER
    )
    imageLabel.setAlignmentX(Component.CENTER_ALIGNMENT)
    imageLabel.setBorder(new EmptyBorder(6, 0, 0, 0))

    mainPanel.add(zoomLabel)
    mainPanel.add(slider)
    mainPanel.add(Box.createVerticalStrut(6))
    mainPanel.add(buttonPanel)
    mainPanel.add(imageLabel)

    def setSliderSilently = { int value ->
        internalSliderUpdate = true

        try {
            slider.setValue(value)
        } finally {
            internalSliderUpdate = false
        }
    }

    def updateDisplayFromActiveImage = {
        ImagePlus imp = WindowManager.getCurrentImage()

        if (imp == null || imp.getCanvas() == null) {
            slider.setEnabled(false)
            fitButton.setEnabled(false)
            oneHundredButton.setEnabled(false)
            zoomLabel.setText("No active image")
            imageLabel.setText("Open or activate an image window.")
            return
        }

        ImageCanvas canvas = imp.getCanvas()
        double magnification = canvas.getMagnification()

        slider.setEnabled(true)
        fitButton.setEnabled(true)
        oneHundredButton.setEnabled(true)

        zoomLabel.setText(formatPercent(magnification))
        imageLabel.setText(imp.getTitle())

        if (!slider.getValueIsAdjusting() && !applyingZoom) {
            setSliderSilently(nearestZoomIndex(magnification))
        }
    }

    def applyZoomLevel = { int targetIndex ->
        ImagePlus imp = WindowManager.getCurrentImage()

        if (imp == null || imp.getCanvas() == null) {
            IJ.showStatus("No active image window.")
            updateDisplayFromActiveImage()
            return
        }

        ImageCanvas canvas = imp.getCanvas()
        double targetMagnification = zoomLevels[targetIndex]

        applyingZoom = true

        try {
            int guard = 0

            while (canvas.getMagnification() <
                   targetMagnification - 1.0e-12 &&
                   guard < 40) {

                double before = canvas.getMagnification()

                int centerX = canvas.getWidth().intdiv(2)
                int centerY = canvas.getHeight().intdiv(2)

                canvas.zoomIn(centerX, centerY)

                double after = canvas.getMagnification()

                if (after <= before + 1.0e-12)
                    break

                guard++
            }

            guard = 0

            while (canvas.getMagnification() >
                   targetMagnification + 1.0e-12 &&
                   guard < 40) {

                double before = canvas.getMagnification()

                int centerX = canvas.getWidth().intdiv(2)
                int centerY = canvas.getHeight().intdiv(2)

                canvas.zoomOut(centerX, centerY)

                double after = canvas.getMagnification()

                if (after >= before - 1.0e-12)
                    break

                guard++
            }

            canvas.repaint()
            imp.updateAndDraw()

            double actualMagnification = canvas.getMagnification()

            zoomLabel.setText(formatPercent(actualMagnification))
            imageLabel.setText(imp.getTitle())

            IJ.showStatus(
                "Zoom: " + formatPercent(actualMagnification)
            )

        } catch (Throwable error) {
            IJ.log(error.toString())
            IJ.error(
                WINDOW_TITLE,
                "Could not change the zoom:\n" +
                error.getMessage()
            )
        } finally {
            applyingZoom = false
        }
    }

    slider.addChangeListener({ event ->
        if (internalSliderUpdate)
            return

        applyZoomLevel(slider.getValue())
    } as ChangeListener)

    oneHundredButton.addActionListener({ event ->
        setSliderSilently(index100)
        applyZoomLevel(index100)
    } as ActionListener)

    fitButton.addActionListener({ event ->
        ImagePlus imp = WindowManager.getCurrentImage()

        if (imp == null || imp.getCanvas() == null) {
            IJ.showStatus("No active image window.")
            updateDisplayFromActiveImage()
            return
        }

        try {
            applyingZoom = true

            ImageCanvas canvas = imp.getCanvas()

            canvas.setSourceRect(
                new Rectangle(
                    0,
                    0,
                    imp.getWidth(),
                    imp.getHeight()
                )
            )

            canvas.fitToWindow()
            canvas.repaint()
            imp.updateAndDraw()

            double actualMagnification = canvas.getMagnification()

            setSliderSilently(nearestZoomIndex(actualMagnification))
            zoomLabel.setText(formatPercent(actualMagnification))
            imageLabel.setText(imp.getTitle())

            IJ.showStatus(
                "Zoom fitted to window: " +
                formatPercent(actualMagnification)
            )

        } catch (Throwable error) {
            IJ.log(error.toString())
            IJ.error(
                WINDOW_TITLE,
                "Could not fit the image to the window:\n" +
                error.getMessage()
            )
        } finally {
            applyingZoom = false
        }
    } as ActionListener)

    Timer syncTimer = new Timer(
        300,
        { event ->
            updateDisplayFromActiveImage()
        } as ActionListener
    )

    frame.addWindowListener(new WindowAdapter() {
        @Override
        void windowClosed(WindowEvent event) {
            syncTimer.stop()
        }
    })

    frame.setContentPane(mainPanel)
    frame.pack()
    frame.setMinimumSize(new Dimension(175, 680))
    frame.setSize(new Dimension(185, 720))
    frame.setResizable(true)
    frame.setLocationByPlatform(true)
    frame.setAlwaysOnTop(true)
    frame.setVisible(true)

    updateDisplayFromActiveImage()
    syncTimer.start()
}
