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
package dk.dma.epd.common.prototype.layers.intendedroute;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.concurrent.ConcurrentHashMap;

import com.bbn.openmap.event.ProjectionEvent;
import com.bbn.openmap.event.ProjectionListener;
import com.bbn.openmap.omGraphics.OMCircle;
import com.bbn.openmap.omGraphics.OMGraphic;

import dk.dma.ais.message.AisMessage;
import dk.dma.epd.common.prototype.EPD;
import dk.dma.epd.common.prototype.ais.AisHandlerCommon;
import dk.dma.epd.common.prototype.ais.AisTarget;
import dk.dma.epd.common.prototype.ais.IAisTargetListener;
import dk.dma.epd.common.prototype.ais.VesselTarget;
import dk.dma.epd.common.prototype.gui.util.InfoPanel;
import dk.dma.epd.common.prototype.gui.views.CommonChartPanel;
import dk.dma.epd.common.prototype.layers.EPDLayerCommon;
import dk.dma.epd.common.prototype.model.route.IntendedRoute;
import dk.dma.epd.common.prototype.service.IIntendedRouteListener;
import dk.dma.epd.common.prototype.service.IntendedRouteHandlerCommon;

/**
 * Base layer for displaying intended routes in EPDShip and EPDShore
 */
public class IntendedRouteLayerCommon extends EPDLayerCommon 
    implements IAisTargetListener, IIntendedRouteListener, ProjectionListener {

    private static final long serialVersionUID = 1L;
    
    /**
     * Map from MMSI to intended route graphic.
     */
    protected ConcurrentHashMap<Long, IntendedRouteGraphic> intendedRoutes = new ConcurrentHashMap<>();  

    protected IntendedRouteInfoPanel intendedRouteInfoPanel = new IntendedRouteInfoPanel();
    
    private CommonChartPanel chartPanel;
    private AisHandlerCommon aisHandler;
    private IntendedRouteHandlerCommon intendedRouteHandler;
    private OMCircle dummyCircle = new OMCircle();
    
    /**
     * Constructor
     */
    public IntendedRouteLayerCommon() {
        super();
        
        // Automatically add info panels
        registerInfoPanel(intendedRouteInfoPanel, IntendedRouteWpCircle.class, IntendedRouteLegGraphic.class);
        
        // Register the classes the will trigger the map menu
        registerMapMenuClasses(IntendedRouteWpCircle.class, IntendedRouteLegGraphic.class);
        
        // Starts the repaint timer, which runs every minute
        // The initial delay is 100ms and is used to batch up repaints()
        startTimer(100, 60 * 1000);
    }
    
    /**
     * Called when the given AIS target is updated
     * @param aisTarget the AIS target that has been updated
     */
    @Override
    public void targetUpdated(AisTarget aisTarget) {
        // Sanity checks
        if (aisHandler == null || intendedRouteHandler == null || !(aisTarget instanceof VesselTarget)) {
            return;
        }
        
        // Look up the intended route
        IntendedRoute intendedRoute = intendedRouteHandler.getIntendedRoute(aisTarget.getMmsi());
        IntendedRouteGraphic intendedRouteGraphic = intendedRoutes.get(aisTarget.getMmsi());
        if (intendedRoute == null || intendedRouteGraphic == null) {
            return;
        }

        // Update the intended route name and vessel position from the VesselTarget
        VesselTarget vessel = aisHandler.getVesselTarget(intendedRoute.getMmsi());
        if (vessel != null) {
            if (vessel.getStaticData() != null) {
                intendedRouteGraphic.setName(AisMessage.trimText(vessel.getStaticData().getName()));
            }
            
            // Update the graphics
            intendedRouteGraphic.updateVesselPosition(vessel.getPositionData().getPos());
            doPrepare();
        
        } 
    }
    
    /**
     * Called when an intended route has been added
     * @param intendedRoute the intended route
     */
    @Override
    public void intendedRouteAdded(IntendedRoute intendedRoute) {   
        IntendedRouteGraphic intendedRouteGraphic = new IntendedRouteGraphic();
        
        // add the new intended route graphic to the set of managed intended route graphics
        intendedRoutes.put(intendedRoute.getMmsi(), intendedRouteGraphic);
        synchronized(graphics) {
            graphics.add(intendedRouteGraphic);
        }
        
        // Update the graphics
        intendedRouteGraphic.updateIntendedRoute(intendedRoute);
        intendedRouteGraphic.showArrowHeads(showArrowHeads());
        
        // Cause imminent repaint
        restartTimer();
    }
    
    /**
     * Called when an intended route has been updated
     * @param intendedRoute the intended route
     */
    @Override
    public void intendedRouteUpdated(IntendedRoute intendedRoute) {   
        IntendedRouteGraphic intendedRouteGraphic = intendedRoutes.get(intendedRoute.getMmsi());
        
        // Should always be defined, but better check...
        if (intendedRouteGraphic != null) {
            // Update the graphics
            intendedRouteGraphic.updateIntendedRoute(intendedRoute);
            
            // Cause imminent repaint
            restartTimer();
        }
    }
    
    /**
     * Called when an intended route has been removed
     * @param intendedRoute the intended route
     */
    @Override
    public void intendedRouteRemoved(IntendedRoute intendedRoute) {   
        IntendedRouteGraphic intendedRouteGraphic = intendedRoutes.get(intendedRoute.getMmsi());
        
        // Should always be defined, but better check...
        if (intendedRouteGraphic != null) {
            synchronized(graphics) {
                graphics.remove(intendedRouteGraphic);
            }
            intendedRoutes.remove(intendedRoute.getMmsi());
            
            // Cause imminent repaint
            restartTimer();
        }
    }
    
    /**
     * Called periodically by the timer
     */
    @Override
    protected void timerAction() {
        for (IntendedRouteGraphic intendedRouteGraphic : intendedRoutes.values()) {
            intendedRouteGraphic.updateIntendedRoute();
        }
        doPrepare();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void projectionChanged(ProjectionEvent pe) {
        // Check if we need to display the arrow heads
        if (getProjection() != null) {
            
            boolean showArrowHeads = showArrowHeads();
            for (IntendedRouteGraphic intendedRouteGraphic : intendedRoutes.values()) {
                intendedRouteGraphic.showArrowHeads(showArrowHeads);
            }
        }
        super.projectionChanged(pe);
    }

    /**
     * {@inheritDoc}
     */
    public void findAndInit(Object obj) {
        super.findAndInit(obj);
        
        if (obj instanceof AisHandlerCommon) {
            aisHandler = (AisHandlerCommon)obj;
            // register as listener for AIS messages
            aisHandler.addListener(this);
        } else if (obj instanceof IntendedRouteHandlerCommon) {
            intendedRouteHandler = (IntendedRouteHandlerCommon)obj;
            // register as listener for intended routes
            intendedRouteHandler.addListener(this);
            // Loads the existing intended routes
            loadIntendedRoutes();
        } else if (obj instanceof CommonChartPanel) {
            this.chartPanel = (CommonChartPanel)obj;
        }
    }
    
    /**
     * Upon creating a new layer, we need to load the intended routes
     * held by the intended route handler
     */
    private void loadIntendedRoutes() {
        for (IntendedRoute intendedRoute : intendedRouteHandler.fetchIntendedRoutes()) {
            intendedRouteAdded(intendedRoute);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean initInfoPanel(InfoPanel infoPanel, OMGraphic newClosest, MouseEvent evt, Point containerPoint) {
        if (newClosest instanceof IntendedRouteWpCircle) {
            intendedRouteInfoPanel.showWpInfo((IntendedRouteWpCircle) newClosest);
        } else {
            // lets user see ETA continually along route leg
            Point2D worldLocation = chartPanel.getMap().getProjection().inverse(evt.getPoint());
            intendedRouteInfoPanel.showLegInfo((IntendedRouteLegGraphic)newClosest, worldLocation);
            closest = dummyCircle;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    protected void initMapMenu(OMGraphic clickedGraphics, MouseEvent evt) {        
        if (clickedGraphics instanceof IntendedRouteWpCircle) {
            
            IntendedRouteWpCircle wpCircle = (IntendedRouteWpCircle) clickedGraphics;
            mapMenu.intendedRouteMenu(wpCircle.getIntendedRouteGraphic());
            
        } else if (clickedGraphics instanceof IntendedRouteLegGraphic) {

            IntendedRouteLegGraphic wpLeg = (IntendedRouteLegGraphic) clickedGraphics;
            mapMenu.intendedRouteMenu(wpLeg.getIntendedRouteGraphic());
        }
    }

    /**
     * Returns whether or not to show arrow heads on the route legs,
     * which depends on the current projection
     * @return whether or not to show arrow heads on the route legs
     */
    private boolean showArrowHeads() {
        if (getProjection() != null) {
            return getProjection().getScale() < EPD.getInstance()
                    .getSettings().getNavSettings().getShowArrowScale();
        }
        return false;
    }
    
}