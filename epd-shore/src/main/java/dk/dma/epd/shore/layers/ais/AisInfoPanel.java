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
package dk.dma.epd.shore.layers.ais;

import dk.dma.epd.shore.gui.utils.InfoPanel;

/**
 * AIS mouse over info
 */
public class AisInfoPanel extends InfoPanel {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor
     */
    public AisInfoPanel() {
        super();
    }

    /**
     * Display a AIS info
     * @param shoreTargetGraphic
     */
    public void showAisInfo(ShoreTargetGraphic shoreTargetGraphic) {
        String aisText = "<HTML>";
        if(!shoreTargetGraphic.getName().equals("N/A")) {
            aisText += shoreTargetGraphic.getName() + " ("+shoreTargetGraphic.getMMSI() + ")";
        } else {
            aisText += shoreTargetGraphic.getMMSI();
        }
        aisText += "<BR/>COG "+shoreTargetGraphic.getHeading()+"° SOG "+shoreTargetGraphic.getSog()+" kn";
        aisText += "</HTML>";
        showText(aisText);
    }
}
