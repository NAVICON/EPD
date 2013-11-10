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
package dk.dma.epd.ship.service.voct;

import javax.swing.JDialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.dma.enav.model.geometry.Position;
import dk.dma.enav.model.voct.RapidResponseDTO;
import dk.dma.epd.common.prototype.enavcloud.VOCTCommunicationService.VOCTCommunicationMessage;
import dk.dma.epd.common.prototype.model.route.Route;
import dk.dma.epd.common.prototype.model.route.RoutesUpdateEvent;
import dk.dma.epd.common.prototype.model.voct.SAR_TYPE;
import dk.dma.epd.common.prototype.model.voct.SearchPatternGenerator;
import dk.dma.epd.common.prototype.model.voct.sardata.EffortAllocationData;
import dk.dma.epd.common.prototype.model.voct.sardata.RapidResponseData;
import dk.dma.epd.common.prototype.model.voct.sardata.SearchPatternRoute;
import dk.dma.epd.common.prototype.voct.VOCTManagerCommon;
import dk.dma.epd.common.prototype.voct.VOCTUpdateEvent;
import dk.dma.epd.common.util.Util;
import dk.dma.epd.ship.EPDShip;
import dk.dma.epd.ship.gui.voct.SARInput;
import dk.dma.epd.ship.layers.voct.VoctLayer;

/**
 * The VOCTManager is responsible for maintaining current VOCT Status and all
 * information relevant to the VOCT
 * 
 * The VOCT Manager can be initiated through the cloud or manually by the user
 * 
 * 
 */

public class VOCTManager extends VOCTManagerCommon {

    private static final long serialVersionUID = 1L;
    private SARInput sarInputDialog;

    VoctLayer voctLayer;

    private static final Logger LOG = LoggerFactory
            .getLogger(VOCTManagerCommon.class);

    public VOCTManager() {
        EPDShip.startThread(this, "VOCTManager");
        LOG.info("Started VOCT Manager");
    }

    @Override
    public void showSarInput() {
        LOG.info("Started new SAR Operation");
        if (!hasSar) {
            hasSar = true;

            // Create the GUI input boxes

            // Voct specific test
            sarInputDialog = new SARInput(this);
            sarInputDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            sarInputDialog.setVisible(true);

        } else {
            // Cannot inititate a SAR without terminating the existing one, show
            // existing dialog?
            sarInputDialog.setVisible(true);
        }

    }

    /**
     * @param voctLayer
     *            the voctLayer to set
     */
    public void setVoctLayer(VoctLayer voctLayer) {
        this.voctLayer = voctLayer;
    }

    @Override
    public void run() {

        // Maintanaince routines
        while (true) {
            Util.sleep(10000);

        }

    }

    public static VOCTManager loadVOCTManager() {

        // Where we load or serialize old VOCTS
        return new VOCTManager();

    }

    @Override
    public void generateSearchPattern(
            SearchPatternGenerator.searchPattern type, Position CSP, int id) {

        sarData.setCSP(CSP);

        SearchPatternGenerator searchPatternGenerator = new SearchPatternGenerator(
                sarOperation);

        SearchPatternRoute searchRoute = searchPatternGenerator
                .generateSearchPattern(type, sarData, EPDShip.getSettings()
                        .getNavSettings(), id);

        // Remove old and overwrite
        if (sarData.getEffortAllocationData().get(id).getSearchPatternRoute() != null) {
            int routeIndex = EPDShip.getRouteManager().getRouteIndex(
                    sarData.getEffortAllocationData().get(id)
                            .getSearchPatternRoute());

            EPDShip.getRouteManager().removeRoute(routeIndex);
        }

        sarData.getEffortAllocationData().get(id)
                .setSearchPatternRoute(searchRoute);

        EPDShip.getRouteManager().addRoute(searchRoute);

        notifyListeners(VOCTUpdateEvent.SEARCH_PATTERN_GENERATED);
    }

    @Override
    public void updateEffectiveAreaLocation() {
        voctLayer.updateEffectiveAreaLocation(sarData);
    }

    public void handleSARDataPackage(VOCTCommunicationMessage message) {

        // Remove any old SAR data
        if (sarData != null) {
            if (sarData.getEffortAllocationData().size() > 0) {
                if (sarData.getEffortAllocationData().get(0)
                        .getSearchPatternRoute() != null) {
                    System.out.println("Removing existing routes");
                    
                    int routeIndex = EPDShip.getRouteManager().getRouteIndex(
                            sarData.getEffortAllocationData().get(0)
                                    .getSearchPatternRoute());

                    EPDShip.getRouteManager().removeRoute(routeIndex);
                    
                    
                    EPDShip.getRouteManager().notifyListeners(
                            RoutesUpdateEvent.ROUTE_REMOVED);
                    
                }
            }
        }

        RapidResponseData data = new RapidResponseData(message.getSarData());

        if (message.getEffortAllocationData() != null) {

            // message.getEffortAllocationData()
            EffortAllocationData effortAllocationData = new EffortAllocationData(
                    message.getEffortAllocationData());

            if (message.getSearchPattern() != null) {
                SearchPatternRoute searchPattern = new SearchPatternRoute(
                        new Route(message.getSearchPattern()));
                effortAllocationData.setSearchPatternRoute(searchPattern);
                EPDShip.getRouteManager().addRoute(searchPattern);
                EPDShip.getRouteManager().notifyListeners(
                        RoutesUpdateEvent.ROUTE_ADDED);

            }

            data.addEffortAllocationData(effortAllocationData, 0);

        }

        this.setSarData(data);
        setSarType(SAR_TYPE.RAPID_RESPONSE);

        hasSar = true;

        notifyListeners(VOCTUpdateEvent.SAR_RECEIVED_CLOUD);

    }

}