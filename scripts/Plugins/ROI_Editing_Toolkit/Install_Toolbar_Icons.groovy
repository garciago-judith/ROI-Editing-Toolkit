import ij.IJ
import ij.gui.Toolbar
import ij.plugin.MacroInstaller

import javax.swing.JOptionPane
import java.io.File

final String TITLE = "ROI Editing Toolkit"

Toolbar toolbar = Toolbar.getInstance()
if (toolbar == null) {
    IJ.error(TITLE, "The ImageJ toolbar is not available.")
    return
}

String pluginsDir = IJ.getDirectory("plugins")
if (pluginsDir == null) {
    IJ.error(TITLE, "The Fiji plugins directory could not be located.")
    return
}

def installSingleTool = { String toolName, String fileName ->
    if (toolbar.getToolId(toolName) >= 0)
        return "already installed"

    String path = pluginsDir + "Tools" + File.separator + fileName
    File sourceFile = new File(path)

    if (!sourceFile.exists())
        throw new FileNotFoundException("Missing toolbar tool:\n" + path)

    String macroText = IJ.openAsString(path)
    if (macroText == null || macroText.startsWith("Error:"))
        throw new IOException("Could not read:\n" + path)

    new MacroInstaller().installSingleTool(macroText)
    return "installed"
}

try {
    String deleteResult = installSingleTool(
        "ROI Delete Action Tool",
        "ROI_Delete_Action_Tool.ijm"
    )

    String zoomResult = installSingleTool(
        "ROI Zoom Control Action Tool",
        "ROI_Zoom_Control_Action_Tool.ijm"
    )

    IJ.showStatus("ROI Editing Toolkit toolbar icons ready.")

    JOptionPane.showMessageDialog(
        null,
        "Toolbar icons:\n" +
        "- ROI Delete: " + deleteResult + "\n" +
        "- Zoom Control: " + zoomResult + "\n\n" +
        "The icons were appended without replacing existing toolbar tools.",
        TITLE,
        JOptionPane.INFORMATION_MESSAGE
    )
} catch (Throwable error) {
    IJ.log(error.toString())
    IJ.error(
        TITLE,
        "Could not install the toolbar icons:\n" + error.getMessage()
    )
}
