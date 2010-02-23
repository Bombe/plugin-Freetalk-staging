/*
 * Freetalk - FreetalkTemplatePage.java - Copyright © 2010 David Roden
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

import java.util.Arrays;
import java.util.Collection;

import net.pterodactylus.util.template.Template;
import plugins.Freetalk.ui.web2.page.Page;
import plugins.Freetalk.ui.web2.page.TemplatePage;
import freenet.l10n.BaseL10n;

/**
 * Base page for the Freetalk web interface.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class FreetalkTemplatePage extends TemplatePage {

	/** The Freetalk plugin. */
	protected WebInterface webInterface;

	/**
	 * Creates a new template page for Freetalk.
	 *
	 * @param path
	 *            The path of the page
	 * @param template
	 *            The template to render
	 * @param l10n
	 *            The l10n handler
	 * @param pageTitleKey
	 *            The l10n key of the page title
	 * @param webInterface
	 *            The web interface
	 */
	public FreetalkTemplatePage(String path, Template template, BaseL10n l10n, String pageTitleKey, WebInterface webInterface) {
		super(path, template, l10n, pageTitleKey);
		this.webInterface = webInterface;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Collection<String> getStyleSheets() {
		return Arrays.asList("css/freetalk.css");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getRedirectTarget(Page.Request request) {
		if (!webInterface.getFreetalkPlugin().wotConnected() && !request.getURI().getPath().endsWith("/WebOfTrustMissing")) {
			return "WebOfTrustMissing";
		}
		return null;
	}

}
