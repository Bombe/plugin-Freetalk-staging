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
import plugins.Freetalk.Freetalk;
import freenet.l10n.BaseL10n;

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
	 * @param l10n
	 *            The l10n handler
	 * @param freetalkPlugin
	 *            The Freetalk plugin
	 */
	public WebOfTrustMissingPage(Template template, BaseL10n l10n, Freetalk freetalkPlugin) {
		super("WebOfTrustMissing", template, l10n, "Page.WebOfTrustMissing.Title", freetalkPlugin);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processTemplate(Template template) {
		template.set("webOfTrustOutdated", freetalkPlugin.wotOutdated());
	}

}
