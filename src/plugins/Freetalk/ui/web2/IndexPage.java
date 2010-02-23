/*
 * Freetalk - IndexPage.java - Copyright © 2010 David Roden
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

import java.util.Collections;

import net.pterodactylus.util.template.Template;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.ui.web2.page.Page;
import freenet.l10n.BaseL10n;

/**
 * Index page implementation that displays the list of currently subscribed
 * boards.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class IndexPage extends FreetalkTemplatePage {

	/**
	 * Creates a new index page, listing currently subscribed boards.
	 *
	 * @param template
	 *            The template to render
	 * @param l10n
	 *            The L10n handler
	 * @param webInterface
	 *            The web interface
	 */
	public IndexPage(Template template, BaseL10n l10n, WebInterface webInterface) {
		super("", template, l10n, "Page.Index.Title", webInterface);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processTemplate(Page.Request request, Template template) {
		FTOwnIdentity loggedInIdentity = getOwnIdentity(request);
		template.set("loggedInUser", loggedInIdentity);
		template.set("boards", Collections.emptyList());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getRedirectTarget(Request request) {
		if (getOwnIdentity(request) == null) {
			return "LogIn";
		}
		return super.getRedirectTarget(request);
	}

}
