package de.mpicbg.scf.spotcoloc;

import ij.ImagePlus;
import ij.plugin.frame.RoiManager;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/*
 * Author: Noreen Walker, Scientific Computing Facility, MPI-CBG
 * Date: 2020-04
 */

/**
 * TODO
 */
@Plugin(type = Command.class, menuPath = "Plugins>Spot Colocalization > SpotColoc") // TODO nicer path
public class SpotColocalizerPlugin implements Command {

    @Parameter(label = "mask image")
    ImagePlus imp;

    @Override
    public void run() {

        System.out.println("Finished running the plugin");
    }
}
