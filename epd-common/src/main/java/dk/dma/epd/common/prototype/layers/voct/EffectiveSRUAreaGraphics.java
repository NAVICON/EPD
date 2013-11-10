/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dma.epd.common.prototype.layers.voct;

import com.bbn.openmap.geo.Geo;
import com.bbn.openmap.geo.Intersection;
import com.bbn.openmap.omGraphics.OMGraphicList;

import dk.dma.enav.model.geometry.Position;
import dk.dma.epd.common.Heading;
import dk.dma.epd.common.prototype.model.voct.sardata.DatumPointData;
import dk.dma.epd.common.prototype.model.voct.sardata.RapidResponseData;
import dk.dma.epd.common.prototype.model.voct.sardata.SARData;
import dk.dma.epd.common.util.Calculator;
import dk.dma.epd.common.util.Converter;

public class EffectiveSRUAreaGraphics extends OMGraphicList {
    private static final long serialVersionUID = 1L;

    AreaInternalGraphics effectiveArea;
    SarEffectiveAreaLines topLine;
    SarEffectiveAreaLines bottomLine;
    SarEffectiveAreaLines leftLine;
    SarEffectiveAreaLines rightLine;

    Position A;
    Position B;
    Position C;
    Position D;
    Double totalSize;

    double verticalBearing = 180;
    double horizontalBearing = 90;

    double deltaCorrection = 0.004;

    SARData sarData;
    int id;

    public enum LineType {
        TOP, BOTTOM, LEFT, RIGHT
    }

    
    public EffectiveSRUAreaGraphics(Position A, Position B, Position C, Position D, int id, String labelName){
        super();
        
        this.A = A;
        this.B = B;
        this.C = C;
        this.D = D;
        
        effectiveArea = new AreaInternalGraphics(A, B, C, D, 0.0, 0.0,
                this, verticalBearing, horizontalBearing, labelName);

        topLine = new SarEffectiveAreaLines(A, B, LineType.TOP, this);
        bottomLine = new SarEffectiveAreaLines(C, D, LineType.BOTTOM, this);

        leftLine = new SarEffectiveAreaLines(A, C, LineType.LEFT, this);

        rightLine = new SarEffectiveAreaLines(B, D, LineType.RIGHT, this);

        add(effectiveArea);
        add(bottomLine);
        add(topLine);
        add(leftLine);
        add(rightLine);
        
    }
    

    
    public EffectiveSRUAreaGraphics(Double width, Double height, SARData data,
            int id, String labelName) {
        super();

        this.id = id;

        this.sarData = data;

        totalSize = width * height;

        // Find center point
        Position centerPosition = null;

        if (sarData instanceof RapidResponseData) {
            RapidResponseData rapidResponseData = (RapidResponseData) data;
            centerPosition = rapidResponseData.getDatum();

            verticalBearing = Calculator.bearing(rapidResponseData.getA(),
                    rapidResponseData.getD(), Heading.RL);
            horizontalBearing = Calculator.bearing(rapidResponseData.getA(),
                    rapidResponseData.getB(), Heading.RL);
        }

        if (sarData instanceof DatumPointData) {
            DatumPointData datumData = (DatumPointData) data;
            verticalBearing = Calculator.bearing(datumData.getA(),
                    datumData.getD(), Heading.RL);
            horizontalBearing = Calculator.bearing(datumData.getA(),
                    datumData.getB(), Heading.RL);
            centerPosition = datumData.getDatumDownWind();

            // Vertical and horizontal must be swapped since the direction has
            // been set to very east or very west
            if (verticalBearing < 280 && verticalBearing > 260
                    || verticalBearing < 100 && verticalBearing > 70) {

                double newVer = verticalBearing;
                System.out.println("swapping");
                verticalBearing = horizontalBearing;
                horizontalBearing = newVer;

            }

            // Reversing if direction is opposite way of assumed, assumed to be
            // headed in 90 direction ie. right
            if (horizontalBearing > 180 || horizontalBearing < 0) {

                horizontalBearing = Calculator
                        .reverseDirection(horizontalBearing);
            }

            if (verticalBearing > 270 || verticalBearing < 90) {
                verticalBearing = Calculator.reverseDirection(verticalBearing);
            }

            System.out.println("Vertical bearing is: " + verticalBearing);
            System.out.println("Horizontal bearing is: " + horizontalBearing);
        }

        // Find A position
        Position topCenter = Calculator.findPosition(centerPosition,
                Calculator.reverseDirection(verticalBearing),
                Converter.nmToMeters(height / 2));

        A = Calculator.findPosition(topCenter,
                Calculator.reverseDirection(horizontalBearing),
                Converter.nmToMeters(width / 2));

        B = Calculator.findPosition(A, horizontalBearing,
                Converter.nmToMeters(width));

        C = Calculator.findPosition(A, verticalBearing,
                Converter.nmToMeters(height));
        D = Calculator.findPosition(C, horizontalBearing,
                Converter.nmToMeters(width));

        sarData.getEffortAllocationData().get(id).setEffectiveAreaA(A);
        sarData.getEffortAllocationData().get(id).setEffectiveAreaB(B);
        sarData.getEffortAllocationData().get(id).setEffectiveAreaC(C);
        sarData.getEffortAllocationData().get(id).setEffectiveAreaD(D);

        effectiveArea = new AreaInternalGraphics(A, B, C, D, width, height,
                this, verticalBearing, horizontalBearing, labelName);

        topLine = new SarEffectiveAreaLines(A, B, LineType.TOP, this);
        bottomLine = new SarEffectiveAreaLines(C, D, LineType.BOTTOM, this);

        leftLine = new SarEffectiveAreaLines(A, C, LineType.LEFT, this);

        rightLine = new SarEffectiveAreaLines(B, D, LineType.RIGHT, this);

        add(effectiveArea);
        add(bottomLine);
        add(topLine);
        add(leftLine);
        add(rightLine);
    }

    public void updateLines(Position A, Position B, Position C, Position D) {

        this.A = A;
        this.B = B;
        this.C = C;
        this.D = D;

        topLine.updateLine(A, B);
        bottomLine.updateLine(C, D);
        leftLine.updateLine(A, C);
        rightLine.updateLine(B, D);

    }

    public void updateLength(LineType type, Position newPos) {

        double height = 0;
        double width = 0;

        System.out.println(type);

        if (type == LineType.BOTTOM) {

            newPos = findIntersectionBottom(newPos);

            if (newPos != null) {

                // Check that we haven't moved the cursor to the "opposite" side
                // of the line ie. inversing
                double deltaValue = A.getLatitude()
                        - (newPos.getLatitude() + deltaCorrection);

                // System.out.println(deltaValue);

                if (deltaValue > 0) {

                    // if (Calculator.bearing(A, newPos, Heading.RL) > 0){

                    // We update C point
                    C = newPos;

                    // New length
                    height = Calculator.range(A, C, Heading.GC);

                    // Recalculate width
                    width = totalSize / height;

                    // if (height > 1 && width > 1) {

                    // Recalculate B and D
                    B = Calculator.findPosition(A, horizontalBearing,
                            Converter.nmToMeters(width));

                    D = Calculator.findPosition(C, horizontalBearing,
                            Converter.nmToMeters(width));

                    effectiveArea.updatePosition(A, B, C, D, width, height);

                    updateLines(A, B, C, D);
                    // }
                }
            }
        }
        if (type == LineType.TOP) {

            newPos = findIntersectionTop(newPos);

            if (newPos != null) {

                double deltaValue = newPos.getLatitude()
                        - (C.getLatitude() + deltaCorrection);
                // System.out.println(deltaValue);

                // Make sure it doesn\t go over and place A under C
                // if (newPos.getLatitude() - 0.001 > C.getLatitude()) {

                // C latitude must always be below As latitude
                if (deltaValue > 0) {

                    // We update A point
                    A = newPos;

                    // New length
                    height = Calculator.range(A, C, Heading.RL);

                    // Recalculate width
                    width = totalSize / height;

                    // Recalculate B and D
                    B = Calculator.findPosition(A, horizontalBearing,
                            Converter.nmToMeters(width));

                    D = Calculator.findPosition(C, horizontalBearing,
                            Converter.nmToMeters(width));

                    effectiveArea.updatePosition(A, B, C, D, width, height);

                    updateLines(A, B, C, D);
                }
            }
        }

        if (type == LineType.LEFT) {

            newPos = findIntersectionLeft(newPos);

            if (newPos != null) {

                double deltaValue = B.getLongitude()
                        - (newPos.getLongitude() + deltaCorrection);
                // System.out.println(deltaValue);

                if (deltaValue > 0) {

                    // We update C point
                    A = newPos;

                    // New width

                    // New length
                    width = Calculator.range(A, B, Heading.RL);

                    // Recalculate width
                    height = totalSize / width;

                    // Recalculate C and D
                    C = Calculator.findPosition(A, verticalBearing,
                            Converter.nmToMeters(height));

                    D = Calculator.findPosition(C, horizontalBearing,
                            Converter.nmToMeters(width));

                    effectiveArea.updatePosition(A, B, C, D, width, height);

                    updateLines(A, B, C, D);
                }
            }
        }

        if (type == LineType.RIGHT) {

            
            newPos = findIntersectionRight(newPos);

            if (newPos != null) {

                double deltaValue = newPos.getLongitude()
                        - (A.getLongitude() + deltaCorrection);

                // System.out.println(deltaValue);

                if (deltaValue > 0) {

                    // We update B point
                    B = newPos;

                    // New width

                    // New length
                    width = Calculator.range(A, B, Heading.RL);

                    // Recalculate width
                    height = totalSize / width;

                    // Recalculate C and D
                    C = Calculator.findPosition(A, verticalBearing,
                            Converter.nmToMeters(height));

                    D = Calculator.findPosition(C, horizontalBearing,
                            Converter.nmToMeters(width));

                    effectiveArea.updatePosition(A, B, C, D, width, height);

                    updateLines(A, B, C, D);
                }
            }else{
                System.out.println("Its null");
            }
        }
    }

    private Position findIntersectionRight(Position newPos) {
        // The mouse can be in 4 different areas
        int lengthMax = 500000;

        double bearing = A.rhumbLineBearingTo(newPos);

        // System.out.println("Vertical bearing " + verticalBearing);
        // System.out.println("Horizontal bearing " + horizontalBearing);

        Geo intersectionPoint = null;

        if (bearing > 180 && bearing <= 270) {

            // Create a line going from newPos, in vertical direction eg up
            Position verticalEndPosition = Calculator.findPosition(newPos,
                    Calculator.reverseDirection(verticalBearing), lengthMax);
            Geo a1 = new Geo(newPos.getLatitude(), newPos.getLongitude());
            Geo a2 = new Geo(verticalEndPosition.getLatitude(),
                    verticalEndPosition.getLongitude());

            // Create a line going from A in reverse horizontal direction
            // eg left
            Position horizontalEndPosition = Calculator.findPosition(A,
                    horizontalBearing, lengthMax);

            Geo b1 = new Geo(A.getLatitude(), A.getLongitude());
            Geo b2 = new Geo(horizontalEndPosition.getLatitude(),
                    horizontalEndPosition.getLongitude());

            intersectionPoint = Intersection.segmentsIntersect(a1, a2, b1, b2);

        }

        if (bearing > 270 || bearing < 180) {

            // if (bearing > 90 && bearing < 180){

            // Create a line going from newPos, in vertical direction eg down
            Position verticalEndPosition = Calculator.findPosition(newPos,
                    Calculator.reverseDirection(verticalBearing), lengthMax);
            Geo a1 = new Geo(newPos.getLatitude(), newPos.getLongitude());
            Geo a2 = new Geo(verticalEndPosition.getLatitude(),
                    verticalEndPosition.getLongitude());

            // Create a line going from D in reverse horizontal direction
            // eg left
            Position horizontalEndPosition = Calculator.findPosition(A,
                    horizontalBearing, lengthMax);

            Geo b1 = new Geo(A.getLatitude(), A.getLongitude());
            Geo b2 = new Geo(horizontalEndPosition.getLatitude(),
                    horizontalEndPosition.getLongitude());

            intersectionPoint = Intersection.segmentsIntersect(a1, a2, b1, b2);

        }

        if (intersectionPoint != null) {

            newPos = Position.create(intersectionPoint.getLatitude(),
                    intersectionPoint.getLongitude());

            return newPos;

        } else {
            System.out.println("something went bad... for mouse bearing "
                    + bearing);

            return null;
        }
    }

    private Position findIntersectionLeft(Position newPos) {
        // The mouse can be in 4 different areas
        int lengthMax = 500000;

        double bearing = B.rhumbLineBearingTo(newPos);

        // System.out.println("Vertical bearing " + verticalBearing);
        // System.out.println("Horizontal bearing " + horizontalBearing);

        Geo intersectionPoint = null;

        if (bearing > 180) {

            // Create a line going from newPos, in vertical direction eg up
            Position verticalEndPosition = Calculator.findPosition(newPos,
                    Calculator.reverseDirection(verticalBearing), lengthMax);
            Geo a1 = new Geo(newPos.getLatitude(), newPos.getLongitude());
            Geo a2 = new Geo(verticalEndPosition.getLatitude(),
                    verticalEndPosition.getLongitude());

            // Create a line going from B in reverse horizontal direction
            // eg left
            Position horizontalEndPosition = Calculator.findPosition(B,
                    Calculator.reverseDirection(horizontalBearing), lengthMax);

            Geo b1 = new Geo(B.getLatitude(), B.getLongitude());
            Geo b2 = new Geo(horizontalEndPosition.getLatitude(),
                    horizontalEndPosition.getLongitude());

            intersectionPoint = Intersection.segmentsIntersect(a1, a2, b1, b2);

            System.out.println(a1);
            System.out.println(a2);
            System.out.println(b1);
            System.out.println(b2);
            System.out.println(intersectionPoint);

        }

        if (bearing > 0 && bearing <= 180) {

            // if (bearing > 90 && bearing < 180){

            // Create a line going from newPos, in vertical direction eg down
            Position verticalEndPosition = Calculator.findPosition(newPos,
                    verticalBearing, lengthMax);
            Geo a1 = new Geo(newPos.getLatitude(), newPos.getLongitude());
            Geo a2 = new Geo(verticalEndPosition.getLatitude(),
                    verticalEndPosition.getLongitude());

            // Create a line going from D in reverse horizontal direction
            // eg left
            Position horizontalEndPosition = Calculator.findPosition(B,
                    Calculator.reverseDirection(horizontalBearing), lengthMax);

            Geo b1 = new Geo(B.getLatitude(), B.getLongitude());
            Geo b2 = new Geo(horizontalEndPosition.getLatitude(),
                    horizontalEndPosition.getLongitude());

            intersectionPoint = Intersection.segmentsIntersect(a1, a2, b1, b2);

        }

        if (intersectionPoint != null) {

            newPos = Position.create(intersectionPoint.getLatitude(),
                    intersectionPoint.getLongitude());

            return newPos;

        } else {
            System.out.println("something went bad... for mouse bearing "
                    + bearing);

            return null;
        }
    }

    private Position findIntersectionTop(Position newPos) {
        // The mouse can be in 4 different areas
        int lengthMax = 500000;

        double bearing = A.rhumbLineBearingTo(newPos);

        System.out.println("Vertical bearing " + verticalBearing);
        System.out.println("Horizontal bearing " + horizontalBearing);

        Geo intersectionPoint = null;

        if (bearing >= 0 && bearing <= 180) {

            // if (bearing > 90 && bearing < 180){

            // Create a line going from C, in reverse vertical direction eg up
            Position verticalEndPosition = Calculator.findPosition(C,
                    Calculator.reverseDirection(verticalBearing), lengthMax);
            Geo a1 = new Geo(C.getLatitude(), C.getLongitude());
            Geo a2 = new Geo(verticalEndPosition.getLatitude(),
                    verticalEndPosition.getLongitude());

            // Create a line going from newPos in reverse horizontal direction
            // eg left
            Position horizontalEndPosition = Calculator.findPosition(newPos,
                    Calculator.reverseDirection(horizontalBearing), lengthMax);

            Geo b1 = new Geo(newPos.getLatitude(), newPos.getLongitude());
            Geo b2 = new Geo(horizontalEndPosition.getLatitude(),
                    horizontalEndPosition.getLongitude());

            intersectionPoint = Intersection.segmentsIntersect(a1, a2, b1, b2);
        }

        if (bearing > 180) {

            // Create a line going from C, in reverse vertical direction eg up
            Position verticalEndPosition = Calculator.findPosition(C,
                    Calculator.reverseDirection(verticalBearing), lengthMax);
            Geo a1 = new Geo(C.getLatitude(), C.getLongitude());
            Geo a2 = new Geo(verticalEndPosition.getLatitude(),
                    verticalEndPosition.getLongitude());

            // Create a line going from newPos in horizontal direction eg right
            Position horizontalEndPosition = Calculator.findPosition(newPos,
                    horizontalBearing, lengthMax);

            Geo b1 = new Geo(newPos.getLatitude(), newPos.getLongitude());
            Geo b2 = new Geo(horizontalEndPosition.getLatitude(),
                    horizontalEndPosition.getLongitude());

            intersectionPoint = Intersection.segmentsIntersect(a1, a2, b1, b2);
        }

        if (intersectionPoint != null) {

            newPos = Position.create(intersectionPoint.getLatitude(),
                    intersectionPoint.getLongitude());

            return newPos;

        } else {
            System.out.println("something went bad... for mouse bearing "
                    + bearing);

            return null;
        }
    }

    private Position findIntersectionBottom(Position newPos) {
        // The mouse can be in 4 different areas
        int lengthMax = 500000;

        double bearing = C.rhumbLineBearingTo(newPos);

        System.out.println("Vertical bearing " + verticalBearing);
        System.out.println("Horizontal bearing " + horizontalBearing);

        Geo intersectionPoint = null;

        if (bearing >= 0 && bearing <= 180) {

            // if (bearing > 90 && bearing < 180){

            // Create a line going from C, in reverse vertical direction eg up
            Position verticalEndPosition = Calculator.findPosition(A,
                    verticalBearing, lengthMax);
            Geo a1 = new Geo(A.getLatitude(), A.getLongitude());
            Geo a2 = new Geo(verticalEndPosition.getLatitude(),
                    verticalEndPosition.getLongitude());

            // Create a line going from newPos in reverse horizontal direction
            // eg left
            Position horizontalEndPosition = Calculator.findPosition(newPos,
                    Calculator.reverseDirection(horizontalBearing), lengthMax);

            Geo b1 = new Geo(newPos.getLatitude(), newPos.getLongitude());
            Geo b2 = new Geo(horizontalEndPosition.getLatitude(),
                    horizontalEndPosition.getLongitude());

            intersectionPoint = Intersection.segmentsIntersect(a1, a2, b1, b2);
        }

        if (bearing > 180) {

            // Create a line going from C, in reverse vertical direction eg up
            Position verticalEndPosition = Calculator.findPosition(C,
                    verticalBearing, lengthMax);
            Geo a1 = new Geo(C.getLatitude(), C.getLongitude());
            Geo a2 = new Geo(verticalEndPosition.getLatitude(),
                    verticalEndPosition.getLongitude());

            // Create a line going from newPos in horizontal direction eg right
            Position horizontalEndPosition = Calculator.findPosition(newPos,
                    horizontalBearing, lengthMax);

            Geo b1 = new Geo(newPos.getLatitude(), newPos.getLongitude());
            Geo b2 = new Geo(horizontalEndPosition.getLatitude(),
                    horizontalEndPosition.getLongitude());

            intersectionPoint = Intersection.segmentsIntersect(a1, a2, b1, b2);
        }

        if (intersectionPoint != null) {

            newPos = Position.create(intersectionPoint.getLatitude(),
                    intersectionPoint.getLongitude());

            return newPos;

        } else {
            System.out.println("something went bad... for mouse bearing "
                    + bearing);

            return null;
        }
    }

    public void updateEffectiveAreaSize(SARData sarData) {

        // System.out.println("Is the sar data null? ");
        // System.out.println(sarData == null);
        // System.out.println("Is the effective area null? ");
        // System.out.println(sarData.getFirstEffortAllocationData()==null);
        //
        if (sarData.getEffortAllocationData().size() > id){
            sarData.getEffortAllocationData().get(id).setEffectiveAreaA(A);
            sarData.getEffortAllocationData().get(id).setEffectiveAreaB(B);
            sarData.getEffortAllocationData().get(id).setEffectiveAreaC(C);
            sarData.getEffortAllocationData().get(id).setEffectiveAreaD(D);    
        }
        
    }

}