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
package dk.dma.epd.common.prototype.layers.ais;

import java.awt.Font;

import javax.swing.ImageIcon;

import com.bbn.openmap.omGraphics.OMText;
import com.bbn.openmap.proj.Projection;

import dk.dma.enav.model.geometry.Position;
import dk.dma.epd.common.prototype.ais.AisTarget;
import dk.dma.epd.common.prototype.ais.SarTarget;
import dk.dma.epd.common.prototype.ais.VesselPositionData;
import dk.dma.epd.common.prototype.settings.AisSettings;
import dk.dma.epd.common.prototype.settings.NavSettings;

/**
 * AIS SART graphic
 */
public class SarTargetGraphic extends TargetGraphic {
    private static final long serialVersionUID = 1L;

    private SarTarget sarTarget;

    private SartGraphic newSartMark;
    private SartGraphic oldSartMark;
    private Font font = new Font(Font.SANS_SERIF, Font.BOLD, 11);
    private OMText label = new OMText(0, 0, 0, 0, "", font, OMText.JUSTIFY_CENTER);
//    private boolean warningIssued;

    @Override
    public void update(AisTarget aisTarget, AisSettings aisSettings, NavSettings navSettings, float mapScale) {
        sarTarget = (SarTarget) aisTarget;
        VesselPositionData posData = sarTarget.getPositionData();
        // VesselStaticData staticData = sarTarget.getStaticData();
        Position pos = posData.getPos();

        double lat = pos.getLatitude();
        double lon = pos.getLongitude();

        if (size() == 0) {
            createGraphics();
        }
        
        if (sarTarget.isOld()) {
            oldSartMark.setVisible(true);
            newSartMark.setVisible(false);
            oldSartMark.setLat(lat);
            oldSartMark.setLon(lon);
        } else {
            newSartMark.setVisible(true);
            oldSartMark.setVisible(false);
            newSartMark.setLat(lat);
            newSartMark.setLon(lon);
        }

        label.setLat(lat);
        label.setLon(lon);
        label.setY(30);
        label.setData("AIS SART");

        //Sar msgbox disabled, change to listener type relationship?
        
//        int result = 1;
        
//        if (!warningIssued) {
//            result = JOptionPane.showOptionDialog(EeINS.getMainFrame(), "AIS SART transponder active! Zoom to location?",
//                    "AIS SART Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, null, null);
//        }
//        if (result == JOptionPane.YES_OPTION) {
//            EeINS.getMainFrame().getChartPanel().getMap().setCenter(lat, lon);
//        }
//        warningIssued = true;

    }

    private void createGraphics() {
        ImageIcon newSartIcon = new ImageIcon(SarTargetGraphic.class.getResource("/images/ais/aisSart.png"));
        newSartMark = new SartGraphic(0, 0, newSartIcon.getIconWidth(), newSartIcon.getIconHeight(), newSartIcon, this);
        add(newSartMark);
        newSartMark.setVisible(false);

        ImageIcon oldSartIcon = new ImageIcon(SarTargetGraphic.class.getResource("/images/ais/aisSartOld.png"));
        oldSartMark = new SartGraphic(0, 0, oldSartIcon.getIconWidth(), oldSartIcon.getIconHeight(), oldSartIcon, this);
        add(oldSartMark);
        oldSartMark.setVisible(false);
        
        add(label);
    }

    @Override
    public void setMarksVisible(Projection projection, AisSettings aisSettings, NavSettings navSettings) {

    }
    
    public SarTarget getSarTarget() {
        return sarTarget;
    }

}
