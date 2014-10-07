/* Copyright (c) 2011 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dk.dma.epd.common.prototype.gui.menuitems;

import dk.dma.epd.common.prototype.EPD;
import dk.dma.epd.common.prototype.gui.metoc.MetocRequestDialog;
import dk.dma.epd.common.prototype.model.route.Route;

public class RouteRequestMetoc extends RouteMenuItem {

	private static final long serialVersionUID = 1L;

	public RouteRequestMetoc(String text) {
		super();
		setText(text);
	}

	@Override
	public void doAction() {
		Route route = EPD.getInstance().getRouteManager().getRoute(routeIndex);
		MetocRequestDialog.requestMetoc(EPD.getInstance().getMainFrame(), EPD
				.getInstance().getRouteManager(), route);
	}
}
