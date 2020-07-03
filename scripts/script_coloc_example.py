# Examples of running some substeps of the spot-colocalizer via lower level access
# see also https://github.com/walkernoreen/spot_colocalizer/blob/master/src/main/java/de/mpicbg/scf/spotcoloc/SpotProcessor.java
# steps in runFullColocalizationAnalysis(...)

from ij import IJ
from de.mpicbg.scf.spotcoloc import SpotProcessor

# specify detector and colocalizer parameters
channelA=1
radiusA_um=0.9
thresholdA=5000
channelB=2
radiusB_um=0.9
thresholdB=4000
distanceFactorColoc=1
doSubpixel=True
doMedian=True

# process current image (>=2 channels)
imp=IJ.getImage()

imp.setOverlay(None)

# initialize a spot processor instance
spotProcessor = SpotProcessor(imp)

# detect spots (lists of trackmate spot objects)
spotsA = spotProcessor.detectSpots(channelA, radiusA_um,thresholdA, doSubpixel, doMedian)
spotsB = spotProcessor.detectSpots(channelB, radiusB_um, thresholdB, doSubpixel, doMedian)

# find colocalized spots
maxdist_um = 0.5 * (radiusA_um + radiusB_um) * distanceFactorColoc
CR = spotProcessor.findSpotCorrespondences(spotsA, spotsB, maxdist_um) 
# colocalization result CR: fields are lists of spots, split by channel and colocalization: CR.spotsA_coloc, CR.spotsA_noncoloc, etc.

# print a summary
print "\nDetected spots channel A: colocalized: ",len(CR.spotsA_coloc),", not colocalized: ",len(CR.spotsA_noncoloc)
print "Detected spots channel B: colocalized: ",len(CR.spotsB_coloc),", not colocalized: ",len(CR.spotsB_noncoloc)

# print the coordinates of all spots in channel A which were colocalized
print "\nCoordinates of colocalized spots channel A (in um):"
for spot in CR.spotsA_coloc:
	print "x=",spot.getDoublePosition(0),", y=",spot.getDoublePosition(1),", z=", spot.getDoublePosition(2)


