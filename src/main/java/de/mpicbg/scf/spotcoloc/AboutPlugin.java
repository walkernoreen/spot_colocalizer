// TODO update to the new plugin info (spotcoloc instead of segtools)
package de.mpicbg.scf.spotcoloc;

import ij.gui.GenericDialog;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;


/*
 * Author: Noreen Walker, Scientific Computing Facility, MPI-CBG
 * Date: 2019-10
 */

/**
 * Provide info on the authors and online documentation help
 */
@Plugin(type = Command.class, menuPath = "Plugins>SegTools>About + Help")
public class AboutPlugin implements Command {

    final String helpURL="https://github.com/mpicbg-scicomp/segmentation_3d_tools";

    @Override
    public void run() {

        String infoText1="SegTools is developed at the Scientific Computing Facility\n"+
                "at the MPI-CBG.";

        String infoText2="Project page with Help and further Information (or press the Help button):\n"+
                helpURL;

        GenericDialog gd = new GenericDialog("About SegTools");

        gd.addMessage(infoText1);
        gd.addMessage(infoText2);
        gd.addHelp(helpURL);
        gd.showDialog();

        if (gd.wasCanceled()) {
            return;
        }

    }


}
