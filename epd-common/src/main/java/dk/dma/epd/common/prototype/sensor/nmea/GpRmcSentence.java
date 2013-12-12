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
package dk.dma.epd.common.prototype.sensor.nmea;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import dk.dma.ais.sentence.Sentence;
import dk.dma.ais.sentence.SentenceException;
import dk.dma.ais.sentence.SentenceLine;
import dk.dma.enav.model.geometry.Position;
import dk.dma.epd.common.FormatException;
import dk.dma.epd.common.util.ParseUtils;

/**
 * GPRMC sentence representation
 */
public class GpRmcSentence extends Sentence {

    private PntMessage pntData;
    private String status;

    public GpRmcSentence() {
        super();
    }

    public int parse(String line) throws SentenceException {
        return parse(new SentenceLine(line.trim()));
    }

    @Override
    public int parse(SentenceLine sl) throws SentenceException {
        // Do common parsing
        super.baseParse(sl);

        // Check RMC
        if (!sl.getFormatter().equals("RMC")) {
            throw new SentenceException("Not RMC sentence");
        }

        // Check that there is a least 10 fields
        if (sl.getFields().size() < 10) {
            throw new SentenceException("RMC sentence " + sl.getLine() + "     does not have at least 10 fields ");
        }

        // Get lat and lon
        Position pos = null;
        Double sog = null;
        Double cog = null;
        try {
            if (sl.getFields().get(3).length() > 2 && sl.getFields().get(5).length() > 3) {
                double lat = ParseUtils.parseLatitude(sl.getFields().get(3).substring(0, 2),
                        sl.getFields().get(3).substring(2, sl.getFields().get(3).length() - 1), sl.getFields().get(4));
                double lon = ParseUtils.parseLongitude(sl.getFields().get(5).substring(0, 3),
                        sl.getFields().get(5).substring(3, sl.getFields().get(5).length() - 1), sl.getFields().get(6));
                pos = Position.create(lat, lon);
            }
            if (sl.getFields().get(7).length() > 0) {
                sog = ParseUtils.parseDouble(sl.getFields().get(7));
            }
            if (sl.getFields().get(8).length() > 0) {
                cog = ParseUtils.parseDouble(sl.getFields().get(8));
            }
        } catch (FormatException e1) {
            throw new SentenceException("GPS sentence not valid: " + sl.getLine());
        }

        // Parse time
        Long time = null;
        String utc = sl.getFields().get(1);
        if (utc.length() <= 6) {
            utc += ".00";
        }
        String dateTimeStr = utc + " " + sl.getFields().get(9);
        SimpleDateFormat dateFormat = new SimpleDateFormat("HHmmss.SS ddMMyy");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+0000"));
        try {
            time = dateFormat.parse(dateTimeStr).getTime();
        } catch (ParseException e) {
            throw new SentenceException("GPS time " + dateTimeStr + " not valid ");
        }

        pntData = new PntMessage(pos, sog, cog, time);

        // Get status
        status = sl.getFields().get(2);

        return 0;
    }

    @Override
    public String getEncoded() {
        return null;
    }

    public PntMessage getPntMessage() {
        return pntData;
    }

    public String getStatus() {
        return status;
    }

}
