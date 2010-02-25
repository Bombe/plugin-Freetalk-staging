/*
 * Freetalk - WebOfTrustMissingPage.java - Copyright © 2010 David Roden
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package plugins.Freetalk.ui.web2;

import net.pterodactylus.util.template.Template;
import plugins.Freetalk.ui.web2.page.Page;

/**
 * Web page that tells the user the the web of trust plugin is either missing or
 * outdated.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class WebOfTrustMissingPage extends FreetalkTemplatePage {

	/**
	 * Creates a new “web of trust is missing” page.
	 *
	 * @param template
	 *            The template to render
	 * @param webInterface
	 *            The web interface
	 */
	public WebOfTrustMissingPage(Template template, WebInterface webInterface) {
		super("WebOfTrustMissing", template, "Page.WebOfTrustMissing.Title", webInterface);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processTemplate(Page.Request request, Template template) {
		template.set("webOfTrustOutdated", webInterface.getFreetalkPlugin().wotOutdated());
	}

}
