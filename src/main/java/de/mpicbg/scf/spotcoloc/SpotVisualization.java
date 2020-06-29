package de.mpicbg.scf.spotcoloc;

/*
 * Author: Noreen Walker, Scientific Computing Facility, MPI-CBG
 */


import fiji.plugin.trackmate.Spot;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.measure.Calibration;

import java.awt.*;
import java.util.List;

import static java.lang.Math.round;
import static java.lang.Math.sqrt;

public class SpotVisualization {
    /** Draws the spots as 3-dimensional spheres/circles into an overlay. Works also for 2D.
     * See also createOverlayOfSpots(..) variants where rad_um, ov and color are optional.
     * Inspired by trackmate spot visualization.
     * Variables in this function are in pixel units unless appended  by um.
     * @param imp
     * @param spots list of trackmate spot objects. contains center coodinates. obtained from detectSpots(..)
     * @param rad_um spot radius in um
     * @param ov existing overlay to which spots will be added.
     * @param color
     * @return ov with added spots
     */
    public static Overlay createOverlayOfSpots(final ImagePlus imp, final List<Spot> spots, double rad_um, Overlay ov, Color color) {
        // pretty plotting
        double xoffset = 0.5; // px (overlay seems shifted when being drawn)
        double yoffset = 0.5;
        double strokewidth = 1; // 0.5 or 1

        // get image properties
        Calibration calib = imp.getCalibration();
        int nChannels = imp.getNChannels();
        boolean isHyperstack=imp.isHyperStack();

        // if no spots exist, we're already done
        if (spots.size() == 0) return ov;

        // spot radius in px
        double rad0xy = rad_um / calib.pixelWidth;
        double rad0z = rad_um / calib.pixelDepth;

        // == process all spots ==
        for (Spot spot : spots) {
            // spot center in px
            double[] pos= new double[3]; // x,y,z
            pos[0] = spot.getDoublePosition(0) / calib.pixelWidth;
            pos[1] = spot.getDoublePosition(1) / calib.pixelHeight;
            pos[2] = spot.getDoublePosition(2) / calib.pixelDepth;

            // draw circle into central slice
            int slice_ctr = (int) round(pos[2] + 1); // slice=z+1
            ov = singleCircleToOverlay(ov, pos[0], pos[1], slice_ctr, rad0xy, nChannels, isHyperstack, color, strokewidth, xoffset, yoffset);

            // draw circles into slices above and below
            for (int deltaz = 1; deltaz < rad0z + 2; deltaz++) { //step through slices (circle extends to maximally slice_ctr+-rad0x +=rounding error)
                double radxy;

                //slice shift in um
                double deltaz_um = deltaz * calib.pixelDepth;

                // compute px radius of circle in this slice
                if (deltaz_um < rad_um) {
                    radxy = (sqrt(rad_um * rad_um - deltaz_um * deltaz_um)) / calib.pixelWidth;
                } else {
                    break;
                }

                // draw circle rois
                if (slice_ctr - deltaz > 0) {
                    ov = singleCircleToOverlay(ov, pos[0], pos[1], slice_ctr - deltaz, radxy, nChannels, isHyperstack, color, strokewidth, xoffset, yoffset);
                }
                if (slice_ctr + deltaz < imp.getNSlices() + 1) {
                    ov = singleCircleToOverlay(ov, pos[0], pos[1], slice_ctr + deltaz, radxy, nChannels, isHyperstack, color, strokewidth, xoffset, yoffset);
                }
            }
        }
        return ov;
    }

    /** Like createOverlayOfSpots(ImagePlus, List, double, Overlay, Color) but with default values.
     * Creates new Overlay.
     */
    public static Overlay createOverlayOfSpots(final ImagePlus imp, final List<Spot> spots, double rad_um, Color color) {
        return createOverlayOfSpots(imp, spots, rad_um, new Overlay(), color);
    }

    /** Like createOverlayOfSpots(ImagePlus, List, ImagePlus, double, Overlay, Color) but with default values.
     * Creates new Overlay, uses radius from the spots object.
     */
    public static Overlay createOverlayOfSpots(final ImagePlus imp, final List<Spot> spots, Color color) {
        if (spots.size() == 0) return new Overlay();
        double rad_um = spots.get(0).getFeature(Spot.RADIUS);
        return createOverlayOfSpots(imp, spots, rad_um, new Overlay(), color);
    }

    /** Like createOverlayOfSpots(ImagePlus, List, double, Overlay, Color) but with default values.
     * Uses radius from the spots object.
     */
    public static Overlay createOverlayOfSpots(final ImagePlus imp, final List<Spot> spots, Overlay ov, Color color) {
        if (spots.size() == 0) return ov;
        double rad_um = spots.get(0).getFeature(Spot.RADIUS);
        return createOverlayOfSpots(imp, spots, rad_um, ov, color);
    }

    /** Like createOverlayOfSpots(ImagePlus, List, double, Overlay, Color) but with default values.
     * Creates new Overlay, uses radius from the spots object, uses color magenta.
     */
    public static Overlay createOverlayOfSpots(final ImagePlus imp, final List<Spot> spots) {
        if (spots.size() == 0) return new Overlay();
        double rad_um = spots.get(0).getFeature(Spot.RADIUS);
        return createOverlayOfSpots(imp, spots, rad_um, new Overlay(), Color.magenta);
    }

    /**
     * Adds a single circle to the overlay. Overlay is added to all channels.
     * See also function variant with default values.
     * @param ov overlay to which circle will be added
     * @param xctr in px
     * @param yctr in px
     * @param slice one-based
     * @param radius in px
     * @param isHyperstack: true for multichannel, false for single channel (and one timepoint)
     * @param color
     * @param strokewidth
     * @param xoffset shift-correction in x during plotting. xctr -> xtr+xoffset
     * @param yoffset shift-correction in y ...
     * @return overlay with added circle
     */
    private static Overlay singleCircleToOverlay(Overlay ov, double xctr, double yctr, int slice, double radius,
                                     int nChannels, boolean isHyperstack, Color color, double strokewidth, double xoffset,double yoffset) {
        for (int channel = 1; channel < nChannels+1; channel++) {
            double xleft = xctr + xoffset - radius;
            double ytop = yctr + yoffset - radius;

            OvalRoi spotroi = new OvalRoi(xleft, ytop, 2 * radius, 2 * radius);
            if (isHyperstack) {
                spotroi.setPosition(channel, slice, 1);
            } else {
                spotroi.setPosition(slice);
            }
            spotroi.setStrokeColor(color);
            spotroi.setStrokeWidth(strokewidth);
            ov.add(spotroi);
        }
        return ov;
    }

    /**
     * Like singleCircleToOverlay(Overlay, double, double, int, double, int, boolean, Color, double, double, double)
     * but with default parameters
     */
    private static Overlay singleCircleToOverlay(Overlay ov, double xctr, double yctr, int slice, double radius,
                                                 int nChannels, boolean isHyperstack, Color color) {
        return singleCircleToOverlay(ov,xctr,yctr, slice,radius,nChannels,isHyperstack, color, 0,0,0 );
    }
}
