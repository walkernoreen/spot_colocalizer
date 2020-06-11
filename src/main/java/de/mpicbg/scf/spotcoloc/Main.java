/*
some imglib2 resources and forum posts:
extract channels: https://forum.image.sc/t/how-can-i-work-with-channels-zstacks-in-the-imagej2-jupyter-notebooks/23447/3
convert imp to imglib2 type: https://forum.image.sc/t/how-to-wrap-any-kind-of-imageplus-to-an-imglib2-img-floattype/178/2
 */

package de.mpicbg.scf.spotcoloc;

import fiji.plugin.trackmate.Spot;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.RealType;

import java.awt.*;
import java.io.File;
import java.util.List;

public class Main {
   public static  <T extends RealType<T>> void main(final String... args) throws Exception { //TODO extend T?
   //public static  void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services

        // plugins dir for development: make IJ.run() work
        //String pluginsDir = "/Applications/Fiji/plugins";
        //System.setProperty("plugins.dir", pluginsDir);


        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // load and show image

//        File inputFile=new File("src/main/resources/spots_singlechannel_stack.tif");
        File inputFile=new File("src/main/resources/spots_multichannel_stack.tif");

      ImagePlus imp= IJ.openImage(inputFile.getPath());

       int[] xpoints = {65,185,175};
       int[] ypoints = {124,22,202};
       imp.setRoi(new PolygonRoi(xpoints,ypoints,3,Roi.POLYGON));
       int nSlices = imp.getNSlices();
       imp.setPosition(1, (int) Math.max(1,0.5*nSlices),1);
       imp.show();

       // invoke the plugin (IJ2 style)
        ij.command().run(SpotColocalizerPlugin.class, true);

        // automatize input for all @Parameters
      /*  Map<String, Object> map = new HashMap<>();
          map.put("segImp", labels);
          map.put("grayImp", imp);

        ij.command().run(Create3DOverlayPlugin.class, true, map);*/

       System.out.println("Done");
    }


}
